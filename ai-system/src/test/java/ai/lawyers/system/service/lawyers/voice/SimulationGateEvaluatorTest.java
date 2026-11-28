package ai.lawyers.system.service.lawyers.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * N2（语雀 7.4）：{@link SimulationGateEvaluator} 单测——judged/skipped 工厂、
 * 总判定聚合、小数位与空列表兜底，无 Spring 依赖。
 *
 * @author ai-lawyers
 */
class SimulationGateEvaluatorTest
{
    private final SimulationGateEvaluator evaluator = new SimulationGateEvaluator();

    @Test
    void judged_actualAtOrAboveThreshold_passes()
    {
        ObjectNode gate = evaluator.buildGate(Collections.singletonList(
                evaluator.judged("intentAccuracy", "S3", 0.85d, 0.85d)));
        assertTrue(gate.get("passed").asBoolean());
        ObjectNode item = (ObjectNode) gate.get("items").get(0);
        assertEquals("judged", item.get("status").asText());
        assertEquals(0.85d, item.get("actualRate").asDouble(), 0.0001);
        assertEquals(0.85d, item.get("thresholdRate").asDouble(), 0.0001);
        assertTrue(item.get("passed").asBoolean());
    }

    @Test
    void judged_belowThreshold_failsAndGateFails()
    {
        ObjectNode gate = evaluator.buildGate(Collections.singletonList(
                evaluator.judged("lawHitRate", "S2", 0.60d, 0.70d)));
        assertFalse(gate.get("passed").asBoolean());
        assertFalse(gate.get("items").get(0).get("passed").asBoolean());
    }

    @Test
    void skipped_doesNotParticipateInOverallVerdict()
    {
        ObjectNode gate = evaluator.buildGate(Arrays.asList(
                evaluator.judged("intentAccuracy", "S3", 0.90d, 0.85d),
                evaluator.skipped("ticketUsefulRate", "S2", "无工单检查点")));
        // 唯一 judged 项通过，skipped 不拖累总判定
        assertTrue(gate.get("passed").asBoolean());
        ObjectNode skipped = (ObjectNode) gate.get("items").get(1);
        assertEquals("skipped", skipped.get("status").asText());
        assertTrue(skipped.has("reason"));
        assertFalse(skipped.has("passed"));
    }

    @Test
    void oneFailedJudgedAmongPass_makesGateFail()
    {
        ObjectNode gate = evaluator.buildGate(Arrays.asList(
                evaluator.judged("intentAccuracy", "S3", 0.90d, 0.85d),
                evaluator.judged("lawHitRate", "S2", 0.50d, 0.70d)));
        assertFalse(gate.get("passed").asBoolean());
    }

    @Test
    void emptyOrNullChecks_gatePasses()
    {
        assertTrue(evaluator.buildGate(Collections.emptyList()).get("passed").asBoolean());
        assertTrue(evaluator.buildGate(null).get("passed").asBoolean());
    }

    @Test
    void actualRate_roundedToThreeDecimals()
    {
        ObjectNode gate = evaluator.buildGate(Collections.singletonList(
                evaluator.judged("x", "S2", 0.70099d, 0.70d)));
        assertEquals(0.701d, gate.get("items").get(0).get("actualRate").asDouble(), 0.00001);
    }

    @Test
    void judgedMax_actualAtOrBelowThreshold_passesWithDownDirection()
    {
        // CER「越低越好」：0.12 ≤ 0.15 通过，item 输出 direction=down，旧 up 项无该字段
        ObjectNode gate = evaluator.buildGate(Collections.singletonList(
                evaluator.judgedMax("maxCerRate", "S1", 0.12d, 0.15d)));
        assertTrue(gate.get("passed").asBoolean());
        ObjectNode item = (ObjectNode) gate.get("items").get(0);
        assertEquals("judged", item.get("status").asText());
        assertEquals("down", item.get("direction").asText());
        assertTrue(item.get("passed").asBoolean());
    }

    @Test
    void judgedMax_actualAboveThreshold_failsAndRounded()
    {
        ObjectNode gate = evaluator.buildGate(Collections.singletonList(
                evaluator.judgedMax("maxCerRate", "S1", 0.2006d, 0.15d)));
        assertFalse(gate.get("passed").asBoolean());
        ObjectNode item = (ObjectNode) gate.get("items").get(0);
        assertFalse(item.get("passed").asBoolean());
        assertEquals(0.201d, item.get("actualRate").asDouble(), 0.00001);
    }
}
