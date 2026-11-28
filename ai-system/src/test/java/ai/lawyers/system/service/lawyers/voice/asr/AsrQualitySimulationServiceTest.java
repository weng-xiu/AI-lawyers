package ai.lawyers.system.service.lawyers.voice.asr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ai.lawyers.system.service.lawyers.voice.SimulationGateEvaluator;
import ai.lawyers.system.service.lawyers.voice.SimulationGateProperties;

/**
 * {@link AsrQualitySimulationService} 单测：双模式金标准、CER 聚合、skipped 边界、
 * 「越低越好」门禁与 null 守卫。纯 JUnit5 + 桩 AsrEngine，不触网、不依赖真实音频。
 */
class AsrQualitySimulationServiceTest
{
    private AsrQualitySimulationService service;

    @BeforeEach
    void setUp()
    {
        service = new AsrQualitySimulationService();
        ReflectionTestUtils.setField(service, "cerMetric", new AsrCerMetric());
        // asrEngine / gate 组件默认不注入，由各用例按需装配
    }

    /** 注入门禁组件（默认 maxCerRate=0.15） */
    private void attachGate()
    {
        ReflectionTestUtils.setField(service, "gateProperties", new SimulationGateProperties());
        ReflectionTestUtils.setField(service, "gateEvaluator", new SimulationGateEvaluator());
    }

    /** 桩 ASR 引擎：忽略音频内容，固定返回给定文本 */
    private void attachStubEngine(String transcript)
    {
        ReflectionTestUtils.setField(service, "asrEngine",
                (ai.lawyers.system.service.lawyers.voice.AsrEngine)
                        (audio, format, sampleRate, options) -> transcript);
    }

    @Test
    void run_mixedSamples_aggregatesCer()
    {
        // A 完全一致 cer=0；B ref「工资」hyp「工某某」cer=1.0 → 平均 0.5，1 过 1 败
        String json = "{\"samples\":["
                + "{\"sampleId\":\"a\",\"referenceText\":\"老板拖欠工资\",\"hypothesisText\":\"老板拖欠工资\"},"
                + "{\"sampleId\":\"b\",\"referenceText\":\"工资\",\"hypothesisText\":\"工某某\"}]}";
        ObjectNode report = service.run(json);
        assertEquals(2, report.get("totalSamples").asInt());
        assertEquals(1, report.get("passedSamples").asInt());
        assertEquals(1, report.get("failedSamples").asInt());
        assertEquals(0, report.get("skippedSamples").asInt());
        assertEquals(0.5d, report.get("avgCerRate").asDouble(), 0.0001);
    }

    @Test
    void run_audioBranch_transcribesAndScores()
    {
        // 无 hypothesisText、有 audioPath：复用现有 classpath 资源做音频占位，桩引擎返回固定转写
        attachStubEngine("老板拖欠工资");
        String json = "{\"samples\":[{\"sampleId\":\"a\",\"referenceText\":\"老板拖欠工资\","
                + "\"audioPath\":\"simulation/asr-golden-samples.json\",\"format\":\"wav\",\"sampleRate\":16000}]}";
        ObjectNode report = service.run(json);
        assertEquals(1, report.get("totalSamples").asInt());
        assertEquals(1, report.get("passedSamples").asInt());
        assertEquals(0d, report.get("avgCerRate").asDouble(), 0.0001);
        assertEquals("老板拖欠工资", report.get("details").get(0).get("hypothesisText").asText());
    }

    @Test
    void run_audioPathButNoEngine_sampleSkipped()
    {
        // 未注入 AsrEngine：音频分支样本 skipped，不计入 avgCerRate
        String json = "{\"samples\":[{\"sampleId\":\"a\",\"referenceText\":\"工资\","
                + "\"audioPath\":\"simulation/x.wav\"}]}";
        ObjectNode report = service.run(json);
        assertEquals(1, report.get("totalSamples").asInt());
        assertEquals(1, report.get("skippedSamples").asInt());
        assertTrue(report.get("details").get(0).get("skipped").asBoolean());
        assertEquals(0d, report.get("avgCerRate").asDouble(), 0.0001);
    }

