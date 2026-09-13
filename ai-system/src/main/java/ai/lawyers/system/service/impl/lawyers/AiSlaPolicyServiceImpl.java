package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiSlaPolicy;
import ai.lawyers.system.mapper.lawyers.AiSlaPolicyMapper;
import ai.lawyers.system.service.lawyers.IAiSlaPolicyService;

/**
 * 工单 SLA 策略 Service 实现（F9）
 *
 * @author ai-lawyers
 */
@Service
public class AiSlaPolicyServiceImpl implements IAiSlaPolicyService
{
    @Autowired
    private AiSlaPolicyMapper aiSlaPolicyMapper;

    @Override
    public AiSlaPolicy selectAiSlaPolicyByPolicyId(Long policyId)
    {
        return aiSlaPolicyMapper.selectAiSlaPolicyByPolicyId(policyId);
    }

    @Override
    public List<AiSlaPolicy> selectAiSlaPolicyList(AiSlaPolicy query)
    {
        return aiSlaPolicyMapper.selectAiSlaPolicyList(query);
    }

    @Override
    public int insertAiSlaPolicy(AiSlaPolicy policy)
    {
        validate(policy);
        if (StringUtils.isEmpty(policy.getStatus()))
        {
            policy.setStatus("0");
        }
        return aiSlaPolicyMapper.insertAiSlaPolicy(policy);
    }

    @Override
    public int updateAiSlaPolicy(AiSlaPolicy policy)
    {
        if (policy.getPolicyId() == null)
        {
            throw new ServiceException("策略ID不能为空");
        }
        validate(policy);
        return aiSlaPolicyMapper.updateAiSlaPolicy(policy);
    }

    @Override
    public int deleteAiSlaPolicyByPolicyIds(Long[] policyIds)
    {
        return aiSlaPolicyMapper.deleteAiSlaPolicyByPolicyIds(policyIds);
    }

    @Override
    public AiSlaPolicy matchPolicy(String bizType, String priority)
    {
        return aiSlaPolicyMapper.selectMatchedPolicy(
                StringUtils.isEmpty(bizType) ? "TICKET" : bizType,
                StringUtils.isEmpty(priority) ? "2" : priority);
    }

    private void validate(AiSlaPolicy policy)
    {
        if (policy == null)
        {
            throw new ServiceException("策略信息不能为空");
        }
        if (StringUtils.isEmpty(policy.getPolicyName()))
        {
            throw new ServiceException("策略名称不能为空");
        }
        if (StringUtils.isEmpty(policy.getBizType()) || StringUtils.isEmpty(policy.getPriority()))
        {
            throw new ServiceException("业务类型与优先级不能为空");
        }
        if (policy.getRespondMinutes() == null || policy.getRespondMinutes() <= 0)
        {
            throw new ServiceException("响应时限必须为正整数（分钟）");
        }
        if (policy.getResolveMinutes() == null || policy.getResolveMinutes() <= 0)
        {
            throw new ServiceException("办结时限必须为正整数（分钟）");
        }
        if (policy.getResolveMinutes() < policy.getRespondMinutes())
        {
            throw new ServiceException("办结时限不能小于响应时限");
        }
    }
}
