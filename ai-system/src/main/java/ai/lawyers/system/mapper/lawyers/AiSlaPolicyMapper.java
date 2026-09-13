package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.AiSlaPolicy;

/**
 * 工单 SLA 策略 Mapper（F9）
 *
 * @author ai-lawyers
 */
public interface AiSlaPolicyMapper
{
    public AiSlaPolicy selectAiSlaPolicyByPolicyId(Long policyId);

    public List<AiSlaPolicy> selectAiSlaPolicyList(AiSlaPolicy query);

    public int insertAiSlaPolicy(AiSlaPolicy policy);

    public int updateAiSlaPolicy(AiSlaPolicy policy);

    public int deleteAiSlaPolicyByPolicyIds(Long[] policyIds);

    /**
     * 按业务类型+优先级匹配启用中的策略；未配置时回退 TICKET + 普通优先级。
     */
    public AiSlaPolicy selectMatchedPolicy(@Param("bizType") String bizType,
                                           @Param("priority") String priority);
}
