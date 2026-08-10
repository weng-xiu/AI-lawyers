package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiRiskWarningRule;

public interface AiRiskWarningRuleMapper
{
    public AiRiskWarningRule selectAiRiskWarningRuleByRuleId(Long ruleId);

    public List<AiRiskWarningRule> selectAiRiskWarningRuleList(AiRiskWarningRule aiRiskWarningRule);

    public int insertAiRiskWarningRule(AiRiskWarningRule aiRiskWarningRule);

    public int updateAiRiskWarningRule(AiRiskWarningRule aiRiskWarningRule);

    public int deleteAiRiskWarningRuleByRuleId(Long ruleId);

    public int deleteAiRiskWarningRuleByRuleIds(Long[] ruleIds);
}
