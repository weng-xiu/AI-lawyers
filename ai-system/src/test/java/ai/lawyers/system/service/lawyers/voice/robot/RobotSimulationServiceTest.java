package ai.lawyers.system.service.lawyers.voice.robot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
