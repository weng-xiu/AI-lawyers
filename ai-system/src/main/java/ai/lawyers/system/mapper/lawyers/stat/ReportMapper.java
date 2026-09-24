package ai.lawyers.system.mapper.lawyers.stat;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * 独立多维统计报表 Mapper（P3-D6）
 *
 * <p>全部基于现有业务表实时聚合；P3-F3 ai_stat_minute 物化表落地后仅需切换数据源，
 * 接口与前端契约不变（与 F9/F10 既定路径一致）。</p>
 *
 * @author ai-lawyers
 */
public interface ReportMapper
{
    /** 呼叫报表：按周期聚合呼叫总量/接通/未接/转接/接通率/平均通话时长 */
    List<Map<String, Object>> selectCallReport(@Param("beginTime") String beginTime,
                                               @Param("endTime") String endTime,
                                               @Param("granularity") String granularity);

    /** 坐席服务报表：按坐席聚合接线量/接通率/通话时长 */
    List<Map<String, Object>> selectServiceReport(@Param("beginTime") String beginTime,
                                                  @Param("endTime") String endTime);

    /** 质检报表：按周期聚合质检量/平均分/已复核/待复核/关联风险数 */
    List<Map<String, Object>> selectQualityReport(@Param("beginTime") String beginTime,
                                                  @Param("endTime") String endTime,
                                                  @Param("granularity") String granularity);

    /** 业务工单报表：按周期聚合工单量/办结量/办结率/超时量/平均办结时长 */
    List<Map<String, Object>> selectBusinessReport(@Param("beginTime") String beginTime,
                                                   @Param("endTime") String endTime,
                                                   @Param("granularity") String granularity);
}
