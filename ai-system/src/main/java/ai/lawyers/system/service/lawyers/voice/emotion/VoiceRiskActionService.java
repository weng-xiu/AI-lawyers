package ai.lawyers.system.service.lawyers.voice.emotion;

import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.AiRiskWarning;
import ai.lawyers.system.domain.lawyers.skill.AiCallQueue;
import ai.lawyers.system.mapper.lawyers.skill.AiCallQueueMapper;
import ai.lawyers.system.service.lawyers.IAiRiskWarningService;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;

/**
 * 情绪/意图联动动作服务（P3-E4）。
 *
 * <p>实时识别命中后执行三类联动，<b>best-effort，任何路径不外抛</b>
 * （语音链路可用性优先）：</p>
 * <ol>
 *   <li><b>提前触发风险预警</b>：落 ai_risk_warning（类型=情绪异常；urgent 高级 /
 *       negative 中级；来源=通话），insert 逻辑自带站内信广播 → 班长介入提醒复用；</li>
 *   <li><b>队列优先级提升（SLA 联动）</b>：排队中（queue_status=0）记录 priority
 *       由 0 提升为 urgent/negative 档，ACD 按 priority desc 取队 → 更短排队、
 *       更紧应答时效保障；仅升不降，已出队记录不动；</li>
 *   <li><b>指标计数</b>：hotline_voice_emotion_total{level}。</li>
 * </ol>
 *
 * <p>不执行自动转人工/自动转条线等重副作用动作——实时规则可能误报，只通知、
 * 只提优，处置由班长/坐席发起（风险预警页一键转办已支持）。</p>
 *
 * @author ai-lawyers
 */
@Service
public class VoiceRiskActionService
{
    private static final Logger log = LoggerFactory.getLogger(VoiceRiskActionService.class);

    /** 触发内容/识别片段落库长度上限（避免 ASR 长文本撑爆预警 content） */
    private static final int HIT_TEXT_MAX = 200;

    private static final int CONTENT_MAX = 500;

    @Value("${ai.emotion-detector.enabled:true}")
    private boolean enabled;

    /** urgent 排队优先级（正常来电 priority=0，须显著高于普通来电） */
    @Value("${ai.emotion-detector.urgent-priority:100}")
    private int urgentPriority;

    /** negative 排队优先级（高于普通、低于 urgent） */
    @Value("${ai.emotion-detector.negative-priority:50}")
    private int negativePriority;

    @Autowired(required = false)
    private IAiRiskWarningService riskWarningService;

    @Autowired(required = false)
    private AiCallQueueMapper queueMapper;

    @Autowired(required = false)
    private HotlineMetrics metrics;

    /**
     * 处理一次情绪命中。
     *
     * @param level    情绪级别（urgent/negative，见 {@link EmotionIntentResult}）
     * @param intent   业务意图（可空）
     * @param hitText  命中时的转写文本（识别片段，可空）
     * @param keywords 命中的情绪关键词（可空）
     * @param sessionId 语音会话ID（=话单 recordId 的字符串形式时关联话单）
     * @return 生成的预警ID；开关关闭/未装配/失败时返回 null
     */
    public Long handleEmotion(String level, String intent, String hitText,
            List<String> keywords, String sessionId)
    {
        if (!enabled)
        {
            return null;
        }
        boolean urgent = EmotionIntentResult.EMOTION_URGENT.equals(level);
        boolean negative = !urgent && EmotionIntentResult.EMOTION_NEGATIVE.equals(level);
        if (!urgent && !negative)
        {
            return null;
        }
        Long recordId = parseRecordId(sessionId);

        Long warningId = insertWarning(urgent, intent, hitText, keywords, recordId);
        boostQueuePriority(urgent, recordId, sessionId);
        if (metrics != null)
        {
            try
            {
                metrics.incrementVoiceEmotion(level);
            }
            catch (Exception e)
            {
                log.debug("情绪命中指标计数失败：{}", e.getMessage());
            }
        }
        return warningId;
    }

