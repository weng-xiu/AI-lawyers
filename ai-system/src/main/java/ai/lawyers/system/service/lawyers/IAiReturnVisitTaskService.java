package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiReturnVisitTask;

/**
 * 回访任务Service接口
 *
 * @author ai-lawyers
 */
public interface IAiReturnVisitTaskService
{
    public AiReturnVisitTask selectAiReturnVisitTaskByTaskId(Long taskId);

    public List<AiReturnVisitTask> selectAiReturnVisitTaskList(AiReturnVisitTask aiReturnVisitTask);

    public int insertAiReturnVisitTask(AiReturnVisitTask aiReturnVisitTask);

    public int updateAiReturnVisitTask(AiReturnVisitTask aiReturnVisitTask);

    public int deleteAiReturnVisitTaskByTaskId(Long taskId);

    public int deleteAiReturnVisitTaskByTaskIds(Long[] taskIds);

    /** 回访任务统计 */
    public java.util.Map<String, Object> selectReturnVisitTaskStats();
}
