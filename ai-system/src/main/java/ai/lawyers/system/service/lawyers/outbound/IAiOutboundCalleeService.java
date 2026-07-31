package ai.lawyers.system.service.lawyers.outbound;

import java.util.List;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundCallee;

public interface IAiOutboundCalleeService
{
    public AiOutboundCallee selectAiOutboundCalleeByCalleeId(Long calleeId);

    public List<AiOutboundCallee> selectAiOutboundCalleeList(AiOutboundCallee aiOutboundCallee);

    public List<AiOutboundCallee> selectAiOutboundCalleeByTaskId(Long taskId);

    public int insertAiOutboundCallee(AiOutboundCallee aiOutboundCallee);

    public int batchInsertCallees(Long taskId, List<AiOutboundCallee> callees);

    public int updateAiOutboundCallee(AiOutboundCallee aiOutboundCallee);

    public int deleteAiOutboundCalleeByCalleeId(Long calleeId);

    public int deleteAiOutboundCalleeByTaskId(Long taskId);

    public int deleteAiOutboundCalleeByCalleeIds(Long[] calleeIds);

    public int importCalleesFromExcel(Long taskId, List<AiOutboundCallee> callees);
}
