package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.AiHotspotSuppress;
import ai.lawyers.system.domain.lawyers.AiHotspotSuppressLog;

/**
 * 高频置底规则 Mapper
 *
 * @author ai-lawyers
 */
public interface AiHotspotSuppressMapper
{
    /** 规则列表（模糊查询） */
    List<AiHotspotSuppress> selectSuppressList(AiHotspotSuppress query);

    /** 按ID查规则 */
    AiHotspotSuppress selectSuppressById(Long suppressId);

    /** 新增规则 */
    int insertSuppress(AiHotspotSuppress suppress);

    /** 修改规则 */
    int updateSuppress(AiHotspotSuppress suppress);

    /** 删除规则 */
    int deleteSuppressByIds(Long[] suppressIds);

    /** 累计命中次数 +1 */
    int incrementHitCount(Long suppressId);

    /**
     * 命中处置日志列表。
     */
    List<AiHotspotSuppressLog> selectLogList(AiHotspotSuppressLog query);

    /** 新增命中日志 */
    int insertLog(AiHotspotSuppressLog log);

    /**
     * 统计某规则在指定时间窗内（now-windowSeconds ~ now）的命中次数，
     * 供频次阈值判定。
     */
    int countRecentHits(@Param("suppressId") Long suppressId,
                       @Param("windowStart") java.util.Date windowStart);
}
