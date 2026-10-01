package ai.lawyers.system.service.lawyers;

import java.util.Map;

/**
 * PII 字段级加密存量迁移（P3-G1-b V2.55 / P3-G1-b2 V2.56）。
 *
 * @author ai-lawyers
 */
public interface IPiiCryptoMigrationService
{
    /**
     * 扫描 ai_caller_profile 存量行：明文字段转 SM4-GCM 密文并回填盲索引；
     * 已密文但缺盲索引的行仅回填索引；G1-b2 起同时回填号码模糊检索 token。
     * 幂等可重复执行。
     *
     * @return {total: 扫描总数, migrated: 本次迁移行数, conflictSkipped: 唯一索引冲突跳过数,
     *          tokenRebuilt: token 回填行数}
     */
    public Map<String, Object> migrateCallerProfiles();

    /**
     * G1-b2：扫描 ai_call_record（热表）与 ai_call_record_archive（归档表）存量号码：
     * 明文转 SM4-GCM 密文并回填盲索引，重建 CALL_RECORD 位置分片模糊检索 token。
     * 已密文的行仅补缺索引/token。幂等可重复执行。
     *
     * @return {hotTotal/archiveTotal: 扫描行数, migrated: 本次落库变更行数,
     *          tokenRebuilt: token 回填行数, failed: 单行异常计数}
     */
    public Map<String, Object> migrateCallRecords();

    /**
     * G1-b2：扫描 ai_call_ledger 存量联系电话/身份证号：明文转 SM4-GCM 密文，
     * 重建 CALL_LEDGER 位置分片模糊检索 token。幂等可重复执行。
     *
     * @return {total: 扫描行数, migrated: 本次落库变更行数,
     *          tokenRebuilt: token 回填行数, failed: 单行异常计数}
     */
    public Map<String, Object> migrateCallLedgers();
}
