package ai.lawyers.system.service.lawyers.voice.copilot;

import java.util.Date;
import java.util.List;
import java.util.Map;
import ai.lawyers.system.domain.lawyers.AiCopilotFeedback;

/**
 * F4：Copilot 采纳行为埋点与采纳率统计服务。
 *
 * @author ai-lawyers
 */
public interface IAiCopilotFeedbackService
{
    /**
     * 记录一条建议行为（服务端补坐席/班组信息）。
     * 非法类型/行为静默丢弃；任何路径不外抛。
     */
    void record(AiCopilotFeedback feedback);

    /**
     * 区间总体采纳统计（total/adoptCount/modifyCount/ignoreCount/adoptRate）。
     */
    Map<String, Object> adoptionTotal(Date beginTime, Date endTime);

    /**
     * 区间按坐席采纳统计（含 deptId/agentName/adoptRate）。
     */
    List<Map<String, Object>> adoptionByAgent(Date beginTime, Date endTime);
}
