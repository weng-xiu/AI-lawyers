package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.AiCallback;
import ai.lawyers.system.mapper.lawyers.AiCallbackMapper;
import ai.lawyers.system.service.lawyers.IAiCallbackService;

/**
 * 客户回访Service实现
 *
 * @author ai-lawyers
 */
@Service
public class AiCallbackServiceImpl implements IAiCallbackService
{
    @Autowired
    private AiCallbackMapper aiCallbackMapper;

    @Override
    public AiCallback selectAiCallbackByCallbackId(Long callbackId)
    {
        return aiCallbackMapper.selectAiCallbackByCallbackId(callbackId);
    }

    @Override
    public List<AiCallback> selectAiCallbackList(AiCallback aiCallback)
    {
        return aiCallbackMapper.selectAiCallbackList(aiCallback);
    }

    @Override
    public int insertAiCallback(AiCallback aiCallback)
    {
        return aiCallbackMapper.insertAiCallback(aiCallback);
    }

    @Override
    public int updateAiCallback(AiCallback aiCallback)
    {
        return aiCallbackMapper.updateAiCallback(aiCallback);
    }

    @Override
    public int deleteAiCallbackByCallbackId(Long callbackId)
    {
        return aiCallbackMapper.deleteAiCallbackByCallbackId(callbackId);
    }

    @Override
    public int deleteAiCallbackByCallbackIds(Long[] callbackIds)
    {
        return aiCallbackMapper.deleteAiCallbackByCallbackIds(callbackIds);
    }

    @Override
    public java.util.Map<String, Object> selectCallbackStats()
    {
        return aiCallbackMapper.selectCallbackStats();
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> selectSatisfactionTrend(Integer days)
    {
        if (days == null || days <= 0) {
            days = 7;
        }
        return aiCallbackMapper.selectSatisfactionTrend(days);
    }
}
