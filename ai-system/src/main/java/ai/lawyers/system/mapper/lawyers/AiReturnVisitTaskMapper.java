package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiReturnVisitTask;

/**
 * 回访任务Mapper接口
 *
 * @author ai-lawyers
 */
public interface AiReturnVisitTaskMapper
{
    public AiReturnVisitTask selectAiReturnVisitTaskByTaskId(Long taskId);

    public List<AiReturnVisitTask> selectAiReturnVisitTaskList(AiReturnVisitTask aiReturnVisitTask);

    public int insertAiReturnVisitTask(AiReturnVisitTask aiReturnVisitTask);

    public int updateAiReturnVisitTask(AiReturnVisitTask aiReturnVisitTask);

    public int deleteAiReturnVisitTaskByTaskId(Long taskId);

    public int deleteAiReturnVisitTaskByTaskIds(Long[] taskIds);

    /** 回访任务统计：总数、待回访、已完成、已逾期 */
    public java.util.Map<String, Object> selectReturnVisitTaskStats();
}
