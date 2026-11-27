package ai.lawyers.system.service.lawyers.voice.robot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ai.lawyers.system.service.lawyers.voice.SimulationGateEvaluator;
import ai.lawyers.system.service.lawyers.voice.SimulationGateProperties;
import ai.lawyers.system.service.lawyers.voice.SimulationScorer;

/**
 * P1-8：机器人仿真评测服务单测——期望词命中/缺失、违禁词、降级聚合。
 * 用 VoiceRobotService 子类做桩，无外部依赖。
 *
 * @author ai-lawyers
 */
class RobotSimulationServiceTest
{
    private RobotSimulationService service;

    @BeforeEach
    void setUp()
    {
        service = new RobotSimulationService();
        ReflectionTestUtils.setField(service, "voiceRobotService", stubRobot());
        // P1-8：注入关闭语义的真实评分器（纯关键词，无 embedding 依赖）
        SimulationScorer scorer = new SimulationScorer();
        ReflectionTestUtils.setField(scorer, "semanticEnabled", false);
        ReflectionTestUtils.setField(scorer, "semanticThreshold", 0.75d);
        ReflectionTestUtils.setField(service, "scorer", scorer);
    }

    @Test
    void run_mixedCases_aggregatesCorrectly()
    {
        String cases = "{\"cases\":["
                + "{\"caseId\":\"ok\",\"name\":\"通过用例\",\"turns\":["
                + "{\"user\":\"问题OK\",\"expectKeywords\":[\"工资\",\"劳动\"],\"forbidden\":[\"包赢\"]}]},"
                + "{\"caseId\":\"miss\",\"name\":\"缺词用例\",\"turns\":["
                + "{\"user\":\"问题MISS\",\"expectKeywords\":[\"押金\"]}]},"
                + "{\"caseId\":\"degrade\",\"name\":\"降级用例\",\"turns\":["
                + "{\"user\":\"问题BAD\",\"expectKeywords\":[]}]}]}";
        ObjectNode report = service.run(cases);
        assertEquals(3, report.get("totalCases").asInt());
        assertEquals(1, report.get("passedCases").asInt());
        assertEquals(2, report.get("failedCases").asInt());
        assertEquals(3, report.get("totalTurns").asInt());
        assertEquals(1, report.get("passedTurns").asInt());
        assertEquals(33.3, report.get("casePassRate").asDouble(), 0.1);
    }

    @Test
    void run_forbiddenWord_turnFails()
    {
        String cases = "{\"cases\":[{\"caseId\":\"f\",\"turns\":["
                + "{\"user\":\"问题FORBID\",\"expectKeywords\":[\"工资\"],\"forbidden\":[\"承诺胜诉\"]}]}]}";
        ObjectNode report = service.run(cases);
        assertEquals(0, report.get("passedCases").asInt());
    }

    @Test
    void run_emptyCases_zeroReport()
    {
        ObjectNode report = service.run("{\"cases\":[]}");
        assertEquals(0, report.get("totalCases").asInt());
        assertEquals(0d, report.get("casePassRate").asDouble(), 0.01);
    }

    @Test
    void run_badJson_rejected()
    {
        try
        {
            service.run("not json");
            throw new AssertionError("应抛出 IllegalArgumentException");
        }
        catch (IllegalArgumentException expected)
        {
        }
    }

    /** N2：注入门禁组件（默认意图门槛 0.85），报告含 gate 块并按用例通过率判定 */
    private void attachGate(double intentThreshold)
    {
        SimulationGateProperties props = new SimulationGateProperties();
        props.setIntentAccuracy(intentThreshold);
        ReflectionTestUtils.setField(service, "gateProperties", props);
        ReflectionTestUtils.setField(service, "gateEvaluator", new SimulationGateEvaluator());
    }

    @Test
    void run_withGate_passingRate_gatePassed()
    {
        // 单条全通过用例：用例通过率 1.0 ≥ 0.85
        attachGate(0.85d);
        String cases = "{\"cases\":[{\"caseId\":\"ok\",\"name\":\"通过用例\",\"turns\":["
                + "{\"user\":\"问题OK\",\"expectKeywords\":[\"工资\",\"劳动\"]}]}]}";
        ObjectNode report = service.run(cases);
        ObjectNode gate = (ObjectNode) report.get("gate");
        assertTrue(gate.get("passed").asBoolean());
        ObjectNode item = (ObjectNode) gate.get("items").get(0);
        assertEquals("intentAccuracy", item.get("name").asText());
        assertEquals(1.0d, item.get("actualRate").asDouble(), 0.0001);
        assertEquals(0.85d, item.get("thresholdRate").asDouble(), 0.0001);
    }

    @Test
    void run_withGate_lowRate_gateFailed()
    {
        // 三条仅一条通过（33.3%），低于 0.85 门槛 → gate 不通过
        attachGate(0.85d);
        String cases = "{\"cases\":["
                + "{\"caseId\":\"ok\",\"name\":\"通过用例\",\"turns\":["
                + "{\"user\":\"问题OK\",\"expectKeywords\":[\"工资\",\"劳动\"]}]},"
                + "{\"caseId\":\"miss\",\"name\":\"缺词用例\",\"turns\":["
                + "{\"user\":\"问题MISS\",\"expectKeywords\":[\"押金\"]}]},"
                + "{\"caseId\":\"degrade\",\"name\":\"降级用例\",\"turns\":["
                + "{\"user\":\"问题BAD\",\"expectKeywords\":[]}]}]}";
        ObjectNode gate = (ObjectNode) service.run(cases).get("gate");
        assertFalse(gate.get("passed").asBoolean());
    }

    @Test
    void run_withoutGate_noGateNodeAndZeroBehaviorChange()
    {
        // 不注入 gate 组件（守护纯 new 老用法）：报告无 gate 节点，既有字段不受影响
        String cases = "{\"cases\":[{\"caseId\":\"ok\",\"turns\":["
                + "{\"user\":\"问题OK\",\"expectKeywords\":[\"工资\"]}]}]}";
        ObjectNode report = service.run(cases);
        assertFalse(report.has("gate"));
        assertEquals(1, report.get("passedCases").asInt());
    }

    /** 桩机器人：按输入返回固定应答/降级 */
    private VoiceRobotService stubRobot()
    {
        return new VoiceRobotService()
        {
            @Override
            public VoiceRobotResult simulateAnswer(String question, String sessionId)
            {
                if ("问题BAD".equals(question))
                {
                    return VoiceRobotResult.degraded(VoiceRobotService.FALLBACK_ANSWER, "llm_error");
                }
                if ("问题FORBID".equals(question))
                {
                    return VoiceRobotResult.ok("关于工资的问题，我们承诺胜诉");
                }
                if ("问题MISS".equals(question))
                {
                    return VoiceRobotResult.ok("这是劳动方面的答复");
                }
                return VoiceRobotResult.ok("关于劳动工资拖欠问题的法律答复");
            }
        };
    }
}
