package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiRiskWarning;

public interface AiRiskWarningMapper
{
    public AiRiskWarning selectAiRiskWarningByWarningId(Long warningId);

    public List<AiRiskWarning> selectAiRiskWarningList(AiRiskWarning aiRiskWarning);

    public int insertAiRiskWarning(AiRiskWarning aiRiskWarning);

    public int updateAiRiskWarning(AiRiskWarning aiRiskWarning);

    public int deleteAiRiskWarningByWarningId(Long warningId);

    public int deleteAiRiskWarningByWarningIds(Long[] warningIds);
}
