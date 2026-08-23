package ai.lawyers.system.service.impl.lawyers.schedule;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.system.domain.lawyers.schedule.AiAgentSchedule;
import ai.lawyers.system.domain.lawyers.schedule.AiAgentScheduleVO;
import ai.lawyers.system.mapper.lawyers.schedule.AiAgentScheduleMapper;
import ai.lawyers.system.service.lawyers.schedule.IAiAgentScheduleService;

/**
 * 坐席排班 Service 实现
 */
@Service
public class AiAgentScheduleServiceImpl implements IAiAgentScheduleService
{
    @Autowired
    private AiAgentScheduleMapper aiAgentScheduleMapper;

    @Override
    public AiAgentSchedule selectAiAgentScheduleByScheduleId(Long scheduleId)
    {
        return aiAgentScheduleMapper.selectAiAgentScheduleByScheduleId(scheduleId);
    }

    @Override
    public List<AiAgentScheduleVO> selectScheduleList(AiAgentScheduleVO query)
    {
        return aiAgentScheduleMapper.selectScheduleList(query);
    }

    @Override
    public List<AiAgentScheduleVO> selectByDateRange(String startDate, String endDate)
    {
        return aiAgentScheduleMapper.selectByDateRange(startDate, endDate);
    }

    @Override
    public AiAgentScheduleVO getMyScheduleToday(Long agentId)
    {
        if (agentId == null)
        {
            return null;
        }
        return aiAgentScheduleMapper.selectTodayByAgentId(agentId);
    }

    @Override
    public int insertAiAgentSchedule(AiAgentSchedule aiAgentSchedule)
    {
        fillDefaults(aiAgentSchedule);
        aiAgentSchedule.setCreateTime(DateUtils.getNowDate());
        return aiAgentScheduleMapper.insertAiAgentSchedule(aiAgentSchedule);
    }

    @Override
    public int batchInsert(List<AiAgentSchedule> list)
    {
        if (list == null || list.isEmpty())
        {
            return 0;
        }
        for (AiAgentSchedule item : list)
        {
            fillDefaults(item);
        }
        return aiAgentScheduleMapper.batchInsert(list);
    }

    @Override
    public int updateAiAgentSchedule(AiAgentSchedule aiAgentSchedule)
    {
        aiAgentSchedule.setUpdateTime(DateUtils.getNowDate());
        return aiAgentScheduleMapper.updateAiAgentSchedule(aiAgentSchedule);
    }

    @Override
    public int deleteAiAgentScheduleByScheduleIds(Long[] scheduleIds)
    {
        return aiAgentScheduleMapper.deleteAiAgentScheduleByScheduleIds(scheduleIds);
    }

    @Override
    public int deleteAiAgentScheduleByScheduleId(Long scheduleId)
    {
        return aiAgentScheduleMapper.deleteAiAgentScheduleByScheduleId(scheduleId);
    }

    @Override
    public int checkIn(Long agentId)
    {
        if (agentId == null)
        {
            return 0;
        }
        String today = DateUtils.getDate();
        AiAgentSchedule exist = aiAgentScheduleMapper.selectByAgentAndDate(agentId, today);
        if (exist == null)
        {
            return 0;
        }
        return aiAgentScheduleMapper.checkIn(agentId, today, new Date());
    }

    @Override
    public int checkOut(Long agentId)
    {
        if (agentId == null)
        {
            return 0;
        }
        String today = DateUtils.getDate();
        AiAgentSchedule exist = aiAgentScheduleMapper.selectByAgentAndDate(agentId, today);
        if (exist == null)
        {
            return 0;
        }
        return aiAgentScheduleMapper.checkOut(agentId, today, new Date());
    }

    /**
     * 写入默认值：排班类型默认 1（正常）、状态默认 1（有效）
     */
    private void fillDefaults(AiAgentSchedule schedule)
    {
        if (schedule.getScheduleType() == null)
        {
            schedule.setScheduleType(1);
        }
        if (schedule.getStatus() == null)
        {
            schedule.setStatus(1);
        }
    }
}
