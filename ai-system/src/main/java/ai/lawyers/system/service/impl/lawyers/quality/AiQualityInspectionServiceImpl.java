package ai.lawyers.system.service.impl.lawyers.quality;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiRiskWarning;
import ai.lawyers.system.domain.lawyers.AiRiskWarningRule;
import ai.lawyers.system.domain.lawyers.quality.AiQualityInspection;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.quality.AiQualityInspectionMapper;
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;
import ai.lawyers.system.service.lawyers.IAiRiskWarningRuleService;
import ai.lawyers.system.service.lawyers.IAiRiskWarningService;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.quality.IAiQualityInspectionService;
import ai.lawyers.system.service.lawyers.queue.MessageNotifyDispatcher;
import ai.lawyers.system.service.lawyers.voice.VoiceEngineManager;
import ai.lawyers.system.service.lawyers.voice.VoiceModelEnum;

/**
 * 智能质检服务实现（T4-1 / T4-2 联动）。
 *
 * <p>流程：录音就绪 →（采样率抽样）建质检记录 → ASR 转写（复用 Whisper 兼容 AsrEngine，
 * 转写文本同步回写 ai_call_record.transcript）→ chatJson 按四维度评分（服务规范/答复准确/
 * 情绪态度/违禁话术）→ 命中违禁词或情绪激烈自动生成风险预警并互相回填 ID。</p>
 *
 * <p>可用性：ASR/大模型任一失败置 ai_status=3（失败），不抛断主流程；录音文件尚未落盘时
 * 抛异常由 quality-transcribe 队列重投（at-least-once）。</p>
 *
 * @author ai-lawyers
 */
