package ai.lawyers.system.domain.lawyers.agent;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import ai.lawyers.system.service.lawyers.rag.RagSourceRef;

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

    /** N4 隐式标识：非 null 表示本条回复为 AI 生成（值为标识标签，开关关闭时为 null） */
    private String aiLabel;

    /** N3 结构化引用溯源（可点回原文，无命中为空列表） */
    private List<RagSourceRef> sourceRefs = new ArrayList<>();

    /** N3 法条引用幻觉拦截：是否改写过回复 */
    private boolean citationIntercepted;

    /** N3 被拦截的无支撑法条引用（留痕审计） */
    private List<String> unsupportedCitations = new ArrayList<>();

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

    public String getAiLabel() { return aiLabel; }
    public void setAiLabel(String aiLabel) { this.aiLabel = aiLabel; }

    public List<RagSourceRef> getSourceRefs() { return sourceRefs; }
    public void setSourceRefs(List<RagSourceRef> sourceRefs)
    {
        this.sourceRefs = sourceRefs == null ? new ArrayList<>() : sourceRefs;
    }

    public boolean isCitationIntercepted() { return citationIntercepted; }
    public void setCitationIntercepted(boolean citationIntercepted)
    {
        this.citationIntercepted = citationIntercepted;
    }

    public List<String> getUnsupportedCitations() { return unsupportedCitations; }
    public void setUnsupportedCitations(List<String> unsupportedCitations)
    {
        this.unsupportedCitations = unsupportedCitations == null ? new ArrayList<>() : unsupportedCitations;
    }
}
