package ai.lawyers.system.service.lawyers.voice.copilot;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ai.lawyers.common.utils.StringUtils;

/**
 * G3 深化（V2.79）：Copilot 坐席辅助批量仿真回归。
 *
 * <p>用合成通话文本集逐 case 调 {@link CopilotAssistService#assist}，对其输出
 * （要素抽取/法条/相似工单/代执行动作）按 checks 规则判分，聚合成回归报告。
 * 提示词/RAG/向量索引/动作白名单改动后跑一次即可回归，对齐 Cresta 式上线前评测。</p>
 *
 * <p>用例 JSON 契约（详见 classpath:simulation/copilot-cases.json）：
 * <pre>
 * {"cases":[{"caseId":"c1","name":"欠薪","text":"通话累计文本","checks":{
 *   "requireElement":true,              // 要素抽取不得降级
 *   "disputeTypeKeywords":["劳动"],      // 纠纷类型须含任一关键词
 *   "claimKeywords":["工资"],           // 诉求聚合文本须含任一关键词
 *   "expectUrgency":"normal|urgent",    // 紧急度须相等
 *   "lawKeywords":["劳动"],             // 法条标题/条号聚合文本须含任一关键词
 *   "requireLawCount":1,                // 法条数下限
 *   "expectActions":["createTicket"],   // 动作白名单须全部出现
 *   "forbidden":["包赢"]                // 全部输出文本均不得出现
 * }}]}
 * </pre>
 *
 * <p>判分：每条非空检查项为一个检查点，全过 case 才通过；报告同时给出
 * 检查点级通过率（checkPassRate）与用例通过率（casePassRate）。
 * assist 本身任何路径不外抛，但用例集解析失败抛 IllegalArgumentException。</p>
 *
 * @author ai-lawyers
 */
