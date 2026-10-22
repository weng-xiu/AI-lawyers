package ai.lawyers.framework.websocket.voice;

import java.util.concurrent.atomic.AtomicBoolean;
import ai.lawyers.system.service.lawyers.voice.emotion.EmotionIntentDetector;
import ai.lawyers.system.service.lawyers.voice.emotion.EmotionIntentResult;
import ai.lawyers.system.service.lawyers.voice.emotion.VoiceRiskActionService;

/**
 * E4 情绪/意图实时识别与联动（P5 从 {@link VoiceSession} 拆出）。
 *
 * <p>对 ASR partial/final 文本做纯词库识别（无状态、微秒级），命中后经联动
 * 动作服务（预警+提优+计数）并下发 emotion 帧。门禁：仅"说话人=来电者"的
 * 通道（role=caller 或 robot 模式）挂钩——坐席自身语音不作为风险来源。
 * 去重：urgent 每通话动作一次；negative 每通话一次且 urgent 已命中后不再
 * 动作；urgent 可覆盖在先的 negative（紧急高级预警优先，宁错报不遗漏）。</p>
 *
 * @author ai-lawyers
 */
final class VoiceEmotionWatcher
{
    /** 宿主回调：下发帧与 robot 模式读取（协作类不反向依赖会话门面） */
    interface Host
    {
        void send(String frame);

        /** 当前是否处于 robot 模式（robot ASR 即来电者语音） */
        boolean robotMode();
    }

    private final VoiceRiskActionService action;

    /** 纯词库识别器（无状态、微秒级） */
    private final EmotionIntentDetector detector = new EmotionIntentDetector();

    private final Host host;

    private final String sessionId;

    private final String role;

    /** 本会话 urgent 已动作（每通话只发一次高级预警） */
    private final AtomicBoolean urgentActed = new AtomicBoolean();

    /** 本会话 negative 已动作（urgent 命中后不再发 negative） */
    private final AtomicBoolean negativeActed = new AtomicBoolean();

    VoiceEmotionWatcher(VoiceRiskActionService action, Host host, String sessionId, String role)
    {
        this.action = action;
        this.host = host;
        this.sessionId = sessionId;
        this.role = role;
    }

    /**
     * 对一段 ASR 文本执行情绪/意图联动。
     *
     * @param text    ASR 文本
     * @param isFinal true=final（urgent/negative 均可）；false=partial（仅 urgent）
     */
    void detect(String text, boolean isFinal)
    {
        if (text == null || text.trim().isEmpty())
        {
            return;
        }
        if (!emotionChannel())
        {
            return;
        }
        EmotionIntentResult r = detector.analyze(text);
        if (r.isUrgent())
        {
            if (!urgentActed.compareAndSet(false, true))
            {
                return;
            }
            fire(r, text);
        }
        else if (isFinal && r.isNegative())
        {
            if (urgentActed.get() || !negativeActed.compareAndSet(false, true))
            {
                return;
            }
            fire(r, text);
        }
    }

    /** 来电者通道判定：caller 角色或 robot 模式（robot ASR 即来电者语音） */
    private boolean emotionChannel()
    {
        return "caller".equalsIgnoreCase(role) || host.robotMode();
    }

    /** 执行联动动作（预警+提优+计数）并下发 emotion 帧；动作服务内部全兜底 */
    private void fire(EmotionIntentResult r, String hitText)
    {
        Long warningId = action.handleEmotion(r.getEmotion(), r.getIntent(),
                hitText, r.getEmotionKeywords(), sessionId);
        host.send(VoiceFrames.emotion(r.getEmotion(), r.getIntent(), r.getEmotionKeywords(), warningId));
    }
}
