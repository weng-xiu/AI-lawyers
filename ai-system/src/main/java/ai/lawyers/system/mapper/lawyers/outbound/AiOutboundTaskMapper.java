package ai.lawyers.system.mapper.lawyers.outbound;

import java.util.List;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundTask;

public interface AiOutboundTaskMapper
{
    public AiOutboundTask selectAiOutboundTaskByTaskId(Long taskId);

    public List<AiOutboundTask> selectAiOutboundTaskList(AiOutboundTask aiOutboundTask);

    public AiOutboundTask selectAiOutboundTaskByTaskNo(String taskNo);

    public List<AiOutboundTask> selectRunningTasks();

    public int insertAiOutboundTask(AiOutboundTask aiOutboundTask);

    public int updateAiOutboundTask(AiOutboundTask aiOutboundTask);

    public int deleteAiOutboundTaskByTaskId(Long taskId);

    public int deleteAiOutboundTaskByTaskIds(Long[] taskIds);

    public int updateTaskStatus(AiOutboundTask aiOutboundTask);

    public int incrementCompletedCount(Long taskId);

    public int incrementAnsweredCount(Long taskId);

    public int incrementFailedCount(Long taskId);

    public int incrementNoAnswerCount(Long taskId);
}
