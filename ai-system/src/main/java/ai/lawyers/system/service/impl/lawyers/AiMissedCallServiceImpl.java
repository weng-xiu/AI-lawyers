package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.AiMissedCall;
import ai.lawyers.system.mapper.lawyers.AiMissedCallMapper;
import ai.lawyers.system.service.lawyers.IAiMissedCallService;

/**
 * 未接来电Service实现
 *
 * @author ai-lawyers
 */
@Service
public class AiMissedCallServiceImpl implements IAiMissedCallService
{
    @Autowired
    private AiMissedCallMapper aiMissedCallMapper;

    @Override
    public AiMissedCall selectAiMissedCallByMissedCallId(Long missedCallId)
    {
        return aiMissedCallMapper.selectAiMissedCallByMissedCallId(missedCallId);
    }

    @Override
    public List<AiMissedCall> selectAiMissedCallList(AiMissedCall aiMissedCall)
    {
        return aiMissedCallMapper.selectAiMissedCallList(aiMissedCall);
    }

    @Override
    public int insertAiMissedCall(AiMissedCall aiMissedCall)
    {
        return aiMissedCallMapper.insertAiMissedCall(aiMissedCall);
    }

    @Override
    public int updateAiMissedCall(AiMissedCall aiMissedCall)
    {
        return aiMissedCallMapper.updateAiMissedCall(aiMissedCall);
    }

    @Override
    public int deleteAiMissedCallByMissedCallId(Long missedCallId)
    {
        return aiMissedCallMapper.deleteAiMissedCallByMissedCallId(missedCallId);
    }

    @Override
    public int deleteAiMissedCallByMissedCallIds(Long[] missedCallIds)
    {
        return aiMissedCallMapper.deleteAiMissedCallByMissedCallIds(missedCallIds);
    }

    @Override
    public java.util.Map<String, Object> selectMissedCallStats()
    {
        return aiMissedCallMapper.selectMissedCallStats();
    }
}
