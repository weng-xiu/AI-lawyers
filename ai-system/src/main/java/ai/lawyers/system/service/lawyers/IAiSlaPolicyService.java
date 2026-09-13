package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiSlaPolicy;

/**
 * 工单 SLA 策略 Service（F9）
 *
 * @author ai-lawyers
 */
public interface IAiSlaPolicyService
{
    public AiSlaPolicy selectAiSlaPolicyByPolicyId(Long policyId);

    public List<AiSlaPolicy> selectAiSlaPolicyList(AiSlaPolicy query);

    public int insertAiSlaPolicy(AiSlaPolicy policy);

    public int updateAiSlaPolicy(AiSlaPolicy policy);

    public int deleteAiSlaPolicyByPolicyIds(Long[] policyIds);

    /**
     * 按业务类型+优先级匹配启用策略（Mapper 内置四级回退：
     * 精确 → 业务类型通配 → TICKET同优先级 → TICKET普通）。
     */
    public AiSlaPolicy matchPolicy(String bizType, String priority);
}
