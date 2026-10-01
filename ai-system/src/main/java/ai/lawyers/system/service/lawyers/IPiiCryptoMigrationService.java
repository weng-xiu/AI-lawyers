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

    /**
     * G1-b3：密钥轮换——全量重加密。
     * <p>前置条件：已将新根密钥写入 {@code APP_SECRET_KEY}，旧根密钥写入
     * {@code APP_PREVIOUS_SECRET_KEY}（应用同时支持双密钥解密）。</p>
     * <p>处理范围：</p>
     * <ul>
     *   <li>PII 字段（档案/话单热表+归档/台账）：旧密钥解密 → 新密钥重加密（密文升级为版本化 encp2:）；</li>
     *   <li>盲索引列：全量重建（密钥变更后索引值全变）；</li>
     *   <li>模糊检索 token 表：全量重建；</li>
     *   <li>录音文件（本地/对象存储）：解密 → 重加密。</li>
     * </ul>
     * 幂等：已是新密钥版本的密文 IV 会变化但语义等价，可安全重复执行。
     *
     * @return {profiles/records/archives/ledgers: 各类处理行数, recordingEncrypted: 录音重加密数,
     *          failed: 异常计数}
     */
    public Map<String, Object> rotateKey();
}