@Service
public class AiQualityInspectionServiceImpl implements IAiQualityInspectionService
{
    private static final Logger log = LoggerFactory.getLogger(AiQualityInspectionServiceImpl.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private AiQualityInspectionMapper inspectionMapper;

    @Autowired
    private AiCallRecordMapper callRecordMapper;

    @Autowired
    private VoiceEngineManager voiceEngineManager;

    @Autowired
    private IAiModelConfigService modelConfigService;

    @Autowired
    private IAiRiskWarningService riskWarningService;

    @Autowired
    private IAiRiskWarningRuleService riskWarningRuleService;

    /** T5-1：质检指标（未引入 micrometer 时内部静默） */
    @Autowired(required = false)
    private HotlineMetrics metrics;

    /** T5-3：质检驳回等事件投递站内信 */
    @Autowired(required = false)
    private MessageNotifyDispatcher messageNotifyDispatcher;

    @Autowired
    private IAiCallAgentStatusService agentStatusService;

    /** 质检总开关 */
    @Value("${ai.quality.enabled:true}")
    private boolean qualityEnabled;

    /** 通话挂断/录音就绪后是否自动入检 */
    @Value("${ai.quality.auto-on-hangup:true}")
    private boolean autoOnHangup;

    /** 采样率：1.0 全量质检；0.05~1 抽检 */
    @Value("${ai.quality.sample-rate:1.0}")
    private double sampleRate;

    /** 录音文件基础路径（对应 FreeSWITCH recordings_dir） */
    @Value("${call.recording.base-path:C:/Program Files/FreeSWITCH/recordings}")
    private String recordingBasePath;

    @Override
    public void submitForRecord(Long recordId)
    {
        if (!qualityEnabled || !autoOnHangup || recordId == null)
        {
            return;
        }
        // 幂等：同一话单已有质检记录则不重复入检
        if (inspectionMapper.selectByRecordId(recordId) != null)
        {
            return;
        }
        // 采样率抽样
        if (sampleRate < 1.0 && Math.random() > sampleRate)
        {
            log.info("质检采样未命中 recordId={} sampleRate={}", recordId, sampleRate);
            return;
        }
        AiCallRecord record = callRecordMapper.selectAiCallRecordByRecordId(recordId);
        if (record == null)
        {
            log.warn("质检入检：话单不存在 recordId={}", recordId);
            return;
        }
        AiQualityInspection ins = new AiQualityInspection();
        ins.setSourceType("1");
        ins.setRecordId(recordId);
        ins.setAgentId(record.getAgentId());
        ins.setCallerNumber(record.getCallerNumber());
        ins.setAiStatus("0");
        ins.setCreateBy("quality-auto");
        inspectionMapper.insertAiQualityInspection(ins);
        log.info("已入质检 recordId={} inspectionId={} agentId={}", recordId, ins.getInspectionId(), record.getAgentId());
    }

    @Override
    public void inspect(Long recordId)
    {
        if (recordId == null)
        {
            return;
        }
        AiQualityInspection ins = inspectionMapper.selectByRecordId(recordId);
        if (ins == null)
        {
            // 队列消息早于质检记录或采样未落库，补建后继续
            submitForRecord(recordId);
            ins = inspectionMapper.selectByRecordId(recordId);
            if (ins == null)
            {
                return;
            }
        }
        // 已完成或检中则幂等跳过（at-least-once 重投/并发消费）
        if ("2".equals(ins.getAiStatus()) || "1".equals(ins.getAiStatus()))
        {
            return;
        }
        // CAS 抢占：0待检/3失败重试 → 1检中，防止重复消费并发评分
        if (inspectionMapper.casAiStatus(ins.getInspectionId(), ins.getAiStatus(), "1") == 0)
        {
            return;
        }
        try
        {
            AiCallRecord record = callRecordMapper.selectAiCallRecordByRecordId(recordId);
            if (record == null)
            {
                throw new IllegalStateException("话单不存在 recordId=" + recordId);
            }

            // 1. 转写：优先复用话单已有转写；否则读录音文件调 ASR
            String transcript = record.getTranscript();
            if (StringUtils.isEmpty(transcript))
            {
                transcript = transcribeRecording(record);
                if (StringUtils.isEmpty(transcript))
                {
                    // ASR 无结果（无录音/引擎未配置）：置失败，不无限重投
                    finishFailed(ins, "录音转写为空（无录音文件或 ASR 未配置）");
                    return;
                }
                // 回写话单转写结果（asr_status=2 已完成）
                AiCallRecord up = new AiCallRecord();
                up.setRecordId(recordId);
                up.setTranscript(transcript);
                up.setAsrStatus(2);
                callRecordMapper.updateRecordingInfo(up);
            }

            // 2. AI 评分
            AiScore score = scoreByAi(transcript, record);

            // 3. 落库评分结果
            AiQualityInspection upd = new AiQualityInspection();
            upd.setInspectionId(ins.getInspectionId());
            upd.setTranscript(transcript);
            upd.setTotalScore(score.totalScore);
            upd.setDimensionJson(score.dimensionJson);
            upd.setViolationJson(score.violationJson);
            upd.setAiStatus("2");
            upd.setAiRemark(score.remark);
            upd.setUpdateBy("quality-ai");
            inspectionMapper.updateAiQualityInspection(upd);

            // 4. T4-2 命中违禁/激烈情绪 → 联动风险预警
            linkRiskWarning(ins, record, transcript, score);

            // T5-1：质检成功计数
            if (metrics != null)
            {
                metrics.incrementQuality("success");
            }
            log.info("AI质检完成 inspectionId={} recordId={} score={}",
                    ins.getInspectionId(), recordId, score.totalScore);
        }
        catch (Exception e)
        {
            log.warn("AI质检失败 inspectionId={} recordId={}: {}", ins.getInspectionId(), recordId, e.getMessage());
            // 录音未落盘等可恢复异常：置回待检(0)并抛出，由队列重投；
            // 此处统一置失败(3)，依赖死信/手动重检，避免无录音时无限重投
            markFail(ins.getInspectionId(), e.getMessage());
        }
    }

    /** 读取录音文件并调 ASR 转写；录音文件不存在时抛异常（队列重投） */
    private String transcribeRecording(AiCallRecord record) throws Exception
    {
        File file = resolveRecordingFile(record);
        if (file == null || !file.exists() || !file.isFile())
        {
            throw new IllegalStateException("录音文件尚未就绪 recordId=" + record.getRecordId());
        }
        byte[] audio = Files.readAllBytes(file.toPath());
        String format = guessFormat(file.getName());
        String text = voiceEngineManager.transcribe(
                voiceEngineManager.defaultEngine(), audio, format, 16000, null);
        return text == null ? "" : text.trim();
    }

    /** 解析录音文件路径（绝对路径直接用，相对路径拼 recordings_dir，兼容 $${recordings_dir} 前缀） */
    private File resolveRecordingFile(AiCallRecord record)
    {
        if (record == null || StringUtils.isEmpty(record.getRecordFile()))
        {
            return null;
        }
        String path = record.getRecordFile().trim();
        File file = new File(path);
        if (file.isAbsolute())
        {
            return file;
        }
        if (path.startsWith("$${recordings_dir}") || path.startsWith("${recordings_dir}"))
        {
            path = path.substring(path.indexOf('}') + 1);
            if (path.startsWith("/") || path.startsWith("\\"))
            {
                path = path.substring(1);
            }
        }
        return new File(new File(recordingBasePath), path);
    }

    private String guessFormat(String fileName)
    {
        String name = fileName == null ? "" : fileName.toLowerCase();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "wav";
    }

    /** 调大模型按四维度评分，解析 JSON；失败抛出由上层置失败 */
    private AiScore scoreByAi(String transcript, AiCallRecord record)
    {
        AiScore score = new AiScore();
        String system = "你是12348公共法律服务热线的质检员。请根据坐席与群众的通话转写文本，从四个维度评分（0-100）："
                + "serviceNorm（服务规范：问候/自报身份/礼貌用语/结束语）、answerAccuracy（答复准确：法律解答正确、无误导）、"
                + "emotionAttitude（情绪态度：耐心、中立、无争执）、compliance（合规话术：无违禁/敏感/承诺胜诉/私自收费等）。"
                + "同时给出 violations 数组，列出命中的违禁/敏感/情绪问题（每项含 keyword 与 reason，无则空数组），"
                + "以及 overall 总体评语。仅返回JSON："
                + "{\"totalScore\":数字,\"dimensions\":{\"serviceNorm\":数字,\"answerAccuracy\":数字,"
                + "\"emotionAttitude\":数字,\"compliance\":数字},\"violations\":[{\"keyword\":\"\",\"reason\":\"\"}],"
                + "\"overall\":\"\"}";
        String user = "通话记录ID：" + record.getRecordId()
                + (StringUtils.isNotEmpty(record.getContent()) ? "；咨询内容：" + record.getContent() : "")
                + "\n通话转写：\n" + transcript;
        String raw = modelConfigService.chatJson(system, user);
        try
        {
            JsonNode node = MAPPER.readTree(cleanJson(raw));
            score.totalScore = BigDecimal.valueOf(node.path("totalScore").asDouble(0));
            ObjectNode dims = MAPPER.createObjectNode();
            JsonNode d = node.path("dimensions");
            dims.put("serviceNorm", d.path("serviceNorm").asDouble(0));
            dims.put("answerAccuracy", d.path("answerAccuracy").asDouble(0));
            dims.put("emotionAttitude", d.path("emotionAttitude").asDouble(0));
            dims.put("compliance", d.path("compliance").asDouble(0));
            score.dimensionJson = MAPPER.writeValueAsString(dims);

            ArrayNode violations = MAPPER.createArrayNode();
            JsonNode v = node.path("violations");
            if (v.isArray())
            {
                for (JsonNode item : v)
                {
                    violations.add(item);
                }
            }
            score.violationJson = MAPPER.writeValueAsString(violations);
            score.remark = node.path("overall").asText("");
            score.violationNodes = v;
        }
        catch (Exception e)
        {
            throw new RuntimeException("解析质检评分结果失败：" + e.getMessage(), e);
        }
        return score;
    }

    /**
     * T4-2 风险联动：AI 命中违禁/情绪问题，或命中启用的风险关键词规则，则生成风险预警，
     * 并把预警ID回填到质检记录（幂等：已有 risk_warning_id 不重复生成）。
     */
    private void linkRiskWarning(AiQualityInspection ins, AiCallRecord record, String transcript, AiScore score)
    {
        try
        {
            if (ins.getRiskWarningId() != null)
            {
                return;
            }
            List<String> hitKeywords = matchRiskRules(transcript);
            boolean aiFlagged = score.violationNodes != null && score.violationNodes.isArray()
                    && score.violationNodes.size() > 0;
            // 合规维度低分（<60）也视为风险
            boolean complianceLow = false;
            JsonNode dims = MAPPER.readTree(score.dimensionJson == null ? "{}" : score.dimensionJson);
            complianceLow = dims.path("compliance").asDouble(100) < 60;

            if (!aiFlagged && !complianceLow && hitKeywords.isEmpty())
            {
                return;
            }

            AiRiskWarning warning = new AiRiskWarning();
            warning.setWarningType(complianceLow || aiFlagged ? "合规风险" : "敏感词");
            // 命中违禁/合规问题定高级，关键词命中定中级
            warning.setWarningLevel(complianceLow || aiFlagged ? "1" : "2");
            warning.setSourceType("通话");
            warning.setSourceId(record.getRecordId());
            warning.setCustomerName(record.getCallerName());
            StringBuilder content = new StringBuilder();
            content.append("智能质检命中风险（质检ID：").append(ins.getInspectionId()).append("）；");
            if (hitKeywords.isEmpty() == false)
            {
                content.append("命中关键词：").append(String.join("、", hitKeywords)).append("；");
            }
            if (aiFlagged)
            {
                content.append("AI判定问题数：").append(score.violationNodes.size()).append("；");
            }
            content.append("评语：").append(score.remark == null ? "" : score.remark);
            warning.setContent(content.toString());
            warning.setTriggerTime(new Date());
            riskWarningService.insertAiRiskWarning(warning);

            if (warning.getWarningId() != null)
            {
                inspectionMapper.updateRiskWarningId(ins.getInspectionId(), warning.getWarningId());
                log.info("质检联动生成风险预警 inspectionId={} warningId={}",
                        ins.getInspectionId(), warning.getWarningId());
            }
        }
        catch (Exception e)
        {
            log.warn("质检联动风险预警失败 inspectionId={}: {}", ins.getInspectionId(), e.getMessage());
        }
    }

    /** 用启用的风险规则关键词匹配转写文本，返回命中的关键词 */
    private List<String> matchRiskRules(String transcript)
    {
        List<String> hits = new ArrayList<>();
        if (StringUtils.isEmpty(transcript))
        {
            return hits;
        }
        try
        {
            List<AiRiskWarningRule> rules = riskWarningRuleService.selectAiRiskWarningRuleList(new AiRiskWarningRule());
            for (AiRiskWarningRule rule : rules)
            {
                if (!"1".equals(rule.getIsEnabled()) || StringUtils.isEmpty(rule.getKeywords()))
                {
                    continue;
                }
                for (String kw : Arrays.asList(rule.getKeywords().split(",")))
                {
                    String k = kw.trim();
                    if (!k.isEmpty() && transcript.contains(k) && !hits.contains(k))
                    {
                        hits.add(k);
                    }
                }
            }
        }
        catch (Exception e)
        {
            log.debug("风险规则匹配失败：{}", e.getMessage());
        }
        return hits;
    }

    private void finishFailed(AiQualityInspection ins, String reason)
    {
        markFail(ins.getInspectionId(), reason);
    }

    private void markFail(Long inspectionId, String reason)
    {
        AiQualityInspection upd = new AiQualityInspection();
        upd.setInspectionId(inspectionId);
        upd.setAiStatus("3");
        upd.setAiRemark(reason != null && reason.length() > 900 ? reason.substring(0, 900) : reason);
        upd.setUpdateBy("quality-ai");
        inspectionMapper.updateAiQualityInspection(upd);
        // T5-1：质检失败计数（ASR/评分异常、转写为空等所有终态失败收口于此）
        if (metrics != null)
        {
            metrics.incrementQuality("fail");
        }
    }

    @Override
    public List<AiQualityInspection> selectInspectionList(AiQualityInspection query)
    {
        return inspectionMapper.selectAiQualityInspectionList(query);
    }

    @Override
    public AiQualityInspection selectInspectionById(Long inspectionId)
    {
        return inspectionMapper.selectAiQualityInspectionByInspectionId(inspectionId);
    }

    @Override
    public int review(AiQualityInspection inspection)
    {
        inspection.setReviewTime(new Date());
        int rows = inspectionMapper.reviewAiQualityInspection(inspection);
        // T5-3：复核驳回时给被检坐席发站内信，通知整改（reviewStatus 2=驳回整改）
        if (rows > 0 && "2".equals(inspection.getReviewStatus()) && messageNotifyDispatcher != null)
        {
            try
            {
                AiQualityInspection full = inspectionMapper
                        .selectAiQualityInspectionByInspectionId(inspection.getInspectionId());
                Long receiver = resolveAgentUserId(full == null ? null : full.getAgentId());
                if (receiver != null)
                {
                    String remark = inspection.getReviewRemark();
                    messageNotifyDispatcher.notify(receiver, "4",
                            "质检结果驳回，请整改",
                            "您有一条通话质检被复核驳回（质检ID " + inspection.getInspectionId()
                                    + (StringUtils.isNotEmpty(remark) ? "）：" + remark : "），请查看并整改。"),
                            "quality", inspection.getInspectionId(),
                            inspection.getReviewerName());
                }
            }
            catch (Exception e)
            {
                log.warn("质检驳回站内信投递失败 inspectionId={}: {}", inspection.getInspectionId(), e.getMessage());
            }
        }
        return rows;
    }

    /** agentId → 绑定 userId（未绑定坐席查不到时返回 null） */
    private Long resolveAgentUserId(Long agentId)
    {
        if (agentId == null)
        {
            return null;
        }
        try
        {
            AiCallAgentStatus agent = agentStatusService.selectAiCallAgentStatusByAgentId(agentId);
            return agent == null ? null : agent.getUserId();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private String cleanJson(String raw)
    {
        if (raw == null)
        {
            return "{}";
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start)
        {
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    /** AI 评分中间结果 */
    private static class AiScore
    {
        BigDecimal totalScore;
        String dimensionJson;
        String violationJson;
        String remark;
        JsonNode violationNodes;
    }
}
