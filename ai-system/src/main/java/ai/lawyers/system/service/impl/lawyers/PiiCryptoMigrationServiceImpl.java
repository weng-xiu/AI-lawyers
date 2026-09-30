package ai.lawyers.system.service.impl.lawyers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.common.utils.sign.PiiCryptoUtils;
import ai.lawyers.system.domain.lawyers.AiCallerProfile;
import ai.lawyers.system.mapper.lawyers.AiCallerProfileMapper;
import ai.lawyers.system.service.lawyers.IPiiCryptoMigrationService;

/**
 * P3-G1-b：ai_caller_profile 存量 PII 明文 → SM4-GCM 密文 + 盲索引回填迁移。
 *
 * <p>幂等：已密文且索引齐全的行跳过；每行独立处理，迁移窗口内同号新建档案
 * 与存量行撞盲索引唯一键时单独计数跳过，不中断整体迁移。</p>
 *
 * @author ai-lawyers
 */
@Service
public class PiiCryptoMigrationServiceImpl implements IPiiCryptoMigrationService
{
    @Autowired
    private AiCallerProfileMapper callerProfileMapper;

    @Override
    public Map<String, Object> migrateCallerProfiles()
    {
        int migrated = 0;
        int conflictSkipped = 0;
        List<AiCallerProfile> rows = callerProfileMapper.selectAiCallerProfileList(new AiCallerProfile());
        for (AiCallerProfile row : rows)
        {
            AiCallerProfile update = new AiCallerProfile();
            update.setProfileId(row.getProfileId());
            fillEncryption(row.getCallerNumber(), row.getCallerNumberIndex(), update, true);
            fillEncryption(row.getCallerIdCard(), row.getCallerIdCardIndex(), update, false);
            if (update.getCallerNumber() == null && update.getCallerNumberIndex() == null
                    && update.getCallerIdCard() == null && update.getCallerIdCardIndex() == null)
            {
                continue;
            }
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
        Map<String, Object> result = new HashMap<>();
        result.put("total", rows.size());
        result.put("migrated", migrated);
        result.put("conflictSkipped", conflictSkipped);
        return result;
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
