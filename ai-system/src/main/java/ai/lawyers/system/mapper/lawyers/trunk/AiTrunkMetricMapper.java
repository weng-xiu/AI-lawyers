package ai.lawyers.system.mapper.lawyers.trunk;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.trunk.AiTrunkMetric;

/**
 * 线路质量统计 Mapper
 */
public interface AiTrunkMetricMapper
{
    public List<AiTrunkMetric> selectAiTrunkMetricList(AiTrunkMetric aiTrunkMetric);

    /** 查询某条线路最近 N 个统计点（趋势图） */
    public List<AiTrunkMetric> selectRecentByTrunk(@Param("trunkId") Long trunkId,
                                                   @Param("dimension") String dimension,
                                                   @Param("limit") Integer limit);

    /** 查询时间窗内全部线路的统计点 */
    public List<AiTrunkMetric> selectByTimeRange(@Param("beginTime") Date beginTime,
                                                 @Param("endTime") Date endTime,
                                                 @Param("dimension") String dimension);

    public int insertAiTrunkMetric(AiTrunkMetric aiTrunkMetric);

    /** 幂等写入：存在则更新 */
    public int saveOrUpdate(AiTrunkMetric aiTrunkMetric);

    /** 清理过期统计数据 */
    public int deleteBefore(@Param("beforeTime") Date beforeTime);
}
