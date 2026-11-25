package ai.lawyers.system.service.lawyers.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * N3 法条引用校验结果。
 *
 * <p>{@code intercepted=true} 表示模型答复中存在检索上下文无法支撑的法条引用（幻觉风险），
 * 已按策略改写为保守表述；{@code unsupportedCitations} 留痕被拦截的引用原文，供审计与评测统计。</p>
 *
 * @author ai-lawyers
 */
public class CitationCheckResult
{
    /** 校验/改写后的答复文本（无幻觉时与原文一致） */
    private final String answer;

    /** 是否发生拦截改写 */
    private final boolean intercepted;

    /** 被判定为无上下文支撑的法条引用（如 "第一百零八条"） */
    private final List<String> unsupportedCitations;

    public CitationCheckResult(String answer, boolean intercepted, List<String> unsupportedCitations)
    {
        this.answer = answer;
        this.intercepted = intercepted;
        this.unsupportedCitations = unsupportedCitations == null ? new ArrayList<>() : unsupportedCitations;
    }

    public static CitationCheckResult pass(String answer)
    {
        return new CitationCheckResult(answer, false, new ArrayList<>());
    }

    public String getAnswer()
    {
        return answer;
    }

    public boolean isIntercepted()
    {
        return intercepted;
    }

    public List<String> getUnsupportedCitations()
    {
        return unsupportedCitations;
    }
}
