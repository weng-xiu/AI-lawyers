package ai.lawyers.system.service.impl.lawyers.summary;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import ai.lawyers.system.service.lawyers.summary.IAiCallSummaryService;

/**
 * 话后 AI 通话小结服务实现（P3-E3 一阶段）。
 *
 * <p>文本来源优先级：ASR 转写 transcript → 人工登记 content + answer；
 * 调用 {@link IAiModelConfigService#chatJson(String, String)} 取结构化 JSON，
 * 组装为带小标题的固定版式文本回写 ai_call_record.ai_summary。</p>
 *
 * @author ai-lawyers
 */
@Service
public class AiCallSummaryServiceImpl implements IAiCallSummaryService
{
    private static final Logger log = LoggerFactory.getLogger(AiCallSummaryServiceImpl.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 待生成 / 生成中 / 已生成 / 失败 */
    private static final String ST_PENDING = "0";
    private static final String ST_RUNNING = "1";
    private static final String ST_DONE = "2";
    private static final String ST_FAIL = "3";

    /** 送入模型的转写文本上限（字符），防止超长通话超 token */
    private static final int TRANSCRIPT_LIMIT = 6000;
    /** 失败原因落库上限（列长 500） */
    private static final int FAIL_REASON_LIMIT = 500;

    @Autowired
    private AiCallRecordMapper callRecordMapper;

    @Autowired
    private IAiModelConfigService modelConfigService;

    /** T5-1：小结指标（未引入 micrometer 时内部静默） */
    @Autowired(required = false)
    private HotlineMetrics metrics;

    /** 话后小结总开关 */
    @Value("${ai.call-summary.enabled:true}")
    private boolean summaryEnabled;

    @Override
    public AiCallRecord generateSummary(Long recordId, boolean force)
    {
        if (!summaryEnabled || recordId == null)
        {
            return recordId == null ? null : callRecordMapper.selectAiCallRecordByRecordId(recordId);
        }
        AiCallRecord record = callRecordMapper.selectAiCallRecordByRecordId(recordId);
        if (record == null)
        {
            log.warn("AI通话小结：话单不存在 recordId={}", recordId);
            return null;
        }
        String status = record.getAiSummaryStatus() == null ? ST_PENDING : record.getAiSummaryStatus();

        // 幂等：已生成且非强制重生成 → 直接返回
        if (ST_DONE.equals(status) && !force)
        {
            return record;
        }
        // 已有生成任务在跑（手动触发与质检联动并发）→ 不重复抢占
        if (ST_RUNNING.equals(status))
        {
            log.info("AI通话小结已在生成中，跳过 recordId={}", recordId);
            return record;
        }
        // 生成小结所需的通话文本（转写优先，降级人工登记内容）
        String sourceText = resolveSourceText(record);
        if (StringUtils.isEmpty(sourceText))
        {
            return markFail(recordId, "无可用通话文本（ASR 未转写且未登记咨询内容），待转写完成后重试");
        }

        // CAS 抢占：force 重生成时 2→1，否则 0/3→1
        if (callRecordMapper.casAiSummaryStatus(recordId, status, ST_RUNNING) == 0)
        {
            log.info("AI通话小结抢占失败（状态已变更）recordId={}", recordId);
            return callRecordMapper.selectAiCallRecordByRecordId(recordId);
        }

        try
        {
            String summary = summarizeByAi(record, sourceText);
            AiCallRecord upd = new AiCallRecord();
            upd.setRecordId(recordId);
            upd.setAiSummary(summary);
            callRecordMapper.updateAiSummarySuccess(upd);
            if (metrics != null)
            {
                metrics.incrementAi("summary", "success");
            }
            log.info("AI通话小结生成完成 recordId={} length={}", recordId, summary.length());
            return callRecordMapper.selectAiCallRecordByRecordId(recordId);
        }
        catch (Exception e)
        {
            log.warn("AI通话小结生成失败 recordId={}: {}", recordId, e.getMessage());
            if (metrics != null)
            {
                metrics.incrementAi("summary", "fail");
            }
            return markFail(recordId, "模型生成失败：" + e.getMessage());
        }
    }

    /** 组装模型输入文本：优先 ASR 转写；无转写时用人工登记的咨询/解答内容 */
    private String resolveSourceText(AiCallRecord record)
    {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotEmpty(record.getTranscript()))
        {
            String t = record.getTranscript().trim();
            sb.append(t, 0, Math.min(t.length(), TRANSCRIPT_LIMIT));
            return sb.toString();
        }
        if (StringUtils.isNotEmpty(record.getContent()))
        {
            sb.append("群众咨询：").append(record.getContent().trim());
        }
        if (StringUtils.isNotEmpty(record.getAnswer()))
        {
            if (sb.length() > 0)
            {
                sb.append("\n");
            }
            sb.append("坐席解答：").append(record.getAnswer().trim());
        }
        return sb.toString();
    }

