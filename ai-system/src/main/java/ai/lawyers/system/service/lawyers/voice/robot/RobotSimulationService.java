package ai.lawyers.system.service.lawyers.voice.robot;

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
 * P1-8（V2.60）：上线前仿真回归——用合成对话集自动评测语音机器人应答。
 *
 * <p>用例 JSON 契约：
 * <pre>
 * {"cases":[{"caseId":"c1","name":"欠薪咨询","turns":[
 *   {"user":"老板拖欠工资怎么办","expectKeywords":["工资","劳动"],"forbidden":["包赢","承诺胜诉"]}
 * ]}]}
 * </pre>
 * 每轮判定：管线未降级（degraded=false）<b>且</b>期望关键词全部出现 <b>且</b>
 * 违禁词均未出现；逐轮聚合为用例通过/失败，输出通过率报告。
 * 上线前改动提示词/知识/RAG 参数后跑一次即可回归，对齐 Cresta 式仿真评测。</p>
 *
 * @author ai-lawyers
 */
@Service
public class RobotSimulationService
{
    private static final Logger log = LoggerFactory.getLogger(RobotSimulationService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 默认合成用例集（classpath） */
    private static final String DEFAULT_CASES_RESOURCE = "simulation/robot-cases.json";

    @Autowired
    private VoiceRobotService voiceRobotService;

    /**
     * 用请求体用例集执行仿真。
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
            throw new IllegalArgumentException("仿真用例集解析失败：" + e.getMessage(), e);
        }

        ObjectNode report = MAPPER.createObjectNode();
        ArrayNode details = MAPPER.createArrayNode();
        int totalCases = 0, passedCases = 0, totalTurns = 0, passedTurns = 0;

        JsonNode cases = root.path("cases");
        if (cases.isArray())
        {
            for (JsonNode c : cases)
            {
                totalCases++;
                ObjectNode caseNode = details.addObject();
                String caseId = c.path("caseId").asText("case-" + totalCases);
                caseNode.put("caseId", caseId);
                caseNode.put("name", c.path("name").asText(""));
                ArrayNode turnNodes = caseNode.putArray("turns");
                boolean casePassed = true;

                JsonNode turns = c.path("turns");
                if (turns.isArray())
                {
                    for (JsonNode t : turns)
                    {
                        totalTurns++;
                        String user = t.path("user").asText("");
                        VoiceRobotResult result =
                                voiceRobotService.simulateAnswer(user, "sim-" + caseId);
                        String answer = result.getAnswer() == null ? "" : result.getAnswer();

                        List<String> missing = new ArrayList<>();
                        JsonNode expect = t.path("expectKeywords");
                        if (expect.isArray())
                        {
                            for (JsonNode kw : expect)
                            {
                                String word = kw.asText("");
                                if (!word.isEmpty() && !answer.contains(word))
                                {
                                    missing.add(word);
                                }
                            }
                        }
                        List<String> forbiddenHit = new ArrayList<>();
                        JsonNode forbidden = t.path("forbidden");
                        if (forbidden.isArray())
                        {
                            for (JsonNode kw : forbidden)
                            {
                                String word = kw.asText("");
                                if (!word.isEmpty() && answer.contains(word))
                                {
                                    forbiddenHit.add(word);
                                }
                            }
                        }

                        ObjectNode turnNode = turnNodes.addObject();
                        turnNode.put("user", user);
                        turnNode.put("answer", answer);
                        boolean turnPassed = !result.isDegraded()
                                && missing.isEmpty() && forbiddenHit.isEmpty();
                        turnNode.put("passed", turnPassed);
                        if (result.isDegraded())
                        {
                            turnNode.put("degradedReason",
                                    StringUtils.isEmpty(result.getFallbackReason())
                                            ? "degraded" : result.getFallbackReason());
                        }
                        ArrayNode missNode = turnNode.putArray("missing");
                        missing.forEach(missNode::add);
                        ArrayNode forbNode = turnNode.putArray("forbiddenHit");
                        forbiddenHit.forEach(forbNode::add);

                        if (turnPassed)
                        {
                            passedTurns++;
                        }
                        else
                        {
                            casePassed = false;
                        }
                    }
                }
                caseNode.put("passed", casePassed);
                if (casePassed)
                {
                    passedCases++;
                }
            }
        }

        report.put("totalCases", totalCases);
        report.put("passedCases", passedCases);
        report.put("failedCases", totalCases - passedCases);
        report.put("totalTurns", totalTurns);
        report.put("passedTurns", passedTurns);
        report.put("casePassRate",
                totalCases == 0 ? 0d : Math.round(passedCases * 1000d / totalCases) / 10d);
        report.set("details", details);
        log.info("机器人仿真回归完成：用例 {}/{} 通过，轮次 {}/{} 通过",
                passedCases, totalCases, passedTurns, totalTurns);
        return report;
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
}