    @Test
    void run_noHypothesisAndNoAudioPath_sampleSkipped()
    {
        String json = "{\"samples\":[{\"sampleId\":\"a\",\"referenceText\":\"工资\"}]}";
        ObjectNode report = service.run(json);
        assertEquals(1, report.get("skippedSamples").asInt());
        assertTrue(report.get("details").get(0).get("reason").asText().contains("缺少"));
    }

    @Test
    void run_badJson_rejected()
    {
        assertThrows(IllegalArgumentException.class, () -> service.run("not json"));
    }

    @Test
    void run_emptySamples_zeroReportAndGateSkipped()
    {
        attachGate();
        ObjectNode report = service.run("{\"samples\":[]}");
        assertEquals(0, report.get("totalSamples").asInt());
        assertEquals(0d, report.get("avgCerRate").asDouble(), 0.0001);
        ObjectNode gate = (ObjectNode) report.get("gate");
        assertEquals("skipped", gate.get("items").get(0).get("status").asText());
        assertTrue(gate.get("passed").asBoolean());
    }

    @Test
    void run_withGate_passingCer_gatePassed()
    {
        attachGate();
        // ref 7 字仅末段 1 字替换（资→次）：cer=1/7≈0.143 ≤ 0.15，judged 且 direction=down
        String json = "{\"samples\":[{\"sampleId\":\"a\",\"referenceText\":\"老板拖欠工资了\","
                + "\"hypothesisText\":\"老板拖欠工次了\"}]}";
        ObjectNode gate = (ObjectNode) service.run(json).get("gate");
        ObjectNode item = (ObjectNode) gate.get("items").get(0);
        assertEquals("maxCerRate", item.get("name").asText());
        assertEquals("judged", item.get("status").asText());
        assertEquals("down", item.get("direction").asText());
        assertEquals(0.15d, item.get("thresholdRate").asDouble(), 0.0001);
        assertTrue(item.get("passed").asBoolean());
        assertTrue(gate.get("passed").asBoolean());
    }

    @Test
    void run_withGate_failingCer_gateFailed()
    {
        attachGate();
        String json = "{\"samples\":[{\"sampleId\":\"a\",\"referenceText\":\"工资\","
                + "\"hypothesisText\":\"工某某\"}]}";
        ObjectNode gate = (ObjectNode) service.run(json).get("gate");
        ObjectNode item = (ObjectNode) gate.get("items").get(0);
        assertEquals("judged", item.get("status").asText());
        assertFalse(item.get("passed").asBoolean());
        assertFalse(gate.get("passed").asBoolean());
    }

    @Test
    void run_withoutGate_noGateNode()
    {
        // 不注入门禁组件：无 gate 节点，单样本仍按默认 0.15 判通过
        String json = "{\"samples\":[{\"sampleId\":\"a\",\"referenceText\":\"老板拖欠工资\","
                + "\"hypothesisText\":\"老板拖欠工资\"}]}";
        ObjectNode report = service.run(json);
        assertFalse(report.has("gate"));
        assertEquals(1, report.get("passedSamples").asInt());
    }

    @Test
    void run_defaultGoldenResource_smokeWithinThreshold()
    {
        // 加载 classpath 默认金标准集：纯文本对、不读音频不联网，平均 CER 应在 0.15 内
        ObjectNode report = service.run(null);
        assertEquals(5, report.get("totalSamples").asInt());
        assertEquals(0, report.get("skippedSamples").asInt());
        assertTrue(report.get("avgCerRate").asDouble() <= 0.15d,
                "默认金标准集平均 CER 应不高于 0.15，实际=" + report.get("avgCerRate"));
    }
}