    /** 调大模型生成结构化小结 JSON，并组装为固定版式文本 */
    private String summarizeByAi(AiCallRecord record, String sourceText)
    {
        String system = "你是12348公共法律服务热线的资深话务主管，负责撰写通话小结供坐席复盘、生成工单和回访使用。"
                + "请基于通话转写客观提炼，不得编造转写中没有的事实，语言简练、使用法言法语。"
                + "仅返回JSON：{\"caseBrief\":\"案情摘要（群众身份/纠纷类型/核心诉求，100字内）\","
                + "\"issues\":[\"争议焦点或待解决问题\"],"
                + "\"legalOpinion\":\"法律依据与已给出的答复意见（含相关法律名称，200字内）\","
                + "\"todos\":[\"后续待办事项（申请仲裁/补材料/转交部门等，无则空数组）\"],"
                + "\"followUp\":\"回访建议（回访关注点或无需回访的理由，100字内）\"}";
        StringBuilder user = new StringBuilder();
        user.append("通话记录ID：").append(record.getRecordId());
        if (StringUtils.isNotEmpty(record.getCallerName()))
        {
            user.append("；来电人：").append(record.getCallerName());
        }
        if (StringUtils.isNotEmpty(record.getCategoryName()))
        {
            user.append("；咨询分类：").append(record.getCategoryName());
        }
        if (record.getDuration() != null)
        {
            user.append("；通话时长：").append(record.getDuration()).append("秒");
        }
        user.append("\n通话文本：\n").append(sourceText);

        String raw = modelConfigService.chatJson(system, user.toString(),
                ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder.SCENE_SUMMARY);
        JsonNode node;
        try
        {
            node = MAPPER.readTree(cleanJson(raw));
        }
        catch (Exception e)
        {
            throw new RuntimeException("解析小结结果失败：" + e.getMessage(), e);
        }

        StringBuilder out = new StringBuilder();
        out.append("【案情摘要】").append(node.path("caseBrief").asText("")).append("\n");
        List<String> issues = toStringList(node.path("issues"));
        out.append("【争议焦点】");
        out.append(issues.isEmpty() ? "无" : String.join("；", issues));
        out.append("\n");
        out.append("【法律意见】").append(node.path("legalOpinion").asText("")).append("\n");
        List<String> todos = toStringList(node.path("todos"));
        out.append("【待办事项】");
        if (todos.isEmpty())
        {
            out.append("无");
        }
        else
        {
            for (int i = 0; i < todos.size(); i++)
            {
                if (i > 0)
                {
                    out.append("；");
                }
                out.append(i + 1).append(".").append(todos.get(i));
            }
        }
        out.append("\n");
        out.append("【回访建议】").append(node.path("followUp").asText("无"));
        return out.toString();
    }

    private List<String> toStringList(JsonNode array)
    {
        List<String> list = new ArrayList<>();
        if (array != null && array.isArray())
        {
            for (JsonNode item : array)
            {
                String s = item.asText("").trim();
                if (!s.isEmpty())
                {
                    list.add(s);
                }
            }
        }
        return list;
    }

    private AiCallRecord markFail(Long recordId, String reason)
    {
        String r = reason == null ? "生成失败" : reason;
        if (r.length() > FAIL_REASON_LIMIT)
        {
            r = r.substring(0, FAIL_REASON_LIMIT);
        }
        callRecordMapper.updateAiSummaryFail(recordId, r);
        return callRecordMapper.selectAiCallRecordByRecordId(recordId);
    }

    /** 截取模型返回中最外层 { ... }，兼容模型在 JSON 外附加说明文字 */
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
}
