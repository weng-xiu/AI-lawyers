package ai.lawyers.system.service.lawyers.schedule;

import java.util.List;
import ai.lawyers.system.domain.lawyers.schedule.AiAgentSchedule;
import ai.lawyers.system.domain.lawyers.schedule.AiAgentScheduleVO;

/**
 * 坐席排班 Service
 */
public interface IAiAgentScheduleService
{
    public AiAgentSchedule selectAiAgentScheduleByScheduleId(Long scheduleId);

    public List<AiAgentScheduleVO> selectScheduleList(AiAgentScheduleVO query);

    /**
     * 按日期范围查询排班
     */
    public List<AiAgentScheduleVO> selectByDateRange(String startDate, String endDate);

    /**
     * 查询坐席今日排班
     */
    public AiAgentScheduleVO getMyScheduleToday(Long agentId);

    public int insertAiAgentSchedule(AiAgentSchedule aiAgentSchedule);

    /**
     * 批量排班（一个班次可给多个坐席同一天排班）
     */
    public int batchInsert(List<AiAgentSchedule> list);

    public int updateAiAgentSchedule(AiAgentSchedule aiAgentSchedule);

    public int deleteAiAgentScheduleByScheduleIds(Long[] scheduleIds);

    public int deleteAiAgentScheduleByScheduleId(Long scheduleId);

    /**
     * 签到：更新 checkInTime = now()，status=1
     */
    public int checkIn(Long agentId);

    /**
     * 签退：更新 checkOutTime = now()
     */
    public int checkOut(Long agentId);
}
