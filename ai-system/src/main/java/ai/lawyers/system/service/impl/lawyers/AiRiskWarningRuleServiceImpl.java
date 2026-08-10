package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.AiRiskWarningRule;
import ai.lawyers.system.mapper.lawyers.AiRiskWarningRuleMapper;
import ai.lawyers.system.service.lawyers.IAiRiskWarningRuleService;

@Service
public class AiRiskWarningRuleServiceImpl implements IAiRiskWarningRuleService
{
    @Autowired
    private AiRiskWarningRuleMapper aiRiskWarningRuleMapper;

    @Override
    public AiRiskWarningRule selectAiRiskWarningRuleByRuleId(Long ruleId)
    {
        return aiRiskWarningRuleMapper.selectAiRiskWarningRuleByRuleId(ruleId);
    }

    @Override
    public List<AiRiskWarningRule> selectAiRiskWarningRuleList(AiRiskWarningRule aiRiskWarningRule)
    {
        return aiRiskWarningRuleMapper.selectAiRiskWarningRuleList(aiRiskWarningRule);
    }

    @Override
    public int insertAiRiskWarningRule(AiRiskWarningRule aiRiskWarningRule)
    {
        if (aiRiskWarningRule.getIsEnabled() == null || aiRiskWarningRule.getIsEnabled().isEmpty()) {
            aiRiskWarningRule.setIsEnabled("1");
        }
        if (aiRiskWarningRule.getRuleLevel() == null || aiRiskWarningRule.getRuleLevel().isEmpty()) {
            aiRiskWarningRule.setRuleLevel("3");
        }
        return aiRiskWarningRuleMapper.insertAiRiskWarningRule(aiRiskWarningRule);
    }

    @Override
    public int updateAiRiskWarningRule(AiRiskWarningRule aiRiskWarningRule)
    {
        return aiRiskWarningRuleMapper.updateAiRiskWarningRule(aiRiskWarningRule);
    }

    @Override
    public int deleteAiRiskWarningRuleByRuleIds(Long[] ruleIds)
    {
        return aiRiskWarningRuleMapper.deleteAiRiskWarningRuleByRuleIds(ruleIds);
    }
}
