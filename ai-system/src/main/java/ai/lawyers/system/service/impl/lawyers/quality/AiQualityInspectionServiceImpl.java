package ai.lawyers.system.service.impl.lawyers.quality;

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
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiRiskWarning;
import ai.lawyers.system.domain.lawyers.AiRiskWarningRule;
import ai.lawyers.system.domain.lawyers.quality.AiQualityInspection;
import ai.lawyers.system.mapper.lawyers.AiCallAgentStatusMapper;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.quality.AiQualityInspectionMapper;
import ai.lawyers.system.service.lawyers.IAiRiskWarningRuleService;
import ai.lawyers.system.service.lawyers.IAiRiskWarningService;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import ai.lawyers.system.service.lawyers.quality.IAiQualityInspectionService;
import ai.lawyers.system.service.lawyers.queue.MessageNotifyDispatcher;
import ai.lawyers.system.service.lawyers.summary.IAiCallSummaryService;
import ai.lawyers.system.service.impl.lawyers.quality.QualityAudioLoader.LoadedAudio;
import ai.lawyers.system.service.impl.lawyers.quality.QualityScoringEngine.AiScore;

/**
 * 智能质检服务实现（T4-1 / T4-2 联动，编排门面）。
 *
 * <p>流程：录音就绪 →（采样率抽样）建质检记录 → ASR 转写（复用 Whisper 兼容 AsrEngine，
 * 转写文本同步回写 ai_call_record.transcript）→ chatJson 按四维度评分（服务规范/答复准确/
 * 情绪态度/违禁话术）→ 命中违禁词或情绪激烈自动生成风险预警并互相回填 ID。</p>
 *
 * <p>可用性：ASR/大模型任一失败置 ai_status=3（失败），不抛断主流程；录音文件尚未落盘时
 * 抛异常由 quality-transcribe 队列重投（at-least-once）。</p>
 *
 * <p>P5 超大类拆分：本类只保留"质检编排"职责——入检/检中 CAS/结果落库/风险与小结联动/
 * CRUD 与申诉复核；录音加载与转写下沉 {@link QualityAudioLoader}，双引擎评分下沉
 * {@link QualityScoringEngine}。协作类均不反向依赖门面，无循环依赖。</p>
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
    private IAiRiskWarningService riskWarningService;

    @Autowired
    private IAiRiskWarningRuleService riskWarningRuleService;

    /** T5-1：质检指标（未引入 micrometer 时内部静默） */
    @Autowired(required = false)
    private HotlineMetrics metrics;

    /** T5-3：质检驳回等事件投递站内信 */
    @Autowired(required = false)
    private MessageNotifyDispatcher messageNotifyDispatcher;

    /**
     * P10/D2：坐席只读反查改走 Mapper。
     *
     * <p>原 {@code IAiCallAgentStatusService} 会形成
     * 质检 → 坐席状态 → 调度服务 → 事件总线 → 质检转写投递 → 质检 的环，
     * 曾用 {@code @Lazy} 打破；Mapper 无下游服务依赖，环随之消除。</p>
     */
    @Autowired
    private AiCallAgentStatusMapper agentStatusMapper;

    /** P5：录音加载与转写协作类（录音字节/ASR/声学/说话人分离影子） */
    @Autowired
    private QualityAudioLoader audioLoader;

    /** P5：双引擎评分协作类（规则 + LLM 融合/降级） */
    @Autowired
    private QualityScoringEngine scoringEngine;

    /** 质检总开关 */
    @Value("${ai.quality.enabled:true}")
    private boolean qualityEnabled;

    /** 通话挂断/录音就绪后是否自动入检 */
    @Value("${ai.quality.auto-on-hangup:true}")
    private boolean autoOnHangup;

    /** 采样率：1.0 全量质检；0.05~1 抽检 */
    @Value("${ai.quality.sample-rate:1.0}")
    private double sampleRate;

    /** P3-E3：质检完成（转写已就绪）后是否自动联动生成 AI 通话小结 */
    @Value("${ai.call-summary.auto-on-inspect:true}")
    private boolean summaryAutoOnInspect;

    /** P3-E3：话后小结服务（required=false：小结模块/开关缺失不影响质检主链路） */
    @Autowired(required = false)
    private IAiCallSummaryService callSummaryService;

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

            // 1. 转写：优先复用话单已有转写；否则读录音文件调 ASR。
            //    无现成转写时本次已读到录音字节，顺带离线跑声学规则（静默/语速），避免重复读盘。
            String transcript = record.getTranscript();
            AcousticFeatureAnalyzer.AcousticResult acoustic = null;
            if (StringUtils.isEmpty(transcript))
            {
                LoadedAudio loaded = audioLoader.loadRecording(record);
                transcript = audioLoader.transcribeAudio(record, loaded);
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

                // 声学规则：仅对本次加载的 WAV 录音离线执行，失败不阻断质检
                acoustic = audioLoader.analyzeAcoustic(loaded, transcript);
            }

            // 2. 评分：P1-7 起 AI+规则双引擎融合（LLM 不可用时规则引擎降级，仍可出结果），
            //    声学规则命中时对服务规范维度追加扣分并合并违规明细
            AiScore score = scoringEngine.scoreByDualEngine(transcript, record, acoustic);

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

            // 5. P3-E3：质检完成后复用本次转写联动生成话后小结（失败已在服务内落库为状态3，不外抛）
            linkCallSummary(recordId);

            // T5-1：质检成功计数
            if (metrics != null)
            {
                metrics.incrementQuality("success");
            }
            log.info("AI质检完成 inspectionId={} recordId={} score={}",
                    ins.getInspectionId(), recordId, score.totalScore);
        }
        catch (RecordingNotReadyException e)
        {
            // P0-5：录音尚未落盘属可恢复异常——置回待检(0)，由 quality-transcribe 队列重投
            // （Stream at-least-once，重试用尽转死信后由定时补扫兜底），不落失败终态
            log.info("录音未就绪，置回待检等待重投 inspectionId={} recordId={}: {}",
                    ins.getInspectionId(), recordId, e.getMessage());
            inspectionMapper.casAiStatus(ins.getInspectionId(), "1", "0");
        }
        catch (Exception e)
        {
            log.warn("AI质检失败 inspectionId={} recordId={}: {}", ins.getInspectionId(), recordId, e.getMessage());
            // 其余异常统一置失败(3)，依赖死信/手动重检，避免无录音时无限重投
            markFail(ins.getInspectionId(), e.getMessage());
        }
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

    /**
     * P3-E3：质检成功后联动生成话后 AI 小结。此时转写已确认落盘，小结零额外 ASR 成本；
     * 服务内部已吞掉全部异常（落库为状态3），这里再加一层防御与开关判断，绝不影响质检主流程。
     */
    private void linkCallSummary(Long recordId)
    {
        if (!summaryAutoOnInspect || callSummaryService == null)
        {
            return;
        }
        try
        {
            callSummaryService.generateSummary(recordId, false);
        }
        catch (Exception e)
        {
            log.warn("质检联动话后小结失败 recordId={}: {}", recordId, e.getMessage());
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

    @Override
    public int appeal(Long inspectionId, String appealReason, Long currentUserId)
    {
        if (inspectionId == null)
        {
            throw new IllegalArgumentException("质检ID不能为空");
        }
        if (currentUserId == null)
        {
            throw new IllegalArgumentException("无法识别当前坐席身份");
        }
        String reason = appealReason == null ? "" : appealReason.trim();
        if (reason.isEmpty())
        {
            throw new IllegalArgumentException("申诉理由不能为空");
        }
        AiQualityInspection full = inspectionMapper.selectAiQualityInspectionByInspectionId(inspectionId);
        if (full == null || !"2".equals(full.getAiStatus()))
        {
            throw new IllegalArgumentException("仅 AI 质检已完成的记录可发起申诉");
        }
        Long ownerUserId = resolveAgentUserId(full.getAgentId());
        if (ownerUserId == null || !ownerUserId.equals(currentUserId))
        {
            throw new IllegalArgumentException("仅被检坐席本人可发起申诉");
        }
        int rows = inspectionMapper.appeal(inspectionId, reason);
        if (rows == 0)
        {
            throw new IllegalArgumentException("申诉提交失败：已有待班组长复核的申诉，请勿重复提交");
        }
        log.info("质检申诉已提交 inspectionId={} agentId={}", inspectionId, full.getAgentId());
        return rows;
    }

    @Override
    public int appealReview(AiQualityInspection inspection)
    {
        if (inspection == null || inspection.getInspectionId() == null)
        {
            throw new IllegalArgumentException("质检ID不能为空");
        }
        String result = inspection.getAppealStatus();
        if (!"2".equals(result) && !"3".equals(result))
        {
            throw new IllegalArgumentException("申诉复核结果只能为 2维持 或 3改分");
        }
        if ("3".equals(result))
        {
            if (inspection.getAdjustedScore() == null)
            {
                throw new IllegalArgumentException("申诉成立改分时必须填写调整分数");
            }
            double s = inspection.getAdjustedScore().doubleValue();
            if (s < 0d || s > 100d)
            {
                throw new IllegalArgumentException("调整分数须在 0-100 之间");
            }
        }
        else
        {
            // 维持原判不写改分值，防止前端夹带
            inspection.setAdjustedScore(null);
        }
        inspection.setUpdateBy(inspection.getReviewerName());
        int rows = inspectionMapper.appealReview(inspection);
        if (rows == 0)
        {
            throw new IllegalArgumentException("申诉复核失败：该申诉不在待复核状态（可能已被处理）");
        }

        // 站内信通知坐席复核结果
        if (messageNotifyDispatcher != null)
        {
            try
            {
                AiQualityInspection full = inspectionMapper
                        .selectAiQualityInspectionByInspectionId(inspection.getInspectionId());
                Long receiver = resolveAgentUserId(full == null ? null : full.getAgentId());
                if (receiver != null)
                {
                    boolean changed = "3".equals(result);
                    messageNotifyDispatcher.notify(receiver, "4",
                            changed ? "质检申诉成立，已改分" : "质检申诉复核完成，维持原判",
                            "您的质检申诉（质检ID " + inspection.getInspectionId() + "）复核结果："
                                    + (changed ? "申诉成立，调整后分数 " + inspection.getAdjustedScore()
                                               : "维持原判") + "。",
                            "quality", inspection.getInspectionId(),
                            inspection.getReviewerName());
                }
            }
            catch (Exception e)
            {
                log.warn("申诉复核站内信投递失败 inspectionId={}: {}", inspection.getInspectionId(), e.getMessage());
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
            AiCallAgentStatus agent = agentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
            return agent == null ? null : agent.getUserId();
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
