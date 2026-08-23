package ai.lawyers.system.mapper.lawyers.schedule;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.schedule.AiAgentSchedule;
import ai.lawyers.system.domain.lawyers.schedule.AiAgentScheduleVO;

/**
 * 坐席排班 Mapper
 */
public interface AiAgentScheduleMapper
{
    public AiAgentSchedule selectAiAgentScheduleByScheduleId(Long scheduleId);

    /**
     * 列表查询：支持按坐席、班次、日期范围过滤，LEFT JOIN 坐席表与班次表返回展示字段
     */
    public List<AiAgentScheduleVO> selectScheduleList(AiAgentScheduleVO query);

    /**
     * 按坐席与日期定位排班（用于签到/签退）
     */
    public AiAgentSchedule selectByAgentAndDate(@Param("agentId") Long agentId,
                                                @Param("scheduleDate") String scheduleDate);

    /**
     * 按日期范围查询排班（含坐席与班次关联字段）
     */
    public List<AiAgentScheduleVO> selectByDateRange(@Param("startDate") String startDate,
                                                     @Param("endDate") String endDate);

    /**
     * 查询某坐席今日排班（基于数据库当前日期）
     */
    public AiAgentScheduleVO selectTodayByAgentId(@Param("agentId") Long agentId);

    public int insertAiAgentSchedule(AiAgentSchedule aiAgentSchedule);

    /** 批量插入排班 */
    public int batchInsert(List<AiAgentSchedule> list);

    public int updateAiAgentSchedule(AiAgentSchedule aiAgentSchedule);

    /**
     * 签到：写入 check_in_time = now()，并将排班置为有效
     */
    public int checkIn(@Param("agentId") Long agentId,
                       @Param("scheduleDate") String scheduleDate,
                       @Param("checkInTime") Date checkInTime);

    /**
     * 签退：写入 check_out_time = now()
     */
    public int checkOut(@Param("agentId") Long agentId,
                        @Param("scheduleDate") String scheduleDate,
                        @Param("checkOutTime") Date checkOutTime);

    public int deleteAiAgentScheduleByScheduleId(Long scheduleId);

    public int deleteAiAgentScheduleByScheduleIds(Long[] scheduleIds);
}
