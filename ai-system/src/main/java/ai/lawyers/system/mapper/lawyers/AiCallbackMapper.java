package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiCallback;

/**
 * 客户回访Mapper接口
 *
 * @author ai-lawyers
 */
public interface AiCallbackMapper
{
    public AiCallback selectAiCallbackByCallbackId(Long callbackId);

    public List<AiCallback> selectAiCallbackList(AiCallback aiCallback);

    public int insertAiCallback(AiCallback aiCallback);

    public int updateAiCallback(AiCallback aiCallback);

    public int deleteAiCallbackByCallbackId(Long callbackId);

    public int deleteAiCallbackByCallbackIds(Long[] callbackIds);

    /** 回访统计：总数、已完成、待回访、平均满意度 */
    public java.util.Map<String, Object> selectCallbackStats();

    /** 满意度趋势（最近N天每日满意度分布） */
    public java.util.List<java.util.Map<String, Object>> selectSatisfactionTrend(Integer days);
}
