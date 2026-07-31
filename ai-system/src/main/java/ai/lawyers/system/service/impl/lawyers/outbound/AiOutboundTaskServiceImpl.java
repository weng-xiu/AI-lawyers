package ai.lawyers.system.service.impl.lawyers.outbound;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundTask;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundTaskMapper;
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundTaskService;

@Service
public class AiOutboundTaskServiceImpl implements IAiOutboundTaskService
{
    @Autowired
    private AiOutboundTaskMapper aiOutboundTaskMapper;

    @Override
    public AiOutboundTask selectAiOutboundTaskByTaskId(Long taskId)
    {
        return aiOutboundTaskMapper.selectAiOutboundTaskByTaskId(taskId);
    }

    @Override
    public List<AiOutboundTask> selectAiOutboundTaskList(AiOutboundTask aiOutboundTask)
    {
        return aiOutboundTaskMapper.selectAiOutboundTaskList(aiOutboundTask);
    }

    @Override
    public AiOutboundTask selectAiOutboundTaskByTaskNo(String taskNo)
    {
        return aiOutboundTaskMapper.selectAiOutboundTaskByTaskNo(taskNo);
    }

    @Override
    public int insertAiOutboundTask(AiOutboundTask aiOutboundTask)
    {
        if (aiOutboundTask.getTaskNo() == null || aiOutboundTask.getTaskNo().isEmpty()) {
            aiOutboundTask.setTaskNo(generateTaskNo());
        }
        return aiOutboundTaskMapper.insertAiOutboundTask(aiOutboundTask);
    }

    @Override
    public int updateAiOutboundTask(AiOutboundTask aiOutboundTask)
    {
        return aiOutboundTaskMapper.updateAiOutboundTask(aiOutboundTask);
    }

    @Override
    public int deleteAiOutboundTaskByTaskId(Long taskId)
    {
        return aiOutboundTaskMapper.deleteAiOutboundTaskByTaskId(taskId);
    }

    @Override
    public int deleteAiOutboundTaskByTaskIds(Long[] taskIds)
    {
        return aiOutboundTaskMapper.deleteAiOutboundTaskByTaskIds(taskIds);
    }

    @Override
    public String generateTaskNo()
    {
        return "OB" + DateUtils.dateTimeNow();
    }

    @Override
    public int startTask(Long taskId)
    {
        AiOutboundTask task = new AiOutboundTask();
        task.setTaskId(taskId);
        task.setStatus("1");
        return aiOutboundTaskMapper.updateTaskStatus(task);
    }

    @Override
    public int pauseTask(Long taskId)
    {
        AiOutboundTask task = new AiOutboundTask();
        task.setTaskId(taskId);
        task.setStatus("3");
        return aiOutboundTaskMapper.updateTaskStatus(task);
    }

    @Override
    public int stopTask(Long taskId)
    {
        AiOutboundTask task = new AiOutboundTask();
        task.setTaskId(taskId);
        task.setStatus("4");
        return aiOutboundTaskMapper.updateTaskStatus(task);
    }
}
