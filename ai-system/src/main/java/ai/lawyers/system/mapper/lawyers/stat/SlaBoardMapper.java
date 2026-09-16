package ai.lawyers.system.mapper.lawyers.stat;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * F9 工单 SLA 可视化看板聚合 Mapper
 *
 * <p>基于 ai_call_ticket / ai_ticket_transfer / ai_sla_policy 实时聚合，不新增业务表、
 * 不依赖 P3-F3 物化表（三期落地后可平滑切换为读物化表）。</p>
 *
 * @author ai-lawyers
 */
public interface SlaBoardMapper
{
    /**
     * 工单总体达成概况（区间内建单口径）：
     * 总量 / 已办结 / 办结准时 / 办结超时 / 进行中 / 进行中已超期 / 进行中未超期。
     * 临近超时（dueSoonCount）由 selectDueSoon 列表单独体现，不在此计数。
     */
    Map<String, Object> selectOverview(@Param("beginTime") Date beginTime,
                                       @Param("endTime") Date endTime);

    /** 按业务条线分组的达成率（内部工单 external_type 为空归 INTERNAL） */
    List<Map<String, Object>> selectAchieveByBizType(@Param("beginTime") Date beginTime,
                                                     @Param("endTime") Date endTime);

    /** 按处理人分组的准时/超时达成（仅统计已分配且已办结工单） */
    List<Map<String, Object>> selectAchieveByAssignee(@Param("beginTime") Date beginTime,
                                                      @Param("endTime") Date endTime);

    /** 超时工单 TOP N（进行中已超期优先，其次历史已办结超时，按超期/延迟时长降序） */
    List<Map<String, Object>> selectOvertimeTop(@Param("beginTime") Date beginTime,
                                                @Param("endTime") Date endTime,
                                                @Param("limit") int limit);

    /** 当前临近超时工单数（进行中、未超期、剩余时限在阈值分钟内），用于总体卡片计数（不分页） */
    long countDueSoon(@Param("warnMinutes") int warnMinutes);

    /** 当前临近超时（进行中、未超期、剩余时限在阈值分钟内），按剩余时间升序 */
    List<Map<String, Object>> selectDueSoon(@Param("warnMinutes") int warnMinutes,
                                            @Param("limit") int limit);

    /** 跨域流转统计：按条线 + 方向汇总转出/转入量、已办结量与平均办结耗时（分钟） */
    List<Map<String, Object>> selectTransferStat(@Param("beginTime") Date beginTime,
                                                 @Param("endTime") Date endTime);
}
