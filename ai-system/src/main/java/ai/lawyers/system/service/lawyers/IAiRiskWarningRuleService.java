package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiRiskWarningRule;

public interface IAiRiskWarningRuleService
{
    public AiRiskWarningRule selectAiRiskWarningRuleByRuleId(Long ruleId);

    public List<AiRiskWarningRule> selectAiRiskWarningRuleList(AiRiskWarningRule aiRiskWarningRule);

    public int insertAiRiskWarningRule(AiRiskWarningRule aiRiskWarningRule);

    public int updateAiRiskWarningRule(AiRiskWarningRule aiRiskWarningRule);

    public int deleteAiRiskWarningRuleByRuleIds(Long[] ruleIds);
}
