package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiRiskWarning;

public interface IAiRiskWarningService
{
    public AiRiskWarning selectAiRiskWarningByWarningId(Long warningId);

    public List<AiRiskWarning> selectAiRiskWarningList(AiRiskWarning aiRiskWarning);

    public int insertAiRiskWarning(AiRiskWarning aiRiskWarning);

    public int updateAiRiskWarning(AiRiskWarning aiRiskWarning);

    public int deleteAiRiskWarningByWarningIds(Long[] warningIds);
}
