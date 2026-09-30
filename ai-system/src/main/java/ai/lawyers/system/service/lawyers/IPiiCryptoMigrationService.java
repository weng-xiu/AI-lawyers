package ai.lawyers.system.service.lawyers;

import java.util.Map;

/**
 * PII 字段级加密存量迁移（P3-G1-b V2.55）。
 *
 * @author ai-lawyers
 */
public interface IPiiCryptoMigrationService
{
    /**
     * 扫描 ai_caller_profile 存量行：明文字段转 SM4-GCM 密文并回填盲索引；
     * 已密文但缺盲索引的行仅回填索引。幂等可重复执行。
     *
     * @return {total: 扫描总数, migrated: 本次迁移行数, conflictSkipped: 唯一索引冲突跳过数}
     */
    public Map<String, Object> migrateCallerProfiles();
}
