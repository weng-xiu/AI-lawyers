package ai.lawyers.system.service.lawyers.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * N3 法条引用幻觉拦截器。
 *
 * <p>在 RAG 答复离开服务端前，抽取答复中的"第 X 条"引用，逐条比对本次检索上下文
 * （命中分块的法条字段与正文）：上下文未出现的引用判定为无支撑引用（幻觉风险），
 * 按策略改写为保守表述，避免模型编造具体法条号误导群众。</p>
 *
 * <p>设计约束：</p>
 * <ul>
 *   <li>只做可证实的字符串级核验，不调用模型、不抛异常，可在兜底路径安全调用；</li>
 *   <li>无 RAG 命中（上下文为空）时不逐条拦截（避免误伤纯常识答复），仅在模型仍引用具体法条时追加保守提示；</li>
 *   <li>开关 {@code ai.rag.citation-guard-enabled} 默认开启，可配置关闭回退原行为。</li>
 * </ul>
 *
 * @author ai-lawyers
 */
@Service
public class LegalCitationGuard
{
    /** 抽取"第X条"，X 为中文数字/阿拉伯数字，兼容"之一/之二"后缀（如 第一百零八条之一） */
    private static final Pattern CITATION_PATTERN =
            Pattern.compile("第[一二三四五六七八九十百千零〇0-9]+条(?:之[一二三四五六七八九十])?");

    /** 开关：默认开启（字面量默认值保证脱离容器直接 new 时也开启，@Value 仅作配置覆盖） */
    @Value("${ai.rag.citation-guard-enabled:true}")
    private boolean enabled = true;

    /** 改写模式：disclaimer=追加保守提示（默认，保留原文）；strip=删除无支撑引用 */
    @Value("${ai.rag.citation-guard-mode:disclaimer}")
    private String mode = "disclaimer";

    /** 无检索命中但答复引用具体法条时的保守提示（字面量默认值，@Value 仅作覆盖） */
    @Value("${ai.rag.citation-no-context-hint:（以上涉及的具体法条请以官方公布原文为准，建议查阅原文或咨询人工坐席核实。）}")
    private String noContextHint = "（以上涉及的具体法条请以官方公布原文为准，建议查阅原文或咨询人工坐席核实。）";

    /**
     * 校验并按需改写答复。
     *
     * @param answer 模型原始答复（空值原样返回）
     * @param hits   本次 RAG 命中分块（可为空）
     * @return 校验结果（含改写后文本与被拦截引用清单）
     */
    public CitationCheckResult check(String answer, List<RagChunk> hits)
    {
        if (!enabled || !StringUtils.hasText(answer))
        {
            return CitationCheckResult.pass(answer);
        }
        Matcher matcher = CITATION_PATTERN.matcher(answer);
        Set<String> cited = new LinkedHashSet<>();
        while (matcher.find())
        {
            cited.add(matcher.group());
        }
        if (cited.isEmpty())
        {
            return CitationCheckResult.pass(answer);
        }

        boolean hasContext = hits != null && !hits.isEmpty();
        if (!hasContext)
        {
            // 无检索依据却引用了具体法条：不删原文，追加核实提示
            if (answer.contains(noContextHint))
            {
                return CitationCheckResult.pass(answer);
            }
            return new CitationCheckResult(answer + noContextHint, true, new ArrayList<>(cited));
        }

        String contextText = buildContextText(hits);
        List<String> unsupported = new ArrayList<>();
        for (String citation : cited)
        {
            // 上下文（法条字段+正文）出现过同一条号才视为有支撑
            if (!contextText.contains(citation))
            {
                unsupported.add(citation);
            }
        }
        if (unsupported.isEmpty())
        {
            return CitationCheckResult.pass(answer);
        }
        String rewritten = "strip".equalsIgnoreCase(mode)
                ? stripUnsupported(answer, unsupported)
                : answer + noContextHint;
        return new CitationCheckResult(rewritten, true, unsupported);
    }

    /** 拼接命中分块的法条字段与正文，作为引用核验的依据文本 */
    private String buildContextText(List<RagChunk> hits)
    {
        StringBuilder sb = new StringBuilder();
        for (RagChunk hit : hits)
        {
            if (StringUtils.hasText(hit.getLawArticle()))
            {
                sb.append(hit.getLawArticle()).append('\n');
            }
            if (StringUtils.hasText(hit.getContent()))
            {
                sb.append(hit.getContent()).append('\n');
            }
            if (StringUtils.hasText(hit.getTitle()))
            {
                sb.append(hit.getTitle()).append('\n');
            }
        }
        return sb.toString();
    }

    /** 删除无支撑引用（strip 模式）：把无依据条号替换为"相关法律规定"，避免留下悬空条号 */
    private String stripUnsupported(String answer, List<String> unsupported)
    {
        String out = answer;
        for (String citation : unsupported)
        {
            out = out.replace(citation, "相关法律规定");
        }
        return out;
    }
}
