package ai.lawyers.system.service.lawyers.voice.asr;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;
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
import ai.lawyers.system.service.lawyers.voice.AsrEngine;
import ai.lawyers.system.service.lawyers.voice.SimulationGateEvaluator;
import ai.lawyers.system.service.lawyers.voice.SimulationGateProperties;

/**
 * N2（语雀 7.4 S1）：ASR 转写质量仿真评测——基于金标准集计算平均字错率（CER）。
 *
 * <p>金标准 JSON 契约（classpath:simulation/asr-golden-samples.json）：
 * <pre>
 * {"samples":[{"sampleId":"s1","name":"欠薪转写","referenceText":"人工金标准",
 *   "hypothesisText":"识别文本（可选，缺省时走音频转写）",
 *   "audioPath":"simulation/audio/s1.wav（可选）","format":"wav","sampleRate":16000}]}
 * </pre>
 * 双模式：条目带 hypothesisText 时纯离线算 CER（默认集/CI 走这条，零音频零网络、确定性）；
 * 缺失且有 audioPath 时读 classpath 音频调 {@link AsrEngine#transcribe} 再评测；两者皆空或
 * 引擎未装配/转写失败则该条 skipped，不影响其余样本。</p>
 *
 * <p>报告给出逐条 CER 与聚合 avgCerRate，门禁按「越低越好」对照 maxCerRate 判定；
 * gate 组件未注入（如纯 new 单测）时不产出 gate 块，单样本仍按默认 0.15 判通过。</p>
 *
 * @author ai-lawyers
 */
