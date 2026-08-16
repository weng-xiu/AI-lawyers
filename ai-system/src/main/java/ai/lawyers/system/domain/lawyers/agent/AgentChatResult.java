package ai.lawyers.system.domain.lawyers.agent;

import java.io.Serializable;

/**
 * 智能体对话结果
 *
 * @author ai-lawyers
 */
public class AgentChatResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 智能体回复内容 */
    private String reply;

    /** 是否需要转人工 */
    private boolean handoff;

    /** 转人工原因 */
    private String reason;

    /** 关联咨询分类ID */
    private Long categoryId;

    /** 命中的知识库ID（逗号分隔） */
    private String knowledgeRefs;

    /** 当前轮次序号 */
    private int turnNo;

    public AgentChatResult() {}

    public AgentChatResult(String reply, boolean handoff, String reason) {
        this.reply = reply;
        this.handoff = handoff;
        this.reason = reason;
    }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }

    public boolean isHandoff() { return handoff; }
    public void setHandoff(boolean handoff) { this.handoff = handoff; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getKnowledgeRefs() { return knowledgeRefs; }
    public void setKnowledgeRefs(String knowledgeRefs) { this.knowledgeRefs = knowledgeRefs; }

    public int getTurnNo() { return turnNo; }
    public void setTurnNo(int turnNo) { this.turnNo = turnNo; }
}
