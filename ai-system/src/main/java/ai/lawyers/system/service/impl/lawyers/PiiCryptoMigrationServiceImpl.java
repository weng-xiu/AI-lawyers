package ai.lawyers.system.service.impl.lawyers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.common.utils.sign.PiiCryptoUtils;
import ai.lawyers.system.domain.lawyers.AiCallLedger;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallerProfile;
import ai.lawyers.system.mapper.lawyers.AiCallLedgerMapper;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.AiCallerProfileMapper;
import ai.lawyers.system.mapper.lawyers.AiCallRecordArchiveMapper;
import ai.lawyers.system.service.lawyers.IPiiCryptoMigrationService;
import ai.lawyers.system.service.lawyers.IPiiSearchTokenService;
import ai.lawyers.system.service.lawyers.storage.RecordingStorageService;

/**
 * P3-G1-b（V2.55）/ P3-G1-b2（V2.56）：存量 PII 明文 → SM4-GCM 密文 +
 * 盲索引回填 + 位置分片模糊检索 token 重建。
 *
 * <p>幂等：每行独立处理，密文/索引/token 齐全的行实际不产生列变更；
 * 档案同号唯一键冲突单独计数跳过，单行异常计数不中断整体迁移。
 * 运行时结果集经 PiiDecryptInterceptor 读出为明文，本类按"明文即迁移"处理，
 * 同时保留 isEncrypted 显式判断以兼容拦截器未生效的调用环境。</p>
 *
 * @author ai-lawyers
 */
@Service
public class PiiCryptoMigrationServiceImpl implements IPiiCryptoMigrationService
{
    private static final Logger log = LoggerFactory.getLogger(PiiCryptoMigrationServiceImpl.class);

    @Autowired
    private AiCallerProfileMapper callerProfileMapper;

    @Autowired
    private AiCallRecordMapper callRecordMapper;

    @Autowired
    private AiCallRecordArchiveMapper archiveMapper;

    @Autowired
    private AiCallLedgerMapper callLedgerMapper;

    @Autowired(required = false)
    private IPiiSearchTokenService piiSearchTokenService;

    /** 录音存储（本地/对象存储），用于密钥轮换时重加密录音文件 */
    @Autowired(required = false)
    private RecordingStorageService recordingStorageService;