@Service
public class CopilotSimulationService
{
    private static final Logger log = LoggerFactory.getLogger(CopilotSimulationService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 默认合成用例集（classpath） */
    private static final String DEFAULT_CASES_RESOURCE = "simulation/copilot-cases.json";

    @Autowired
    private CopilotAssistService copilotAssistService;

    /**
     * 用给定用例集执行仿真。
     *
     * @param casesJson 用例集 JSON；空则加载默认 classpath 用例
     */
    public ObjectNode run(String casesJson)
    {
        JsonNode root;
        try
        {
            root = MAPPER.readTree(loadCases(casesJson));
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Copilot 仿真用例集解析失败：" + e.getMessage(), e);
        }

        ObjectNode report = MAPPER.createObjectNode();
        ArrayNode details = MAPPER.createArrayNode();
        int totalCases = 0, passedCases = 0;
        int totalChecks = 0, passedChecks = 0;

        JsonNode cases = root.path("cases");
        if (cases.isArray())
        {
            for (JsonNode c : cases)
            {
                totalCases++;
                String caseId = c.path("caseId").asText("case-" + totalCases);
                String name = c.path("name").asText("");
                String text = c.path("text").asText("");

                CopilotAssistResult r =
                        copilotAssistService.assist(text, "sim-" + totalCases);

                ObjectNode caseNode = details.addObject();
                caseNode.put("caseId", caseId);
                caseNode.put("name", name);
                ArrayNode checkNodes = caseNode.putArray("checks");

                CheckCounter counter = evaluateCase(c.path("checks"), r, checkNodes);
                totalChecks += counter.total;
                passedChecks += counter.passed;

                boolean casePassed = counter.total > 0 && counter.passed == counter.total;
                caseNode.put("passed", casePassed);
                caseNode.put("passedChecks", counter.passed);
                caseNode.put("totalChecks", counter.total);
                if (casePassed)
                {
                    passedCases++;
                }
            }
        }

        report.put("totalCases", totalCases);
        report.put("passedCases", passedCases);
        report.put("failedCases", totalCases - passedCases);
        report.put("totalChecks", totalChecks);
        report.put("passedChecks", passedChecks);
        report.put("casePassRate", rate(passedCases, totalCases));
        report.put("checkPassRate", rate(passedChecks, totalChecks));
        report.set("details", details);
        log.info("Copilot 仿真回归完成：用例 {}/{} 通过，检查点 {}/{} 通过",
                passedCases, totalCases, passedChecks, totalChecks);
        return report;
    }

    /** 对单用例执行全部检查点判分 */
    private CheckCounter evaluateCase(JsonNode checks, CopilotAssistResult r, ArrayNode out)
    {
        CheckCounter counter = new CheckCounter();

        boolean requireElement = checks.path("requireElement").asBoolean(false);
        if (requireElement)
        {
            record(out, counter, "要素抽取未降级", !r.isElementDegraded());
        }

        // 纠纷类型
        JsonNode typeKws = checks.path("disputeTypeKeywords");
        if (typeKws.isArray() && typeKws.size() > 0)
        {
            boolean hit = matchAny(r.getDisputeType(), typeKws);
            record(out, counter, "纠纷类型命中 " + join(typeKws), hit);
        }

        // 诉求
        JsonNode claimKws = checks.path("claimKeywords");
        if (claimKws.isArray() && claimKws.size() > 0)
        {
            String claims = String.join("；", r.getClaims());
            record(out, counter, "诉求命中 " + join(claimKws), matchAny(claims, claimKws));
        }

        // 紧急度
        JsonNode urgencyNode = checks.path("expectUrgency");
        if (!urgencyNode.isMissingNode() && !urgencyNode.asText("").isEmpty())
        {
            String expect = urgencyNode.asText("");
            record(out, counter, "紧急度=" + expect,
                    expect.equalsIgnoreCase(r.getUrgency()));
        }

        // 法条关键词
        JsonNode lawKws = checks.path("lawKeywords");
        if (lawKws.isArray() && lawKws.size() > 0)
        {
            StringBuilder lawText = new StringBuilder();
            for (CopilotLaw law : r.getLaws())
            {
                lawText.append(law.getTitle()).append(' ').append(law.getLawArticle()).append(' ');
            }
            record(out, counter, "法条命中 " + join(lawKws),
                    matchAny(lawText.toString(), lawKws));
        }

        // 法条数量下限
        int requireLawCount = checks.path("requireLawCount").asInt(0);
        if (requireLawCount > 0)
        {
            record(out, counter, "法条数≥" + requireLawCount,
                    r.getLaws().size() >= requireLawCount);
        }

        // 动作白名单
        JsonNode expectActions = checks.path("expectActions");
        if (expectActions.isArray() && expectActions.size() > 0)
        {
            for (JsonNode act : expectActions)
            {
                String name = act.asText("");
                boolean has = false;
                for (CopilotAssistResult.SuggestedAction a : r.getActions())
                {
                    if (name.equals(a.getAction()))
                    {
                        has = true;
                        break;
                    }
                }
                record(out, counter, "包含动作 " + name, has);
            }
        }

        // 违禁词：扫描全部输出文本
        JsonNode forbidden = checks.path("forbidden");
        if (forbidden.isArray() && forbidden.size() > 0)
        {
            String allText = collectAllText(r);
            List<String> hitWords = new ArrayList<>();
            for (JsonNode kw : forbidden)
            {
                String word = kw.asText("");
                if (!word.isEmpty() && allText.contains(word))
                {
                    hitWords.add(word);
                }
            }
            ObjectNode node = record(out, counter, "无违禁词 " + join(forbidden),
                    hitWords.isEmpty());
            if (!hitWords.isEmpty())
            {
                ArrayNode hitNode = node.putArray("forbiddenHit");
                hitWords.forEach(hitNode::add);
            }
        }
        return counter;
    }

    /** 汇总 Copilot 输出全部文本字段，供违禁词扫描 */
    private String collectAllText(CopilotAssistResult r)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(r.getDisputeType());
        r.getClaims().forEach(c -> sb.append(' ').append(c));
        r.getKeyFacts().forEach(f -> sb.append(' ').append(f));
        for (CopilotLaw law : r.getLaws())
        {
            sb.append(' ').append(law.getTitle()).append(' ').append(law.getLawArticle());
        }
        for (CopilotTicket t : r.getTickets())
        {
            sb.append(' ').append(t.getTitle()).append(' ').append(t.getContentSnippet());
        }
        for (CopilotAssistResult.SuggestedAction a : r.getActions())
        {
            sb.append(' ').append(a.getLabel());
            Object content = a.getPayload() == null ? null : a.getPayload().get("content");
            if (content != null)
            {
                sb.append(' ').append(content);
            }
        }
        return sb.toString();
    }

    /** text 命中关键词数组任一即 true（空文本/空词均不命中） */
    private boolean matchAny(String text, JsonNode keywords)
    {
        if (StringUtils.isEmpty(text))
        {
            return false;
        }
        for (JsonNode kw : keywords)
        {
            String word = kw.asText("");
            if (!word.isEmpty() && text.contains(word))
            {
                return true;
            }
        }
        return false;
    }

    private ObjectNode record(ArrayNode out, CheckCounter counter, String name, boolean passed)
    {
        ObjectNode node = out.addObject();
        node.put("check", name);
        node.put("passed", passed);
        counter.total++;
        if (passed)
        {
            counter.passed++;
        }
        return node;
    }

    private String join(JsonNode array)
    {
        List<String> parts = new ArrayList<>();
        for (JsonNode n : array)
        {
            String s = n.asText("");
            if (!s.isEmpty())
            {
                parts.add(s);
            }
        }
        return String.join("/", parts);
    }

    /** 百分比保留 1 位小数 */
    private double rate(int passed, int total)
    {
        return total == 0 ? 0d : Math.round(passed * 1000d / total) / 10d;
    }

    /** 请求体为空时加载默认用例集 */
    private String loadCases(String casesJson) throws Exception
    {
        if (StringUtils.isNotEmpty(casesJson))
        {
            return casesJson;
        }
        ClassPathResource resource = new ClassPathResource(DEFAULT_CASES_RESOURCE);
        try (InputStream in = resource.getInputStream())
        {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1)
            {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), "UTF-8");
        }
    }

    /** 单用例检查点计数 */
    private static class CheckCounter
    {
        int total;
        int passed;
    }
}
