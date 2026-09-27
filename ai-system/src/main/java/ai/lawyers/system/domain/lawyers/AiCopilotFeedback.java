package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * F4：坐席 Copilot 建议采纳行为埋点 ai_copilot_feedback。
 *
 * <p>记录坐席对 Copilot 各类建议（案情要素/法条/相似工单）的
 * 采纳 ADOPT / 修改后采用 MODIFY / 忽略 IGNORE 行为，按坐席/班组
 * 统计建议采纳率（F10 大屏）。仅埋点，不驱动任何业务闭环。</p>
 *
 * @author ai-lawyers
 */
public class AiCopilotFeedback extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 建议类型：要素 ELEMENT / 法条 LAW / 工单 TICKET */
    public static final String TYPE_ELEMENT = "ELEMENT";
    public static final String TYPE_LAW = "LAW";
    public static final String TYPE_TICKET = "TICKET";

    /** 行为：采纳 / 修改后采用 / 忽略 */
    public static final String ACTION_ADOPT = "ADOPT";
    public static final String ACTION_MODIFY = "MODIFY";
    public static final String ACTION_IGNORE = "IGNORE";

    private Long feedbackId;

    /** 通话记录ID（可空，非语音场景埋点） */
    private Long recordId;

    /** 语音会话ID（原始 sessionId 兜底） */
    private String sessionId;

    private Long agentId;

    private String agentName;

    private Long deptId;

    /** 建议类型 ELEMENT/LAW/TICKET */
    private String suggestionType;

    /** 建议对象引用（chunkId/ticketId/element，可空） */
    private String suggestionRef;

    /** 行为 ADOPT/MODIFY/IGNORE */
    private String action;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public Long getFeedbackId() { return feedbackId; }
    public void setFeedbackId(Long feedbackId) { this.feedbackId = feedbackId; }

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }

    public String getSuggestionType() { return suggestionType; }
    public void setSuggestionType(String suggestionType) { this.suggestionType = suggestionType; }

    public String getSuggestionRef() { return suggestionRef; }
    public void setSuggestionRef(String suggestionRef) { this.suggestionRef = suggestionRef; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    @Override
    public Date getCreateTime() { return createTime; }
    @Override
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