    @Override
    public Map<String, Object> migrateCallerProfiles()
    {
        int migrated = 0;
        int conflictSkipped = 0;
        int tokenRebuilt = 0;
        List<Map<String, Object>> rows = callerProfileMapper.selectRawForMigrate();
        for (Map<String, Object> row : rows)
        {
            Long profileId = asLong(row.get("profileId"));
            String rawNumber = asString(row.get("callerNumber"));
            String rawNumberIndex = asString(row.get("callerNumberIndex"));
            String rawIdCard = asString(row.get("callerIdCard"));
            String rawIdCardIndex = asString(row.get("callerIdCardIndex"));

            String numberPlain = decryptOrNull(rawNumber);
            String idCardPlain = decryptOrNull(rawIdCard);

            AiCallerProfile update = new AiCallerProfile();
            update.setProfileId(profileId);
            if (numberPlain != null)
            {
                if (!PiiCryptoUtils.isEncrypted(rawNumber))
                {
                    update.setCallerNumber(PiiCryptoUtils.encrypt(numberPlain));
                }
                if (StringUtils.isEmpty(rawNumberIndex))
                {
                    update.setCallerNumberIndex(PiiCryptoUtils.blindIndex(numberPlain));
                }
            }
            if (idCardPlain != null)
            {
                if (!PiiCryptoUtils.isEncrypted(rawIdCard))
                {
                    update.setCallerIdCard(PiiCryptoUtils.encrypt(idCardPlain));
                }
                if (StringUtils.isEmpty(rawIdCardIndex))
                {
                    update.setCallerIdCardIndex(PiiCryptoUtils.blindIndex(idCardPlain));
                }
            }
            boolean hasColumnChange = update.getCallerNumber() != null
                    || update.getCallerNumberIndex() != null
                    || update.getCallerIdCard() != null
                    || update.getCallerIdCardIndex() != null;
            if (hasColumnChange)
            {
                try
                {
                    callerProfileMapper.updateAiCallerProfile(update);
                    migrated++;
                }
                catch (DuplicateKeyException e)
                {
                    conflictSkipped++;
                }
            }
            // G1-b2：号码 token 仅在缺失或号码本轮重加密时重建（幂等零写库）
            boolean numberChanged = update.getCallerNumber() != null;
            if (ensureToken(IPiiSearchTokenService.OWNER_CALLER_PROFILE,
                    profileId, numberPlain, numberChanged))
            {
                tokenRebuilt++;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", rows.size());
        result.put("migrated", migrated);
        result.put("conflictSkipped", conflictSkipped);
        result.put("tokenRebuilt", tokenRebuilt);
        return result;
    }

    @Override
    public Map<String, Object> migrateCallRecords()
    {
        int migrated = 0;
        int tokenRebuilt = 0;
        int failed = 0;

        // 热表
        List<Map<String, Object>> hotRows = callRecordMapper.selectRawForMigrate();
        for (Map<String, Object> row : hotRows)
        {
            try
            {
                Long recordId = asLong(row.get("recordId"));
                String raw = asString(row.get("callerNumber"));
                String existingIndex = asString(row.get("callerNumberIndex"));
                String plain = decryptOrNull(raw);
                boolean needCipher = plain != null && !PiiCryptoUtils.isEncrypted(raw);
                boolean needIndex = plain != null && StringUtils.isEmpty(existingIndex);
                if (needCipher || needIndex)
                {
                    AiCallRecord update = new AiCallRecord();
                    update.setRecordId(recordId);
                    if (needCipher)
                    {
                        update.setCallerNumber(PiiCryptoUtils.encrypt(plain));
                    }
                    update.setCallerNumberIndex(PiiCryptoUtils.blindIndex(plain));
                    callRecordMapper.updateAiCallRecord(update);
                    migrated++;
                }
                if (ensureToken(IPiiSearchTokenService.OWNER_CALL_RECORD,
                        recordId, plain, needCipher))
                {
                    tokenRebuilt++;
                }
            }
            catch (Exception e)
            {
                failed++;
                log.warn("话单 PII 迁移单行失败 recordId={}: {}", asLong(row.get("recordId")), e.getMessage());
            }
        }

        // 归档表（token 属主仍为 CALL_RECORD：record_id 稳定，归档不另建属主）
        int archiveTotal = 0;
        List<Map<String, Object>> archiveRows = archiveMapper.selectRawForMigrate();
        for (Map<String, Object> row : archiveRows)
        {
            archiveTotal++;
            try
            {
                Long recordId = asLong(row.get("recordId"));
                String raw = asString(row.get("callerNumber"));
                String existingIndex = asString(row.get("callerNumberIndex"));
                String plain = decryptOrNull(raw);
                boolean needCipher = plain != null && !PiiCryptoUtils.isEncrypted(raw);
                boolean needIndex = plain != null && StringUtils.isEmpty(existingIndex);
                if (needCipher || needIndex)
                {
                    archiveMapper.updateArchiveEncryption(recordId,
                            needCipher ? PiiCryptoUtils.encrypt(plain) : null,
                            needIndex ? PiiCryptoUtils.blindIndex(plain) : null);
                    migrated++;
                }
                if (ensureToken(IPiiSearchTokenService.OWNER_CALL_RECORD,
                        recordId, plain, needCipher))
                {
                    tokenRebuilt++;
                }
            }
            catch (Exception e)
            {
                failed++;
                log.warn("归档话单 PII 迁移单行失败 recordId={}: {}", asLong(row.get("recordId")), e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("hotTotal", hotRows.size());
        result.put("archiveTotal", archiveTotal);
        result.put("migrated", migrated);
        result.put("tokenRebuilt", tokenRebuilt);
        result.put("failed", failed);
        return result;
    }

    @Override
    public Map<String, Object> migrateCallLedgers()
    {
        int migrated = 0;
        int tokenRebuilt = 0;
        int failed = 0;
        List<Map<String, Object>> rows = callLedgerMapper.selectRawForMigrate();
        for (Map<String, Object> row : rows)
        {
            try
            {
                Long ledgerId = asLong(row.get("ledgerId"));
                String rawPhone = asString(row.get("callerPhone"));
                String rawIdCard = asString(row.get("callerIdCard"));
                String plainPhone = decryptOrNull(rawPhone);
                String plainIdCard = decryptOrNull(rawIdCard);
                boolean needPhoneCipher = plainPhone != null
                        && !PiiCryptoUtils.isEncrypted(rawPhone);
                boolean needIdCardCipher = plainIdCard != null
                        && !PiiCryptoUtils.isEncrypted(rawIdCard);
                if (needPhoneCipher || needIdCardCipher)
                {
                    AiCallLedger update = new AiCallLedger();
                    update.setLedgerId(ledgerId);
                    if (needPhoneCipher)
                    {
                        update.setCallerPhone(PiiCryptoUtils.encrypt(plainPhone));
                    }
                    if (needIdCardCipher)
                    {
                        update.setCallerIdCard(PiiCryptoUtils.encrypt(plainIdCard));
                    }
                    callLedgerMapper.updateAiCallLedger(update);
                    migrated++;
                }
                if (ensureToken(IPiiSearchTokenService.OWNER_CALL_LEDGER,
                        ledgerId, plainPhone, needPhoneCipher))
                {
                    tokenRebuilt++;
                }
            }
            catch (Exception e)
            {
                failed++;
                log.warn("台账 PII 迁移单行失败 ledgerId={}: {}", asLong(row.get("ledgerId")), e.getMessage());
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", rows.size());
        result.put("migrated", migrated);
        result.put("tokenRebuilt", tokenRebuilt);
        result.put("failed", failed);
        return result;
    }

    /**
     * 幂等地保证属主号码 token 存在：仅当号码本轮重加密（changed=true）或属主 token
     * 缺失时才触发重建；已存在且号码未变则零写库跳过。
     * @return 是否实际执行了重建
     */
    private boolean ensureToken(String ownerType, Long ownerId, String plain, boolean changed)
    {
        if (piiSearchTokenService == null || ownerId == null || StringUtils.isEmpty(plain))
        {
            return false;
        }
        if (!changed && piiSearchTokenService.countByOwner(ownerType, ownerId) > 0)
        {
            return false;
        }
        piiSearchTokenService.rebuild(ownerType, ownerId, plain);
        return true;
    }

    /**
     * 解密库中原始值为明文：null/空 → null；密文 → 解密；存量明文 → 原样。
     */
    private String decryptOrNull(String stored)
    {
        if (StringUtils.isEmpty(stored))
        {
            return null;
        }
        return PiiCryptoUtils.isEncrypted(stored) ? PiiCryptoUtils.decrypt(stored) : stored;
    }

    private String asString(Object value)
    {
        return value == null ? null : value.toString();
    }

    private Long asLong(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Long)
        {
            return (Long) value;
        }
        if (value instanceof Number)
        {
            return ((Number) value).longValue();
        }
        return Long.valueOf(value.toString());
    }

    // ------------------------------------------------------------ G1-b3 密钥轮换

    @Override
    public Map<String, Object> rotateKey()
    {
        int profiles = 0;
        int records = 0;
        int archives = 0;
        int ledgers = 0;
        int recordingEncrypted = 0;
        int failed = 0;

        // 1. 来电人档案：号码 + 身份证重加密 + 盲索引重建 + token 重建
        List<AiCallerProfile> profileRows = callerProfileMapper.selectAiCallerProfileList(new AiCallerProfile());
        for (AiCallerProfile row : profileRows)
        {
            try
            {
                AiCallerProfile update = new AiCallerProfile();
                update.setProfileId(row.getProfileId());
                reEncryptField(row.getCallerNumber(), (cipher, idx) -> {
                    update.setCallerNumber(cipher);
                    update.setCallerNumberIndex(idx);
                });
                reEncryptField(row.getCallerIdCard(), (cipher, idx) -> {
                    update.setCallerIdCard(cipher);
                    update.setCallerIdCardIndex(idx);
                });
                if (update.getCallerNumber() != null || update.getCallerIdCard() != null
                        || update.getCallerNumberIndex() != null || update.getCallerIdCardIndex() != null)
                {
                    callerProfileMapper.updateAiCallerProfile(update);
                }
                if (piiSearchTokenService != null)
                {
                    piiSearchTokenService.rebuild(IPiiSearchTokenService.OWNER_CALLER_PROFILE,
                            row.getProfileId(), decryptPlain(row.getCallerNumber()));
                }
                profiles++;
            }
            catch (Exception e)
            {
                failed++;
                log.warn("密钥轮换-档案失败 profileId={}: {}", row.getProfileId(), e.getMessage());
            }
        }

        // 2. 话单热表：号码重加密 + 盲索引重建 + token 重建 + 录音重加密
        List<AiCallRecord> hotRows = callRecordMapper.selectAiCallRecordList(new AiCallRecord());
        for (AiCallRecord row : hotRows)
        {
            try
            {
                String plain = decryptPlain(row.getCallerNumber());
                if (plain != null)
                {
                    AiCallRecord update = new AiCallRecord();
                    update.setRecordId(row.getRecordId());
                    update.setCallerNumber(PiiCryptoUtils.encrypt(plain));
                    update.setCallerNumberIndex(PiiCryptoUtils.blindIndex(plain));
                    callRecordMapper.updateAiCallRecord(update);
                    if (piiSearchTokenService != null)
                    {
                        piiSearchTokenService.rebuild(IPiiSearchTokenService.OWNER_CALL_RECORD,
                                row.getRecordId(), plain);
                    }
                }
                reEncryptRecording(row.getRecordFile());
                recordingEncrypted++;
                records++;
            }
            catch (Exception e)
            {
                failed++;
                log.warn("密钥轮换-话单失败 recordId={}: {}", row.getRecordId(), e.getMessage());
            }
        }

        // 3. 话单归档表：镜像热表处理（token 属主仍为 CALL_RECORD）
        List<AiCallRecord> archiveRows = archiveMapper.selectArchiveList(new AiCallRecord());
        for (AiCallRecord row : archiveRows)
        {
            try
            {
                String plain = decryptPlain(row.getCallerNumber());
                if (plain != null)
                {
                    archiveMapper.updateArchiveEncryption(row.getRecordId(),
                            PiiCryptoUtils.encrypt(plain), PiiCryptoUtils.blindIndex(plain));
                    if (piiSearchTokenService != null)
                    {
                        piiSearchTokenService.rebuild(IPiiSearchTokenService.OWNER_CALL_RECORD,
                                row.getRecordId(), plain);
                    }
                }
                archives++;
            }
            catch (Exception e)
            {
                failed++;
                log.warn("密钥轮换-归档失败 recordId={}: {}", row.getRecordId(), e.getMessage());
            }
        }

        // 4. 台账：电话 + 身份证重加密 + token 重建
        List<AiCallLedger> ledgerRows = callLedgerMapper.selectAiCallLedgerList(new AiCallLedger());
        for (AiCallLedger row : ledgerRows)
        {
            try
            {
                AiCallLedger update = new AiCallLedger();
                update.setLedgerId(row.getLedgerId());
                String phone = decryptPlain(row.getCallerPhone());
                String idCard = decryptPlain(row.getCallerIdCard());
                if (phone != null)
                {
                    update.setCallerPhone(PiiCryptoUtils.encrypt(phone));
                    if (piiSearchTokenService != null)
                    {
                        piiSearchTokenService.rebuild(IPiiSearchTokenService.OWNER_CALL_LEDGER,
                                row.getLedgerId(), phone);
                    }
                }
                if (idCard != null)
                {
                    update.setCallerIdCard(PiiCryptoUtils.encrypt(idCard));
                }
                if (update.getCallerPhone() != null || update.getCallerIdCard() != null)
                {
                    callLedgerMapper.updateAiCallLedger(update);
                }
                ledgers++;
            }
            catch (Exception e)
            {
                failed++;
                log.warn("密钥轮换-台账失败 ledgerId={}: {}", row.getLedgerId(), e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("profiles", profiles);
        result.put("records", records);
        result.put("archives", archives);
        result.put("ledgers", ledgers);
        result.put("recordingEncrypted", recordingEncrypted);
        result.put("failed", failed);
        return result;
    }

    /** 字段重加密回调：接受新密文与盲索引 */
    private interface FieldCipherSetter
    {
        void set(String cipher, String blindIndex);
    }

    /** 解密字段值（兼容拦截器已解密为明文/或仍为密文两种情况） */
    private String decryptPlain(String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return null;
        }
        return PiiCryptoUtils.isEncrypted(value) ? PiiCryptoUtils.decrypt(value) : value;
    }

    /** 单字段重加密 + 盲索引重建 */
    private void reEncryptField(String value, FieldCipherSetter setter)
    {
        String plain = decryptPlain(value);
        if (plain == null)
        {
            return;
        }
        setter.set(PiiCryptoUtils.encrypt(plain), PiiCryptoUtils.blindIndex(plain));
    }

    /** 录音文件重加密：load（明文）→ save（加密覆盖） */
    private void reEncryptRecording(String recordFile)
    {
        if (recordingStorageService == null || StringUtils.isEmpty(recordFile))
        {
            return;
        }
        try (java.io.InputStream in = recordingStorageService.load(recordFile))
        {
            if (in == null)
            {
                return;
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1)
            {
                bos.write(buf, 0, n);
            }
            byte[] plain = bos.toByteArray();
            // save 内部会加密（encrypt-enabled 开启时）；未开启则为空操作
            recordingStorageService.save(recordFile, new java.io.ByteArrayInputStream(plain),
                    plain.length, "application/octet-stream");
        }
        catch (Exception e)
        {
            log.warn("密钥轮换-录音重加密失败 file={}: {}", recordFile, e.getMessage());
        }
    }
}
