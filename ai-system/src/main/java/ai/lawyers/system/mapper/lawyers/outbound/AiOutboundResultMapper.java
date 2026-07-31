package ai.lawyers.system.mapper.lawyers.outbound;

import java.util.List;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundResult;

public interface AiOutboundResultMapper
{
    public AiOutboundResult selectAiOutboundResultByResultId(Long resultId);

    public List<AiOutboundResult> selectAiOutboundResultList(AiOutboundResult aiOutboundResult);

    public List<AiOutboundResult> selectAiOutboundResultByTaskId(Long taskId);

    public AiOutboundResult selectAiOutboundResultByCalleeId(Long calleeId);

    public int insertAiOutboundResult(AiOutboundResult aiOutboundResult);
}
