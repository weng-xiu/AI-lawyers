package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.AiRiskWarning;
import ai.lawyers.system.mapper.lawyers.AiRiskWarningMapper;
import ai.lawyers.system.service.lawyers.IAiRiskWarningService;

@Service
public class AiRiskWarningServiceImpl implements IAiRiskWarningService
{
    @Autowired
    private AiRiskWarningMapper aiRiskWarningMapper;

    @Override
    public AiRiskWarning selectAiRiskWarningByWarningId(Long warningId)
    {
        return aiRiskWarningMapper.selectAiRiskWarningByWarningId(warningId);
    }

    @Override
    public List<AiRiskWarning> selectAiRiskWarningList(AiRiskWarning aiRiskWarning)
    {
        return aiRiskWarningMapper.selectAiRiskWarningList(aiRiskWarning);
    }

    @Override
    public int insertAiRiskWarning(AiRiskWarning aiRiskWarning)
    {
        if (aiRiskWarning.getStatus() == null || aiRiskWarning.getStatus().isEmpty()) {
            aiRiskWarning.setStatus("0");
        }
        if (aiRiskWarning.getWarningLevel() == null || aiRiskWarning.getWarningLevel().isEmpty()) {
            aiRiskWarning.setWarningLevel("3");
        }
        return aiRiskWarningMapper.insertAiRiskWarning(aiRiskWarning);
    }

    @Override
    public int updateAiRiskWarning(AiRiskWarning aiRiskWarning)
    {
        return aiRiskWarningMapper.updateAiRiskWarning(aiRiskWarning);
    }

    @Override
    public int deleteAiRiskWarningByWarningIds(Long[] warningIds)
    {
        return aiRiskWarningMapper.deleteAiRiskWarningByWarningIds(warningIds);
    }
}
