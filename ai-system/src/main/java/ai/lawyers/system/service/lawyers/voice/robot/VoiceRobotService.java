package ai.lawyers.system.service.lawyers.voice.robot;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import ai.lawyers.system.service.lawyers.rag.RagChunk;
import ai.lawyers.system.service.lawyers.rag.RagSearchService;
import ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder;

/**
 * P3-E3：/ws/voice 语音机器人对话引擎（RAG 检索 + LLM 应答装配）。
 *
 * <p>单轮问答链路：群众问题（ASR final）→ {@link RagSearchService} 混合检索取 Top-K
 * → 装配 12348 口语化应答提示词 → {@link IAiModelConfigService#chat} 生成答复。
 * 检索与调用耗时分别经 {@link HotlineMetrics#recordVoiceRagMs} /
 * {@link HotlineMetrics#recordVoiceLlmFirstTokenMs} 埋点（4.1 节预算 40ms/600ms）。</p>
 *
 * <p>降级原则（宁给兜底话术，不拖垮语音链路）：</p>
 * <ul>
 *   <li>总开关 {@code ai.voice-robot.enabled}=false → 返回转人工兜底话术，degraded=true；</li>
 *   <li>RAG 无命中 → 仍调 LLM 通用应答（提示词声明无检索依据，引导转人工）；</li>
 *   <li>LLM 调用任何异常 → 兜底话术，degraded=true，<b>不外抛</b>；</li>
 *   <li>答复超 {@code ai.voice-robot.answer-max-chars} 截断（TTS 播报时长控制）。</li>
 * </ul>
 *
 * @author ai-lawyers
 */
@Service
public class VoiceRobotService
{
    private static final Logger log = LoggerFactory.getLogger(VoiceRobotService.class);

    /** 转人工兜底话术（开关关闭/模型故障时播报） */
    static final String FALLBACK_ANSWER = "抱歉，这个问题我暂时无法准确回答。请您稍等，正在为您转接人工坐席，由值班律师为您解答。";

    /** 系统提示词：12348 热线语音机器人人设（口语化、简短、依法条、不编造） */
    static final String SYSTEM_PROMPT =
            "你是广东12348公共法律服务热线的智能语音助手。请遵循以下规则回答群众来电问题：\n"
            + "1. 回答用于语音播报，必须口语化、简洁，控制在150字以内，不要使用列表符号和Markdown格式；\n"
            + "2. 优先依据检索到的法律法规知识回答，可提及法条名称与条号；\n"
            + "3. 检索内容不足以回答时，如实说明并建议群众转人工坐席咨询，不要编造法条；\n"
            + "4. 涉及紧急人身财产安全的情况，先提示拨打110或120，再给出法律建议。";

    /** 总开关（政务内网模型未就绪前默认关闭，VoiceSession 按降级话术处理） */
    @Value("${ai.voice-robot.enabled:false}")
    private boolean enabled;

    /** 播报答复长度上限（字），超出截断 */
    @Value("${ai.voice-robot.answer-max-chars:150}")
    private int answerMaxChars;

    /** RAG 上下文装配长度上限（字），防超 token */
    @Value("${ai.voice-robot.context-max-chars:1200}")
    private int contextMaxChars;

    @Autowired(required = false)
    private RagSearchService ragSearchService;

    @Autowired(required = false)
    private IAiModelConfigService modelConfigService;

    @Autowired(required = false)
    private HotlineMetrics metrics;

    public boolean isEnabled()
    {
        return enabled;
    }

    /**
     * 单轮问答。任何情况下不抛异常（异常收敛为兜底话术 + degraded 标记）。
     *
     * @param question  群众问题（ASR final 文本）
     * @param sessionId 语音会话ID（埋点 tag）
     * @return 应答结果（含答复文本/命中数/溯源/耗时/降级标记）
     */
    public VoiceRobotResult answer(String question, String sessionId)
    {
        if (StringUtils.isEmpty(question) || question.trim().isEmpty())
        {
            return VoiceRobotResult.degraded(FALLBACK_ANSWER, "empty_question");
        }
        if (!enabled)
        {
            return VoiceRobotResult.degraded(FALLBACK_ANSWER, "robot_disabled");
        }
        return runPipeline(question.trim(), sessionId);
    }

    /**
     * P1-8：上线前仿真入口——绕过总开关执行同一 RAG+LLM 管线（开关未开也可做回归评测）。
     * 空问题仍返回降级结果；其余行为与 {@link #answer} 完全一致。
     */
    public VoiceRobotResult simulateAnswer(String question, String sessionId)
    {
        if (StringUtils.isEmpty(question) || question.trim().isEmpty())
        {
            return VoiceRobotResult.degraded(FALLBACK_ANSWER, "empty_question");
        }
        return runPipeline(question.trim(), sessionId);
    }

