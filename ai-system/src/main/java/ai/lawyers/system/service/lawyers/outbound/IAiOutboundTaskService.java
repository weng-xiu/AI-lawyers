package ai.lawyers.system.service.lawyers.outbound;

import java.util.List;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundTask;

public interface IAiOutboundTaskService
{
    public AiOutboundTask selectAiOutboundTaskByTaskId(Long taskId);

    public List<AiOutboundTask> selectAiOutboundTaskList(AiOutboundTask aiOutboundTask);

    public AiOutboundTask selectAiOutboundTaskByTaskNo(String taskNo);

    public int insertAiOutboundTask(AiOutboundTask aiOutboundTask);

    public int updateAiOutboundTask(AiOutboundTask aiOutboundTask);

    public int deleteAiOutboundTaskByTaskId(Long taskId);

    public int deleteAiOutboundTaskByTaskIds(Long[] taskIds);

    public String generateTaskNo();

    public int startTask(Long taskId);

    public int pauseTask(Long taskId);

    public int stopTask(Long taskId);
}