    /** 落情绪异常预警；返回预警ID（失败 null）。站内信广播由 insert 逻辑完成。 */
    private Long insertWarning(boolean urgent, String intent, String hitText,
            List<String> keywords, Long recordId)
    {
        if (riskWarningService == null)
        {
            log.warn("情绪命中但风险预警服务未装配，跳过预警 recordId={}", recordId);
            return null;
        }
        try
        {
            AiRiskWarning warning = new AiRiskWarning();
            warning.setWarningType("情绪异常");
            warning.setWarningLevel(urgent ? "1" : "2");
            warning.setSourceType("通话");
            warning.setSourceId(recordId);
            warning.setStatus("0");
            warning.setTriggerTime(new Date());

            String levelText = urgent ? "紧急" : "负面";
            String fragment = clip(hitText, HIT_TEXT_MAX);
            String kw = keywords == null ? "" : String.join("、", keywords);
            StringBuilder content = new StringBuilder();
            content.append("实时情绪识别命中（").append(levelText).append("）");
            if (intent != null && !intent.isEmpty())
            {
                content.append("；意图：").append(intent);
            }
            if (!kw.isEmpty())
            {
                content.append("；命中词：").append(kw);
            }
            if (!fragment.isEmpty())
            {
                content.append("；识别片段：").append(fragment);
            }
            warning.setContent(clip(content.toString(), CONTENT_MAX));

            riskWarningService.insertAiRiskWarning(warning);
            log.info("情绪联动风险预警 warningId={}, level={}, recordId={}",
                    warning.getWarningId(), urgent ? "urgent" : "negative", recordId);
            return warning.getWarningId();
        }
        catch (Exception e)
        {
            log.warn("情绪联动预警落库失败 recordId={}: {}", recordId, e.getMessage());
            return null;
        }
    }

    /** 排队中记录提优（仅升不降）；recordId 解析失败时按会话ID兜底查队。 */
    private void boostQueuePriority(boolean urgent, Long recordId, String sessionId)
    {
        if (queueMapper == null)
        {
            return;
        }
        int target = urgent ? urgentPriority : negativePriority;
        try
        {
            AiCallQueue queuing = null;
            if (recordId != null)
            {
                AiCallQueue q = new AiCallQueue();
                q.setRecordId(recordId);
                q.setQueueStatus("0");
                List<AiCallQueue> rows = queueMapper.selectAiCallQueueList(q);
                if (rows != null && !rows.isEmpty())
                {
                    queuing = rows.get(0);
                }
            }
            if (queuing == null && sessionId != null && !sessionId.isEmpty())
            {
                queuing = queueMapper.selectQueuingBySessionId(sessionId);
            }
            if (queuing == null)
            {
                // 通话已接通/已出队则无队可提（实时联动的排队窗口本来就短）
                return;
            }
            Integer current = queuing.getPriority();
            if (current != null && current >= target)
            {
                return;
            }
            AiCallQueue upd = new AiCallQueue();
            upd.setQueueId(queuing.getQueueId());
            upd.setPriority(target);
            queueMapper.updateAiCallQueue(upd);
            log.info("情绪联动队列提优 queueId={}, {} -> {}", queuing.getQueueId(), current, target);
        }
        catch (Exception e)
        {
            log.warn("情绪联动队列提优失败 recordId={}: {}", recordId, e.getMessage());
        }
    }

    /** sessionId 为纯数字（话单 recordId 字符串）时解析，否则 null */
    private Long parseRecordId(String sessionId)
    {
        if (sessionId == null || sessionId.isEmpty())
        {
            return null;
        }
        try
        {
            return Long.parseLong(sessionId.trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private String clip(String s, int max)
    {
        if (s == null)
        {
            return "";
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    /* ---------- 测试/装配注入点 ---------- */

    void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }
}
