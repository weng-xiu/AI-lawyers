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

    @Override
    public Map<String, Object> migrateCallerProfiles()
    {
        int migrated = 0;
        int conflictSkipped = 0;
        int tokenRebuilt = 0;
        List<AiCallerProfile> rows = callerProfileMapper.selectAiCallerProfileList(new AiCallerProfile());
        for (AiCallerProfile row : rows)
        {
            AiCallerProfile update = new AiCallerProfile();
            update.setProfileId(row.getProfileId());
            fillEncryption(row.getCallerNumber(), row.getCallerNumberIndex(), update, true);
            fillEncryption(row.getCallerIdCard(), row.getCallerIdCardIndex(), update, false);
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
            // G1-b2：号码 token 回填（含未落库变更的行，首轮迁移统一补齐）
            if (rebuildToken(IPiiSearchTokenService.OWNER_CALLER_PROFILE,
                    row.getProfileId(), row.getCallerNumber()))
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
        List<AiCallRecord> hotRows = callRecordMapper.selectAiCallRecordList(new AiCallRecord());
        for (AiCallRecord row : hotRows)
        {
            try
            {
                String raw = row.getCallerNumber();
                String plain = StringUtils.isNotEmpty(raw) ? PiiCryptoUtils.decrypt(raw) : null;
                boolean needCipher = plain != null && !PiiCryptoUtils.isEncrypted(raw);
                boolean needIndex = plain != null && StringUtils.isEmpty(row.getCallerNumberIndex());
                if (needCipher || needIndex)
                {
                    AiCallRecord update = new AiCallRecord();
                    update.setRecordId(row.getRecordId());
                    if (needCipher)
                    {
                        update.setCallerNumber(PiiCryptoUtils.encrypt(plain));
                    }
                    update.setCallerNumberIndex(PiiCryptoUtils.blindIndex(plain));
                    callRecordMapper.updateAiCallRecord(update);
                    migrated++;
                }
                if (rebuildToken(IPiiSearchTokenService.OWNER_CALL_RECORD,
                        row.getRecordId(), plain))
                {
                    tokenRebuilt++;
                }
            }
            catch (Exception e)
            {
                failed++;
                log.warn("话单 PII 迁移单行失败 recordId={}: {}", row.getRecordId(), e.getMessage());
            }
        }

        // 归档表（token 属主仍为 CALL_RECORD：record_id 稳定，归档不另建属主）
        int archiveTotal = 0;
        List<AiCallRecord> archiveRows = archiveMapper.selectArchiveList(new AiCallRecord());
        for (AiCallRecord row : archiveRows)
        {
            archiveTotal++;
            try
            {
                String raw = row.getCallerNumber();
                String plain = StringUtils.isNotEmpty(raw) ? PiiCryptoUtils.decrypt(raw) : null;
                boolean needCipher = plain != null && !PiiCryptoUtils.isEncrypted(raw);
                boolean needIndex = plain != null && StringUtils.isEmpty(row.getCallerNumberIndex());
                if (needCipher || needIndex)
                {
                    archiveMapper.updateArchiveEncryption(row.getRecordId(),
                            needCipher ? PiiCryptoUtils.encrypt(plain) : null,
                            needIndex ? PiiCryptoUtils.blindIndex(plain) : null);
                    migrated++;
                }
                if (rebuildToken(IPiiSearchTokenService.OWNER_CALL_RECORD,
                        row.getRecordId(), plain))
                {
                    tokenRebuilt++;
                }
            }
            catch (Exception e)
            {
                failed++;
                log.warn("归档话单 PII 迁移单行失败 recordId={}: {}", row.getRecordId(), e.getMessage());
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
        List<AiCallLedger> rows = callLedgerMapper.selectAiCallLedgerList(new AiCallLedger());
        for (AiCallLedger row : rows)
        {
            try
            {
                String plainPhone = StringUtils.isNotEmpty(row.getCallerPhone())
                        ? PiiCryptoUtils.decrypt(row.getCallerPhone()) : null;
                String plainIdCard = StringUtils.isNotEmpty(row.getCallerIdCard())
                        ? PiiCryptoUtils.decrypt(row.getCallerIdCard()) : null;
                boolean needPhoneCipher = plainPhone != null
                        && !PiiCryptoUtils.isEncrypted(row.getCallerPhone());
                boolean needIdCardCipher = plainIdCard != null
                        && !PiiCryptoUtils.isEncrypted(row.getCallerIdCard());
                if (needPhoneCipher || needIdCardCipher)
                {
                    AiCallLedger update = new AiCallLedger();
                    update.setLedgerId(row.getLedgerId());
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
                if (rebuildToken(IPiiSearchTokenService.OWNER_CALL_LEDGER,
                        row.getLedgerId(), plainPhone))
                {
                    tokenRebuilt++;
                }
            }
            catch (Exception e)
            {
                failed++;
                log.warn("台账 PII 迁移单行失败 ledgerId={}: {}", row.getLedgerId(), e.getMessage());
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", rows.size());
        result.put("migrated", migrated);
        result.put("tokenRebuilt", tokenRebuilt);
        result.put("failed", failed);
        return result;
    }

    /** 重建单个属主的号码 token；服务不可用/明文为空时返回 false */
    private boolean rebuildToken(String ownerType, Long ownerId, String plain)
    {
        if (piiSearchTokenService == null || ownerId == null || StringUtils.isEmpty(plain))
        {
            return false;
        }
        piiSearchTokenService.rebuild(ownerType, ownerId, plain);
        return true;
    }

    /** 字段迁移：明文 → 密文 + 盲索引；密文缺索引 → 解密回填索引；其余不动 */
    private void fillEncryption(String value, String existingIndex, AiCallerProfile update, boolean isNumber)
    {
        if (StringUtils.isEmpty(value))
        {
            return;
        }
        if (PiiCryptoUtils.isEncrypted(value))
        {
            if (StringUtils.isEmpty(existingIndex))
            {
                String index = PiiCryptoUtils.blindIndex(PiiCryptoUtils.decrypt(value));
                if (isNumber)
                {
                    update.setCallerNumberIndex(index);
                }
                else
                {
                    update.setCallerIdCardIndex(index);
                }
            }
            return;
        }
        String encrypted = PiiCryptoUtils.encrypt(value);
        String index = PiiCryptoUtils.blindIndex(value);
        if (isNumber)
        {
            update.setCallerNumber(encrypted);
            update.setCallerNumberIndex(index);
        }
        else
        {
            update.setCallerIdCard(encrypted);
            update.setCallerIdCardIndex(index);
        }
    }
}
