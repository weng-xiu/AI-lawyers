package ai.lawyers.system.service.impl.lawyers;

import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.AiReturnVisitTask;
import ai.lawyers.system.mapper.lawyers.AiReturnVisitTaskMapper;
import ai.lawyers.system.service.lawyers.IAiReturnVisitTaskService;

/**
 * 回访任务Service实现
 *
 * @author ai-lawyers
 */
@Service
public class AiReturnVisitTaskServiceImpl implements IAiReturnVisitTaskService
{
    private static final Logger log = LoggerFactory.getLogger(AiReturnVisitTaskServiceImpl.class);

    @Autowired
    private AiReturnVisitTaskMapper aiReturnVisitTaskMapper;

    @Override
    public AiReturnVisitTask selectAiReturnVisitTaskByTaskId(Long taskId)
    {
        return aiReturnVisitTaskMapper.selectAiReturnVisitTaskByTaskId(taskId);
    }

    @Override
    public List<AiReturnVisitTask> selectAiReturnVisitTaskList(AiReturnVisitTask aiReturnVisitTask)
    {
        return aiReturnVisitTaskMapper.selectAiReturnVisitTaskList(aiReturnVisitTask);
    }

    @Override
    public int insertAiReturnVisitTask(AiReturnVisitTask aiReturnVisitTask)
    {
        return aiReturnVisitTaskMapper.insertAiReturnVisitTask(aiReturnVisitTask);
    }

    @Override
    public int updateAiReturnVisitTask(AiReturnVisitTask aiReturnVisitTask)
    {
        return aiReturnVisitTaskMapper.updateAiReturnVisitTask(aiReturnVisitTask);
    }

    @Override
    public int deleteAiReturnVisitTaskByTaskId(Long taskId)
    {
        return aiReturnVisitTaskMapper.deleteAiReturnVisitTaskByTaskId(taskId);
    }

    @Override
    public int deleteAiReturnVisitTaskByTaskIds(Long[] taskIds)
    {
        return aiReturnVisitTaskMapper.deleteAiReturnVisitTaskByTaskIds(taskIds);
    }

    @Override
    public java.util.Map<String, Object> selectReturnVisitTaskStats()
    {
        return aiReturnVisitTaskMapper.selectReturnVisitTaskStats();
    }

    /**
     * 回访任务逾期扫描：每分钟扫描一次，将 status=0(待回访) 且 plan_time 已过的
     * 任务标记为 status=2(已逾期)。
     */
    @Scheduled(fixedDelayString = "${call.returnVisit.overdueScanIntervalMs:60000}", initialDelay = 30000)
    public void scanOverdueTasks()
    {
        try
        {
            int rows = aiReturnVisitTaskMapper.markOverdueTasks(new Date());
            if (rows > 0)
            {
                log.info("[ReturnVisit] 逾期扫描完成，标记 {} 个回访任务为已逾期", rows);
            }
        }
        catch (Exception e)
        {
            log.error("[ReturnVisit] 回访任务逾期扫描异常", e);
        }
    }
}