    /** RAG 检索 + 提示词装配 + LLM 应答（answer 与仿真共用） */
    private VoiceRobotResult runPipeline(String question, String sessionId)
    {
        // ① RAG 检索（耗时埋点；服务未装配时按无命中处理）
        long ragBegin = System.nanoTime();
        List<RagChunk> hits = new ArrayList<>();
        try
        {
            if (ragSearchService != null)
            {
                List<RagChunk> r = ragSearchService.search(question.trim(), null);
                if (r != null)
                {
                    hits = r;
                }
            }
        }
        catch (Exception e)
        {
            log.warn("VoiceRobot RAG 检索异常 sessionId={}: {}", sessionId, e.getMessage());
        }
        long ragMs = (System.nanoTime() - ragBegin) / 1_000_000L;
        if (metrics != null)
        {
            metrics.recordVoiceRagMs(ragMs, sessionId);
        }

        // ② 装配提示词 + LLM 应答
        String userMessage = buildUserMessage(question.trim(), hits);
        long llmBegin = System.nanoTime();
        String answer;
        try
        {
            if (modelConfigService == null)
            {
                throw new IllegalStateException("模型配置服务未装配");
            }
            answer = modelConfigService.chat(SYSTEM_PROMPT, userMessage, AiModelCallLogRecorder.SCENE_VOICE_ROBOT);
        }
        catch (Exception e)
        {
            log.warn("VoiceRobot LLM 应答异常 sessionId={}: {}", sessionId, e.getMessage());
            VoiceRobotResult r = VoiceRobotResult.degraded(FALLBACK_ANSWER, "llm_error");
            r.setRagHits(hits.size());
            r.setSources(collectSources(hits));
            r.setRagMs(ragMs);
            return r;
        }
        long llmMs = (System.nanoTime() - llmBegin) / 1_000_000L;
        if (metrics != null)
        {
            metrics.recordVoiceLlmFirstTokenMs(llmMs, sessionId);
        }
        if (StringUtils.isEmpty(answer) || answer.trim().isEmpty())
        {
            VoiceRobotResult r = VoiceRobotResult.degraded(FALLBACK_ANSWER, "llm_empty");
            r.setRagHits(hits.size());
            r.setSources(collectSources(hits));
            r.setRagMs(ragMs);
            r.setLlmMs(llmMs);
            return r;
        }
        VoiceRobotResult r = VoiceRobotResult.ok(truncate(answer.trim(), answerMaxChars));
        r.setRagHits(hits.size());
        r.setSources(collectSources(hits));
        r.setRagMs(ragMs);
        r.setLlmMs(llmMs);
        return r;
    }

    /** 装配用户消息：检索依据（标题+法条+内容，总长按 contextMaxChars 截断）+ 群众问题 */
    private String buildUserMessage(String question, List<RagChunk> hits)
    {
        StringBuilder sb = new StringBuilder();
        if (!hits.isEmpty())
        {
            sb.append("以下是检索到的法律法规知识，供你参考：\n");
            int budget = contextMaxChars > 0 ? contextMaxChars : 1200;
            int used = 0;
            int idx = 1;
            for (RagChunk hit : hits)
            {
                StringBuilder item = new StringBuilder();
                item.append("【").append(idx++).append("】");
                if (StringUtils.isNotEmpty(hit.getTitle()))
                {
                    item.append(hit.getTitle()).append(" ");
                }
                if (StringUtils.isNotEmpty(hit.getLawArticle()))
                {
                    item.append("（").append(hit.getLawArticle()).append("）");
                }
                item.append("\n").append(hit.getContent() == null ? "" : hit.getContent()).append("\n");
                if (used + item.length() > budget)
                {
                    break;
                }
                sb.append(item);
                used += item.length();
            }
            sb.append("\n");
        }
        else
        {
            sb.append("（本次未检索到直接相关的法律法规知识，请如实说明并引导转人工。）\n\n");
        }
        sb.append("群众来电问题：").append(question);
        return sb.toString();
    }

    /** 溯源清单：标题（法条），供 answer_done 帧下发前端展示 */
    private List<String> collectSources(List<RagChunk> hits)
    {
        List<String> sources = new ArrayList<>();
        for (RagChunk hit : hits)
        {
            StringBuilder s = new StringBuilder();
            if (StringUtils.isNotEmpty(hit.getTitle()))
            {
                s.append(hit.getTitle());
            }
            if (StringUtils.isNotEmpty(hit.getLawArticle()))
            {
                if (s.length() > 0)
                {
                    s.append(" ");
                }
                s.append(hit.getLawArticle());
            }
            if (s.length() > 0)
            {
                sources.add(s.toString());
            }
        }
        return sources;
    }

    private static String truncate(String text, int max)
    {
        if (max <= 0 || text.length() <= max)
        {
            return text;
        }
        return text.substring(0, max);
    }

    /* 测试用注入点 */
    void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }
}
