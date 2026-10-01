package ai.lawyers.system.service.lawyers.voice.copilot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F4：一次 Copilot 实时辅助结果（案情要素 + 推荐法条 + 相似工单 + 建议动作）。
 *
 * <p>要素来自 LLM 结构化抽取；法条来自 RAG 混合检索（含 chunkId 可溯源）；
 * 相似工单 P1-8 起优先语义向量路。actions 为 P1-8 代执行白名单动作建议
 * （仅 createTicket/queryTicket，payload 是草稿，执行前须人工确认）。</p>
 *
 * @author ai-lawyers
 */
public class CopilotAssistResult
{
    /**
     * P1-8：建议动作（代执行白名单）。action 仅允许 createTicket / queryTicket。
     */
    public static class SuggestedAction
    {
        private final String action;
        private final String label;
        private final Map<String, Object> payload;

        public SuggestedAction(String action, String label, Map<String, Object> payload)
        {
            this.action = action;
            this.label = label;
            this.payload = payload;
        }

        public String getAction() { return action; }
        public String getLabel() { return label; }
        public Map<String, Object> getPayload() { return payload; }
    }

    /** 纠纷类型（如 劳动争议/婚姻家庭/民间借贷） */
    private String disputeType = "";

    /** 核心诉求 */
    private List<String> claims = new ArrayList<>();

    /** 紧急度 normal / urgent */
    private String urgency = "normal";

    /** 关键事实 */
    private List<String> keyFacts = new ArrayList<>();

    /** 推荐法条 */
    private List<CopilotLaw> laws = new ArrayList<>();

    /** 相似工单 */
    private List<CopilotTicket> tickets = new ArrayList<>();

    /** 要素抽取是否降级（模型不可用/返回非法 JSON） */
    private boolean elementDegraded;

    /** P1-8：建议动作（默认空，仅要素抽取成功时生成 createTicket 草稿） */
    private List<SuggestedAction> actions = new ArrayList<>();

    public static CopilotAssistResult empty()
    {
        return new CopilotAssistResult();
    }

    public String getDisputeType() { return disputeType; }
    public void setDisputeType(String disputeType)
    {
        this.disputeType = disputeType == null ? "" : disputeType;
    }

    public List<String> getClaims() { return claims; }
    public void setClaims(List<String> claims)
    {
        this.claims = claims == null ? Collections.<String>emptyList() : claims;
    }

    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency)
    {
        this.urgency = urgency == null ? "normal" : urgency;
    }

    public List<String> getKeyFacts() { return keyFacts; }
    public void setKeyFacts(List<String> keyFacts)
    {
        this.keyFacts = keyFacts == null ? Collections.<String>emptyList() : keyFacts;
    }

    public List<CopilotLaw> getLaws() { return laws; }
    public void setLaws(List<CopilotLaw> laws)
    {
        this.laws = laws == null ? Collections.<CopilotLaw>emptyList() : laws;
    }

    public List<CopilotTicket> getTickets() { return tickets; }
    public void setTickets(List<CopilotTicket> tickets)
    {
        this.tickets = tickets == null ? Collections.<CopilotTicket>emptyList() : tickets;
    }

    public boolean isElementDegraded() { return elementDegraded; }
    public void setElementDegraded(boolean elementDegraded) { this.elementDegraded = elementDegraded; }

    public List<SuggestedAction> getActions() { return actions; }
    public void setActions(List<SuggestedAction> actions)
    {
        this.actions = actions == null ? Collections.<SuggestedAction>emptyList() : actions;
    }
}
