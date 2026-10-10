package ai.lawyers.system.service.lawyers.voice.robot;

import java.util.ArrayList;
import java.util.List;

/**
 * P3-E3：语音机器人单轮应答结果。
 *
 * <p>{@code degraded=true} 表示走了兜底话术（开关关闭/模型异常/空答复），
 * {@code fallbackReason} 记录降级原因（empty_question/robot_disabled/llm_error/llm_empty）。</p>
 *
 * @author ai-lawyers
 */
public class VoiceRobotResult
{
    /** 应答文本（已按播报长度截断） */
    private String answer;

    /** RAG 命中数 */
    private int ragHits;

    /** 溯源清单（标题 + 法条） */
    private List<String> sources = new ArrayList<>();

    /** RAG 检索耗时（ms） */
    private long ragMs;

    /** LLM 调用耗时（ms，非流式口径=整段返回） */
    private long llmMs;

    /** 是否降级（兜底话术） */
    private boolean degraded;

    /** 降级原因 */
    private String fallbackReason;

    /** G6：机器人自助动作类型（命中自助查询时为 query_ticket，普通问答为 null） */
    private String action;

    /** G6：本次自助查询命中的工单数 */
    private int ticketCount;

    /** G6：命中工单的工单号清单（供前端展示，不涉敏感信息） */
    private List<String> ticketNos = new ArrayList<>();

    public static VoiceRobotResult ok(String answer)
    {
        VoiceRobotResult r = new VoiceRobotResult();
        r.answer = answer;
        r.degraded = false;
        return r;
    }

    /** G6：自助查询命中结果（口语化答复 + 命中工单摘要） */
    public static VoiceRobotResult ticketQuery(String answer, List<String> ticketNos)
    {
        VoiceRobotResult r = new VoiceRobotResult();
        r.answer = answer;
        r.degraded = false;
        r.action = "query_ticket";
        r.ticketNos = ticketNos == null ? new ArrayList<>() : ticketNos;
        r.ticketCount = r.ticketNos.size();
        return r;
    }

    public static VoiceRobotResult degraded(String fallbackAnswer, String reason)
    {
        VoiceRobotResult r = new VoiceRobotResult();
        r.answer = fallbackAnswer;
        r.degraded = true;
        r.fallbackReason = reason;
        return r;
    }

    public String getAnswer()
    {
        return answer;
    }

    public int getRagHits()
    {
        return ragHits;
    }

    public void setRagHits(int ragHits)
    {
        this.ragHits = ragHits;
    }

    public List<String> getSources()
    {
        return sources;
    }

    public void setSources(List<String> sources)
    {
        this.sources = sources == null ? new ArrayList<>() : sources;
    }

    public long getRagMs()
    {
        return ragMs;
    }

    public void setRagMs(long ragMs)
    {
        this.ragMs = ragMs;
    }

    public long getLlmMs()
    {
        return llmMs;
    }

    public void setLlmMs(long llmMs)
    {
        this.llmMs = llmMs;
    }

    public boolean isDegraded()
    {
        return degraded;
    }

    public String getFallbackReason()
    {
        return fallbackReason;
    }

    public String getAction()
    {
        return action;
    }

    public int getTicketCount()
    {
        return ticketCount;
    }

    public List<String> getTicketNos()
    {
        return ticketNos;
    }
}
