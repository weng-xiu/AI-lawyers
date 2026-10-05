package ai.lawyers.system.service.lawyers;

/**
 * PII 模糊检索 token 服务（P3-G1-b2 V2.56）：维护 ai_pii_search_token 位置分片盲 token。
 *
 * @author ai-lawyers
 */
public interface IPiiSearchTokenService
{
    /** 属主类型：话单 ai_call_record（含归档表同源 record_id） */
    String OWNER_CALL_RECORD = "CALL_RECORD";

    /** 属主类型：咨询台账 ai_call_ledger */
    String OWNER_CALL_LEDGER = "CALL_LEDGER";

    /** 属主类型：来电人档案 ai_caller_profile */
    String OWNER_CALLER_PROFILE = "CALLER_PROFILE";

    /**
     * 按号码明文重建属主全部 token：先删旧 token 再批量写入（幂等可重复执行）。
     * 号码为空时仅清理旧 token（空号码不可被模糊检索）。
     *
     * @param ownerType 属主类型（本接口常量）
     * @param ownerId   属主 ID
     * @param plain     号码明文
     */
    public void rebuild(String ownerType, Long ownerId, String plain);

    /**
     * 统计某属主已有 token 数（迁移幂等：>0 表示 token 已回填）。
     */
    public int countByOwner(String ownerType, Long ownerId);
}
