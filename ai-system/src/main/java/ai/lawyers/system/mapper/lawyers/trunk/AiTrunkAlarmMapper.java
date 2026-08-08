package ai.lawyers.system.mapper.lawyers.trunk;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.trunk.AiTrunkAlarm;

/**
 * 线路告警 Mapper
 */
public interface AiTrunkAlarmMapper
{
    public AiTrunkAlarm selectAiTrunkAlarmByAlarmId(Long alarmId);

    public List<AiTrunkAlarm> selectAiTrunkAlarmList(AiTrunkAlarm aiTrunkAlarm);

    /** 查询未处理告警 */
    public List<AiTrunkAlarm> selectActiveAlarms();

    /** 判断同类告警在抑制窗口内是否已存在（告警收敛，避免刷屏） */
    public AiTrunkAlarm selectActiveSameAlarm(@Param("trunkId") Long trunkId,
                                              @Param("alarmType") String alarmType,
                                              @Param("since") Date since);

    public int insertAiTrunkAlarm(AiTrunkAlarm aiTrunkAlarm);

    public int updateAiTrunkAlarm(AiTrunkAlarm aiTrunkAlarm);

    /** 线路恢复时自动关闭该线路下同类未处理告警 */
    public int recoverAlarms(@Param("trunkId") Long trunkId, @Param("alarmType") String alarmType);

    public int deleteAiTrunkAlarmByAlarmIds(Long[] alarmIds);
}
