package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiCallback;

/**
 * 客户回访Service接口
 *
 * @author ai-lawyers
 */
public interface IAiCallbackService
{
    public AiCallback selectAiCallbackByCallbackId(Long callbackId);

    public List<AiCallback> selectAiCallbackList(AiCallback aiCallback);

    public int insertAiCallback(AiCallback aiCallback);

    public int updateAiCallback(AiCallback aiCallback);

    public int deleteAiCallbackByCallbackId(Long callbackId);

    public int deleteAiCallbackByCallbackIds(Long[] callbackIds);

    /** 回访统计 */
    public java.util.Map<String, Object> selectCallbackStats();

    /** 满意度趋势 */
    public java.util.List<java.util.Map<String, Object>> selectSatisfactionTrend(Integer days);
}
