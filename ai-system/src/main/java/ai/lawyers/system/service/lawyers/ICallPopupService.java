package ai.lawyers.system.service.lawyers;

import java.util.List;
import java.util.Map;
import ai.lawyers.system.domain.lawyers.AiCallerProfile;

/**
 * 来电弹屏聚合服务
 */
public interface ICallPopupService
{
    /** 来电人档案 + 智能分析（合并实时通话统计） */
    public Map<String, Object> getPopupProfile(String callerNumber);

    /** 历史通话 */
    public List<?> getPopupHistory(String callerNumber, Integer limit);

    /** 历史工单 */
    public List<?> getPopupTickets(String callerNumber);

    /** 来电轨迹（通话+工单时间线） */
    public List<Map<String, Object>> getPopupTrack(String callerNumber);

    /** 维护来电人档案 */
    public AiCallerProfile selectAiCallerProfileByCallerNumber(String callerNumber);

    public int updateAiCallerProfile(AiCallerProfile aiCallerProfile);
}
