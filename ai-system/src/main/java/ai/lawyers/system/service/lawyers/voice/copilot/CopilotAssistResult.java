package ai.lawyers.system.service.lawyers.voice.copilot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * F4：一次 Copilot 实时辅助结果（案情要素 + 推荐法条 + 相似工单）。
 *
 * <p>要素来自 LLM 结构化抽取；法条来自 RAG 混合检索（含 chunkId 可溯源）；
 * 相似工单来自已办结工单关键词匹配。任一部分降级不影响其余部分。</p>
 *
 * @author ai-lawyers
 */
public class CopilotAssistResult
{
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
}
