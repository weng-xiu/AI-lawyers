package ai.lawyers.system.service.impl.lawyers.outbound;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundResult;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundResultMapper;
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundResultService;

@Service
public class AiOutboundResultServiceImpl implements IAiOutboundResultService
{
    @Autowired
    private AiOutboundResultMapper aiOutboundResultMapper;

    @Override
    public AiOutboundResult selectAiOutboundResultByResultId(Long resultId)
    {
        return aiOutboundResultMapper.selectAiOutboundResultByResultId(resultId);
    }

    @Override
    public List<AiOutboundResult> selectAiOutboundResultList(AiOutboundResult aiOutboundResult)
    {
        return aiOutboundResultMapper.selectAiOutboundResultList(aiOutboundResult);
    }

    @Override
    public List<AiOutboundResult> selectAiOutboundResultByTaskId(Long taskId)
    {
        return aiOutboundResultMapper.selectAiOutboundResultByTaskId(taskId);
    }

    @Override
    public AiOutboundResult selectAiOutboundResultByCalleeId(Long calleeId)
    {
        return aiOutboundResultMapper.selectAiOutboundResultByCalleeId(calleeId);
    }

    @Override
    public int insertAiOutboundResult(AiOutboundResult aiOutboundResult)
    {
        return aiOutboundResultMapper.insertAiOutboundResult(aiOutboundResult);
    }

    @Override
    public Map<String, Object> getTaskResultStatistics(Long taskId)
    {
        Map<String, Object> stats = new HashMap<>();
        List<AiOutboundResult> results = selectAiOutboundResultByTaskId(taskId);
        int total = results.size();
        int answered = 0;
        int noAnswer = 0;
        int failed = 0;
        int interested = 0;
        int notInterested = 0;
        for (AiOutboundResult result : results) {
            if ("1".equals(result.getCallResult())) {
                answered++;
            } else if ("2".equals(result.getCallResult())) {
                noAnswer++;
            } else if ("3".equals(result.getCallResult())) {
                failed++;
            }
            if ("1".equals(result.getIntentionCode())) {
                interested++;
            } else if ("2".equals(result.getIntentionCode())) {
                notInterested++;
            }
        }
        stats.put("total", total);
        stats.put("answered", answered);
        stats.put("noAnswer", noAnswer);
        stats.put("failed", failed);
        stats.put("interested", interested);
        stats.put("notInterested", notInterested);
        return stats;
    }
}
