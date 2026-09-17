package ai.lawyers.system.mapper.lawyers;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
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

    /**
     * 将所有 status=0(待回访) 且 plan_time 早于指定时间的任务标记为 status=2(已逾期)。
     * 供定时任务每分钟扫描调用。
     *
     * @param now 当前时间
     * @return 更新行数
     */
    public int markOverdueTasks(@Param("now") Date now);

    /**
     * F5 查询已逾期（status=2）且仍未回访的任务，供逐级升级定时任务扫描。
     * 高优先级优先、计划时间最早优先，限制单轮批量。
     *
     * @param limit 单轮最多处理条数
     */
    public List<AiReturnVisitTask> selectOverdueTasksForEscalate(@Param("limit") int limit);
}
