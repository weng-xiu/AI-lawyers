package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
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
}