@Service
public class AsrQualitySimulationService
{
    private static final Logger log = LoggerFactory.getLogger(AsrQualitySimulationService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 默认金标准集（classpath） */
    private static final String DEFAULT_CASES_RESOURCE = "simulation/asr-golden-samples.json";

    /** 未注入门禁配置时的单样本 CER 通过上限（与 SimulationGateProperties 默认值一致） */
    private static final double DEFAULT_MAX_CER_RATE = 0.15d;

    @Autowired
    private AsrCerMetric cerMetric;

    /** 音频驱动分支才需要；纯离线金标准与纯 new 单测下可为 null */
    @Autowired(required = false)
    private AsrEngine asrEngine;

    /** N2：灰度准入门禁阈值，null 时（如纯 new 单测）不产出 gate 块 */
    @Autowired(required = false)
    private SimulationGateProperties gateProperties;

    /** N2：门禁判定与报告拼装，与 gateProperties 同为可空 */
    @Autowired(required = false)
    private SimulationGateEvaluator gateEvaluator;

    /**
     * 用给定金标准集执行 ASR 质量评测。
     *
     * @param samplesJson 金标准 JSON；空则加载默认 classpath 用例
     */
    public ObjectNode run(String samplesJson)
    {
        JsonNode root;
        try
        {
            root = MAPPER.readTree(loadCases(samplesJson));
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("ASR 金标准用例集解析失败：" + e.getMessage(), e);
        }

        ObjectNode report = MAPPER.createObjectNode();
        ArrayNode details = MAPPER.createArrayNode();
        int totalSamples = 0, passedSamples = 0, failedSamples = 0, skippedSamples = 0;
        int evaluatedCount = 0;
        double cerSum = 0d;
        double maxCerRate = gateProperties == null
                ? DEFAULT_MAX_CER_RATE : gateProperties.getMaxCerRate();

        JsonNode samples = root.path("samples");
        if (samples.isArray())
        {
            for (JsonNode s : samples)
            {
                totalSamples++;
                String sampleId = s.path("sampleId").asText("sample-" + totalSamples);
                String reference = s.path("referenceText").asText("");
                String hypothesis = s.path("hypothesisText").asText("");
                String audioPath = s.path("audioPath").asText("");

                ObjectNode detail = details.addObject();
                detail.put("sampleId", sampleId);
                detail.put("name", s.path("name").asText(""));

                // ① 离线文本对优先；缺失时回退音频转写
                if (StringUtils.isEmpty(hypothesis) && StringUtils.isNotEmpty(audioPath))
                {
                    if (asrEngine == null)
                    {
                        markSkipped(detail, sampleId, "未配置 hypothesisText 且 ASR 引擎未装配");
                        skippedSamples++;
                        continue;
                    }
                    try
                    {
                        byte[] audio = readResourceBytes(audioPath);
                        String format = s.path("format").asText("wav");
                        int sampleRate = s.path("sampleRate").asInt(16000);
                        hypothesis = asrEngine.transcribe(audio, format, sampleRate, null);
                    }
                    catch (Exception e)
                    {
                        markSkipped(detail, sampleId, "音频读取或转写失败：" + e.getMessage());
                        skippedSamples++;
                        continue;
                    }
                }
                else if (StringUtils.isEmpty(hypothesis))
                {
                    markSkipped(detail, sampleId, "样本缺少 hypothesisText 与 audioPath");
                    skippedSamples++;
                    continue;
                }

                // ② 评测 CER
                AsrCerMetric.AsrCerResult result = cerMetric.score(reference, hypothesis);
                boolean passed = result.getCer() <= maxCerRate;
                detail.put("referenceText", reference);
                detail.put("hypothesisText", hypothesis == null ? "" : hypothesis);
                detail.put("editDistance", result.getEditDistance());
                detail.put("refLength", result.getRefLength());
                detail.put("cer", result.getCer());
                detail.put("passed", passed);
                detail.put("skipped", false);

                evaluatedCount++;
                cerSum += result.getCer();
                if (passed)
                {
                    passedSamples++;
                }
                else
                {
                    failedSamples++;
                }
            }
        }

        double avgCerRate = evaluatedCount == 0 ? 0d : round3(cerSum / evaluatedCount);
        report.put("totalSamples", totalSamples);
        report.put("passedSamples", passedSamples);
        report.put("failedSamples", failedSamples);
        report.put("skippedSamples", skippedSamples);
        report.put("samplePassRate", percent(passedSamples, totalSamples));
        report.put("avgCerRate", avgCerRate);
        report.set("details", details);

        // N2（语雀 7.4 S1）：平均字错率「越低越好」门禁；无有效样本或未注入组件时跳过
        if (gateProperties != null && gateEvaluator != null)
        {
            SimulationGateEvaluator.GateCheck cerGate = evaluatedCount == 0
                    ? gateEvaluator.skipped("maxCerRate", "S1", "金标准集无有效评测样本")
                    : gateEvaluator.judgedMax("maxCerRate", "S1",
                            avgCerRate, gateProperties.getMaxCerRate());
            report.set("gate", gateEvaluator.buildGate(Collections.singletonList(cerGate)));
        }
        log.info("ASR 质量评测完成：样本 {} 条，通过 {}，跳过 {}，平均 CER={}",
                totalSamples, passedSamples, skippedSamples, avgCerRate);
        return report;
    }

    /** skipped 样本统一写节点字段 */
    private void markSkipped(ObjectNode detail, String sampleId, String reason)
    {
        detail.put("sampleId", sampleId);
        detail.put("skipped", true);
        detail.put("passed", false);
        detail.put("reason", reason);
    }

    /** 请求体为空时加载默认金标准集 */
    private String loadCases(String samplesJson) throws Exception
    {
        if (StringUtils.isNotEmpty(samplesJson))
        {
            return samplesJson;
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

    /** 读取 classpath 音频为字节数组 */
    private byte[] readResourceBytes(String path) throws Exception
    {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream in = resource.getInputStream())
        {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1)
            {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }

    /** 百分比保留 1 位小数 */
    private double percent(int passed, int total)
    {
        return total == 0 ? 0d : Math.round(passed * 1000d / total) / 10d;
    }

    /** 比率保留 3 位小数 */
    private static double round3(double v)
    {
        return Math.round(v * 1000d) / 1000d;
    }
}
