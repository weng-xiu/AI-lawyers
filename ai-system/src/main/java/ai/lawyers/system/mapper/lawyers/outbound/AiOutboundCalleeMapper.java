package ai.lawyers.system.mapper.lawyers.outbound;

import java.util.List;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundCallee;

public interface AiOutboundCalleeMapper
{
    public AiOutboundCallee selectAiOutboundCalleeByCalleeId(Long calleeId);

    public List<AiOutboundCallee> selectAiOutboundCalleeList(AiOutboundCallee aiOutboundCallee);

    public List<AiOutboundCallee> selectAiOutboundCalleeByTaskId(Long taskId);

    public List<AiOutboundCallee> selectPendingCallees(Long taskId, int limit);

    public int insertAiOutboundCallee(AiOutboundCallee aiOutboundCallee);

    public int batchInsertCallees(List<AiOutboundCallee> callees);

    public int updateAiOutboundCallee(AiOutboundCallee aiOutboundCallee);

    public int updateCalleeStatus(AiOutboundCallee aiOutboundCallee);

    public int deleteAiOutboundCalleeByCalleeId(Long calleeId);

    public int deleteAiOutboundCalleeByTaskId(Long taskId);

    public int deleteAiOutboundCalleeByCalleeIds(Long[] calleeIds);

    public int countByTaskIdAndStatus(Long taskId, String callStatus);
}
