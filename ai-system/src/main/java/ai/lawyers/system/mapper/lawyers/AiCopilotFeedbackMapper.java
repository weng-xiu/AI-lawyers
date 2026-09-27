package ai.lawyers.system.mapper.lawyers;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.AiCopilotFeedback;

/**
 * F4 Copilot 采纳行为埋点 数据层。
 *
 * @author ai-lawyers
 */
public interface AiCopilotFeedbackMapper
{
    /** 写入一条行为埋点 */
    public int insertFeedback(AiCopilotFeedback feedback);

    /**
     * 区间总体统计：总数/采纳数/修改数/忽略数。
     *
     * @param beginTime 起始（含）
     * @param endTime   截止（不含）
     * @return 统计行（无数据时各计数为 null，服务层归一为 0）
     */
    public Map<String, Object> selectAdoptionTotal(@Param("beginTime") Date beginTime,
                                                   @Param("endTime") Date endTime);

    /**
     * 区间按坐席统计（含班组ID）。
     */
    public List<Map<String, Object>> selectAdoptionByAgent(@Param("beginTime") Date beginTime,
                                                           @Param("endTime") Date endTime);
}
