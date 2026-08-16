package ai.lawyers.system.domain.lawyers.agent;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import ai.lawyers.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * AI智能体对话消息对象 ai_agent_message
 *
 * @author ai-lawyers
 */
public class AiAgentMessage extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 消息ID */
    private Long messageId;

    /** 会话ID（对应IVR sessionId） */
    private String sessionId;

    /** 智能体ID */
    private Long agentId;

    /** 所属IVR流程ID */
    private Long flowId;

    /** 所属IVR节点ID */
    private Long nodeId;

    /** 关联通话记录ID */
    private Long recordId;

    /** 主叫号码 */
    private String callerNumber;

    /** 角色（user/assistant） */
    private String role;

    /** 消息内容 */
    private String content;

    /** 是否触发转人工（0否 1是） */
    private String handoff;

    /** 转人工原因 */
    private String reason;

    /** 命中的知识库ID（逗号分隔） */
    private String knowledgeRefs;

    /** 轮次序号 */
    private Integer turnNo;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public Long getMessageId() { return messageId; }

    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getSessionId() { return sessionId; }

    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public Long getAgentId() { return agentId; }

    public void setFlowId(Long flowId) { this.flowId = flowId; }
    public Long getFlowId() { return flowId; }

    public void setNodeId(Long nodeId) { this.nodeId = nodeId; }
    public Long getNodeId() { return nodeId; }

    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Long getRecordId() { return recordId; }

    public void setCallerNumber(String callerNumber) { this.callerNumber = callerNumber; }
    public String getCallerNumber() { return callerNumber; }

    public void setRole(String role) { this.role = role; }
    public String getRole() { return role; }

    public void setContent(String content) { this.content = content; }
    public String getContent() { return content; }

    public void setHandoff(String handoff) { this.handoff = handoff; }
    public String getHandoff() { return handoff; }

    public void setReason(String reason) { this.reason = reason; }
    public String getReason() { return reason; }

    public void setKnowledgeRefs(String knowledgeRefs) { this.knowledgeRefs = knowledgeRefs; }
    public String getKnowledgeRefs() { return knowledgeRefs; }

    public void setTurnNo(Integer turnNo) { this.turnNo = turnNo; }
    public Integer getTurnNo() { return turnNo; }

    @Override
    public Date getCreateTime() { return createTime; }
    @Override
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("messageId", getMessageId())
            .append("sessionId", getSessionId())
            .append("agentId", getAgentId())
            .append("role", getRole())
            .append("content", getContent())
            .append("handoff", getHandoff())
            .append("turnNo", getTurnNo())
            .append("createTime", getCreateTime())
            .toString();
    }
}
