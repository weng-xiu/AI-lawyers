package ai.lawyers.system.service.lawyers.voice.copilot;

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
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.mapper.lawyers.AiCopilotFeedbackMapper;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import ai.lawyers.system.service.lawyers.rag.KnowledgeQueryLogRecorder;
import ai.lawyers.system.service.lawyers.rag.RagChunk;
import ai.lawyers.system.service.lawyers.rag.RagSearchService;
import ai.lawyers.system.service.lawyers.rag.TicketVectorService;
import ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder;
import ai.lawyers.system.service.lawyers.voice.emotion.EmotionIntentDetector;
import ai.lawyers.system.service.lawyers.voice.emotion.EmotionIntentResult;

/**
 * F4：坐席 Copilot 实时辅助服务（通话中文本 → 案情要素 / 法条 / 相似工单）。
 *
 * <p>编排：ASR final 累计文本（节流由 VoiceSession 控制）→ ① LLM 结构化抽取
 * 纠纷类型/诉求/紧急度/关键事实（chatJson，场景 extract 计入 E5 成本看板）；
 * ② {@link RagSearchService} 取 Top-N 法条（含 chunkId 供点击溯源）；
 * ③ 以纠纷类型/诉求为短语匹配已办结历史工单。</p>
 *
 * <p>降级原则：总开关关闭/空文本 → 空结果；任一部分异常只标记该部分降级，
 * <b>任何路径不外抛</b>，不影响通话链路。</p>
 *
 * @author ai-lawyers
 */
@Service
public class CopilotAssistService
{
    private static final Logger log = LoggerFactory.getLogger(CopilotAssistService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 相似工单短语长度上限（LIKE 短语过长几乎不命中） */
    private static final int PHRASE_MAX = 20;

    @Value("${ai.copilot.enabled:true}")
    private boolean enabled;

    @Value("${ai.copilot.law-limit:3}")
    private int lawLimit;

    @Value("${ai.copilot.ticket-limit:3}")
    private int ticketLimit;

    /** P1-8：对客口播话术个性化总开关 */
    @Value("${ai.copilot.script-enabled:true}")
    private boolean scriptEnabled;

    /** P1-8：坐席采纳画像回溯天数（供 prompt 注入坐席习惯） */
    @Value("${ai.copilot.profile-days:30}")
    private int profileDays;

    @Autowired(required = false)
    private IAiModelConfigService modelConfigService;

    @Autowired(required = false)
    private RagSearchService ragSearchService;

    @Autowired
    private AiCallTicketMapper ticketMapper;

    /** P1-8：坐席采纳画像数据层（供话术个性化注入） */
    @Autowired(required = false)
    private AiCopilotFeedbackMapper feedbackMapper;

    /** P1-8：情绪实时识别器（纯规则零网络，直接实例化） */
    private final EmotionIntentDetector emotionDetector = new EmotionIntentDetector();

    /** P1-8：相似工单语义向量路（未装配/未就绪时回退 LIKE 短语） */
    @Autowired(required = false)
    private TicketVectorService ticketVectorService;

    @Autowired(required = false)
    private HotlineMetrics metrics;

    public boolean isEnabled()
    {
        return enabled;
    }

    /**
     * 执行一次实时辅助。任何情况下不抛异常。
     *
     * @param text      累计/新增通话文本
     * @param sessionId 语音会话ID（数字型时解析为 recordId 排除当前工单）
     * @return 辅助结果（可能部分降级，不为 null）
     */
    public CopilotAssistResult assist(String text, String sessionId)
    {
        return assist(text, sessionId, null);
    }

    /**
     * P1-8：执行一次实时辅助（含个性化对客口播话术）。任何情况下不抛异常。
     *
     * @param text      累计/新增通话文本
     * @param sessionId 语音会话ID（数字型时解析为 recordId 排除当前工单）
     * @param agentId   当前坐席ID（用于注入坐席采纳画像；null 时话术不带画像）
     * @return 辅助结果（可能部分降级，不为 null）
     */
    public CopilotAssistResult assist(String text, String sessionId, Long agentId)
    {
        CopilotAssistResult result = CopilotAssistResult.empty();
        if (!enabled || StringUtils.isEmpty(text) || text.trim().isEmpty())
        {
            return result;
        }
        String q = text.trim();
        EmotionIntentResult emotion = emotionDetector.analyze(q);
        extractElements(q, result, emotion);
        recommendLaws(q, result);
        recommendTickets(q, result, sessionId);
        buildSuggestedActions(result, sessionId);
        buildSuggestedScript(q, result, emotion, agentId);
        if (metrics != null)
        {
            metrics.incrementCopilotAssist();
        }
        return result;
    }

    /** ① LLM 结构化要素抽取（模型/解析失败 → elementDegraded=true） */
    private void extractElements(String text, CopilotAssistResult result, EmotionIntentResult emotion)
    {
        try
        {
            if (modelConfigService == null)
            {
                throw new IllegalStateException("模型配置服务未装配");
            }
            String userMessage = buildExtractUserMessage(text, emotion);
            String raw = modelConfigService.chatJson(buildExtractSystemPrompt(emotion), userMessage,
                    AiModelCallLogRecorder.SCENE_EXTRACT);
            JsonNode node = MAPPER.readTree(cleanJson(raw));
            result.setDisputeType(node.path("disputeType").asText("").trim());
            result.setClaims(toStringList(node.path("claims")));
            String urgency = node.path("urgency").asText("normal").trim();
            result.setUrgency("urgent".equalsIgnoreCase(urgency) ? "urgent" : "normal");
            result.setKeyFacts(toStringList(node.path("keyFacts")));
        }
        catch (Exception e)
        {
            log.warn("Copilot 要素抽取降级: {}", e.getMessage());
            result.setElementDegraded(true);
        }
    }

    /** 要素抽取 user 消息：注入规则情绪作为先验，提示模型优先采信（规则零网络、宁错报） */
    private String buildExtractUserMessage(String text, EmotionIntentResult emotion)
    {
        StringBuilder sb = new StringBuilder("通话文本：\n").append(text);
        if (emotion.isUrgent())
        {
            sb.append("\n\n【系统情绪先验】检测到紧急危机信号（")
              .append(String.join("、", emotion.getEmotionKeywords()))
              .append("），若与文本相符请将 urgency 置为 urgent。");
        }
        return sb.toString();
    }

    /** ② RAG 法条推荐（检索异常 → 空列表，不影响其余部分） */
    private void recommendLaws(String text, CopilotAssistResult result)
    {
        List<CopilotLaw> laws = new ArrayList<>();
        try
        {
            if (ragSearchService != null)
            {
                List<RagChunk> hits = ragSearchService.search(text, null,
                        KnowledgeQueryLogRecorder.SCENE_COPILOT);
                if (hits != null)
                {
                    int n = lawLimit > 0 ? lawLimit : 3;
                    for (RagChunk hit : hits)
                    {
                        if (laws.size() >= n)
                        {
                            break;
                        }
                        laws.add(new CopilotLaw(hit.getChunkId(), hit.getTitle(),
                                hit.getLawArticle(), hit.getSource()));
                    }
                }
            }
        }
        catch (Exception e)
        {
            log.warn("Copilot 法条推荐异常: {}", e.getMessage());
        }
        result.setLaws(laws);
    }

    /**
     * ③ 相似工单：P1-8 起优先语义向量路（完整通话文本 embedding 近邻），
     * 向量路未装配/未就绪/无命中时回退旧的纠纷类型+诉求 LIKE 短语路。
     */
    private void recommendTickets(String queryText, CopilotAssistResult result, String sessionId)
    {
        Long excludeRecordId = parseRecordId(sessionId);
        int n = ticketLimit > 0 ? ticketLimit : 3;

        // 向量路
        if (ticketVectorService != null && ticketVectorService.isReady())
        {
            try
            {
                List<AiCallTicket> tickets =
                        ticketVectorService.findSimilar(queryText, excludeRecordId, n);
                if (tickets != null && !tickets.isEmpty())
                {
                    result.setTickets(toCopilotTickets(tickets));
                    return;
                }
            }
            catch (Exception e)
            {
                log.warn("Copilot 相似工单向量路异常，回退 LIKE: {}", e.getMessage());
            }
        }

        // 回退：LIKE 短语路
        String phrase1 = clip(result.getDisputeType());
        String phrase2 = null;
        if (!result.getClaims().isEmpty())
        {
            phrase2 = clip(result.getClaims().get(0));
        }
        if (phrase1 == null && phrase2 == null)
        {
            result.setTickets(new ArrayList<CopilotTicket>());
            return;
        }
        try
        {
            List<AiCallTicket> tickets = ticketMapper.selectSimilarTickets(
                    phrase1, phrase2, excludeRecordId, n);
            result.setTickets(toCopilotTickets(tickets));
        }
        catch (Exception e)
        {
            log.warn("Copilot 相似工单查询异常: {}", e.getMessage());
        }
    }

    /** 工单实体转 Copilot 输出结构 */
    private List<CopilotTicket> toCopilotTickets(List<AiCallTicket> tickets)
    {
        List<CopilotTicket> out = new ArrayList<>();
        if (tickets != null)
        {
            for (AiCallTicket t : tickets)
            {
                out.add(new CopilotTicket(t.getTicketId(), t.getTicketNo(),
                        t.getTitle(), t.getStatus(), t.getContent()));
            }
        }
        return out;
    }

    /**
     * P1-8：构造代执行白名单动作（仅草稿，不自动执行）。
     * 要素抽取成功且有纠纷类型/诉求 → createTicket 草稿（标题/内容/优先级预填，人工确认修改）。
     */
    private void buildSuggestedActions(CopilotAssistResult result, String sessionId)
    {
        if (result.isElementDegraded())
        {
            return;
        }
        boolean hasType = StringUtils.isNotEmpty(result.getDisputeType());
        if (!hasType && result.getClaims().isEmpty())
        {
            return;
        }
        // 标题：纠纷类型 + 咨询工单；无类型时用首个诉求
        String title;
        if (hasType)
        {
            title = result.getDisputeType() + "咨询工单";
        }
        else
        {
            String claim = result.getClaims().get(0);
            title = claim.length() > 20 ? claim.substring(0, 20) : claim;
        }
        // 内容：诉求 + 关键事实
        StringBuilder content = new StringBuilder();
        if (!result.getClaims().isEmpty())
        {
            content.append("诉求：").append(String.join("；", result.getClaims()));
        }
        if (!result.getKeyFacts().isEmpty())
        {
            if (content.length() > 0)
            {
                content.append('\n');
            }
            content.append("关键事实：").append(String.join("；", result.getKeyFacts()));
        }
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("title", title);
        payload.put("content", content.toString());
        payload.put("priority", "urgent".equals(result.getUrgency()) ? "1" : "2");
        Long recordId = parseRecordId(sessionId);
        if (recordId != null)
        {
            payload.put("recordId", recordId);
        }
        List<CopilotAssistResult.SuggestedAction> actions = new ArrayList<>();
        actions.add(new CopilotAssistResult.SuggestedAction(
                "createTicket", "确认建单（草稿可修改）", payload));
        result.setActions(actions);

        // queryTicket：存在相似工单时，取 Top1 作为"查询该工单办理进度"候选（坐席确认后调进度接口）
        if (!result.getTickets().isEmpty())
        {
            CopilotTicket top = result.getTickets().get(0);
            if (top != null && top.getTicketId() != null)
            {
                java.util.Map<String, Object> queryPayload = new java.util.LinkedHashMap<>();
                queryPayload.put("ticketId", top.getTicketId());
                queryPayload.put("ticketNo", top.getTicketNo());
                queryPayload.put("title", top.getTitle());
                actions.add(new CopilotAssistResult.SuggestedAction(
                        "queryTicket", "查询工单进度", queryPayload));
            }
        }
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

    /**
     * P1-8：生成个性化对客口播话术。
     *
     * <p>以规则情绪（urgent/negative/neutral）+ 纠纷类型/诉求/关键事实 + 坐席近 N 天
     * 采纳画像为输入，调 {@link IAiModelConfigService#chat}（场景 agent）产出一段坐席可
     * 直接念读的口语化话术。开关关闭/模型未装配/异常 → 留空，任何路径不外抛。</p>
     */
    private void buildSuggestedScript(String text, CopilotAssistResult result,
                                      EmotionIntentResult emotion, Long agentId)
    {
        if (!scriptEnabled || modelConfigService == null)
        {
            return;
        }
        try
        {
            String profile = loadAgentProfile(agentId);
            String systemPrompt = buildScriptSystemPrompt(emotion, profile);
            String userMessage = buildScriptUserMessage(result);
            String script = modelConfigService.chat(systemPrompt, userMessage,
                    AiModelCallLogRecorder.SCENE_AGENT);
            if (script != null)
            {
                result.setSuggestedScript(script.trim());
            }
        }
        catch (Exception e)
        {
            log.warn("Copilot 口播话术生成降级（留空）: {}", e.getMessage());
        }
    }

    /** 取坐席采纳画像并格式化为一句画像描述；无坐席/无数据/未装配返回空串 */
    private String loadAgentProfile(Long agentId)
    {
        if (agentId == null || feedbackMapper == null)
        {
            return "";
        }
        try
        {
            int days = profileDays > 0 ? profileDays : 30;
            java.util.Date now = new java.util.Date();
            java.util.Date begin = new java.util.Date(now.getTime() - days * 24L * 3600_000L);
            java.util.Map<String, Object> row =
                    feedbackMapper.selectAgentProfile(agentId, begin, now);
            if (row == null)
            {
                return "";
            }
            long total = toLong(row.get("total"));
            if (total <= 0)
            {
                return "";
            }
            long adopt = toLong(row.get("adoptCount"));
            long modify = toLong(row.get("modifyCount"));
            long ignore = toLong(row.get("ignoreCount"));
            long rate = Math.round(adopt * 100d / total);
            return "该坐席近" + days + "天共收到" + total + "条建议，采纳率约" + rate + "%（采纳"
                    + adopt + "/修改" + modify + "/忽略" + ignore + "），请贴合其表达习惯调整详略。";
        }
        catch (Exception e)
        {
            log.warn("Copilot 坐席画像查询异常（话术不带画像）: {}", e.getMessage());
            return "";
        }
    }

    private long toLong(Object v)
    {
        if (v instanceof Number)
        {
            return ((Number) v).longValue();
        }
        return 0L;
    }

    /** 话术 user 消息：注入已抽案情要素（可能部分为空） */
    private String buildScriptUserMessage(CopilotAssistResult result)
    {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotEmpty(result.getDisputeType()))
        {
            sb.append("纠纷类型：").append(result.getDisputeType()).append('\n');
        }
        if (!result.getClaims().isEmpty())
        {
            sb.append("群众诉求：").append(String.join("；", result.getClaims())).append('\n');
        }
        if (!result.getKeyFacts().isEmpty())
        {
            sb.append("关键事实：").append(String.join("；", result.getKeyFacts())).append('\n');
        }
        if (sb.length() == 0)
        {
            sb.append("（案情要素暂不充分，请先安抚并引导群众说明情况）\n");
        }
        sb.append("请生成一句到一段对客口播话术。");
        return sb.toString();
    }

    /** 话术 SYSTEM_PROMPT：注入情绪应对口径 + 坐席画像（任一可空） */
    private String buildScriptSystemPrompt(EmotionIntentResult emotion, String profile)
    {
        StringBuilder sb = new StringBuilder(SCRIPT_SYSTEM_PROMPT_HEAD);
        if (emotion.isUrgent())
        {
            sb.append("\n当前群众情绪【紧急危机】：先稳住情绪、明确表达会尽力帮助，"
                    + "话术简短温和，必要时提示立即报警/寻求紧急帮助，不冷漠不推诿。");
        }
        else if (emotion.isNegative())
        {
            sb.append("\n当前群众情绪【负面不满】：先共情安抚、耐心倾听，语气诚恳，"
                    + "避免生硬法条条框和争辩。");
        }
        else
        {
            sb.append("\n当前群众情绪【平和】：可正常、清晰地说明口径与下一步。");
        }
        if (StringUtils.isNotEmpty(profile))
        {
            sb.append('\n').append(profile);
        }
        return sb.toString();
    }

    /** 要素抽取 SYSTEM_PROMPT：base 固定 schema（无变量部分，情绪先验走 user 消息） */
    private String buildExtractSystemPrompt(EmotionIntentResult emotion)
    {
        return SYSTEM_PROMPT;
    }

    /** 空或空白返回 null；否则截断至 PHRASE_MAX */
    private String clip(String phrase)
    {
        if (StringUtils.isEmpty(phrase))
        {
            return null;
        }
        String p = phrase.trim();
        if (p.isEmpty())
        {
            return null;
        }
        return p.length() > PHRASE_MAX ? p.substring(0, PHRASE_MAX) : p;
    }

    private Long parseRecordId(String sessionId)
    {
        if (StringUtils.isEmpty(sessionId))
        {
            return null;
        }
        String s = sessionId.trim();
        for (int i = 0; i < s.length(); i++)
        {
            if (!Character.isDigit(s.charAt(i)))
            {
                return null;
            }
        }
        try
        {
            return Long.valueOf(s);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
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

    /* 测试用注入点 */
    void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    /** 要素抽取系统提示词：只返回固定 schema JSON，不编造 */
    static final String SYSTEM_PROMPT =
            "你是12348公共法律服务热线的坐席助手。请基于通话文本客观提炼案情要素，"
            + "不得编造文本中没有的事实，不确定的字段留空。仅返回JSON："
            + "{\"disputeType\":\"纠纷类型（如劳动争议/婚姻家庭/民间借贷/交通事故/合同纠纷，6字内）\","
            + "\"claims\":[\"群众核心诉求，每项一条\"],"
            + "\"urgency\":\"紧急度，normal或urgent（人身安全/正在发生的侵害为urgent）\","
            + "\"keyFacts\":[\"关键事实：当事人关系/争议金额/时间/已采取行动等\"]}";

    /** P1-8：对客口播话术系统提示词公共头（情绪口径/坐席画像在运行时变量化追加） */
    private static final String SCRIPT_SYSTEM_PROMPT_HEAD =
            "你是12348公共法律服务热线的坐席助手，请为坐席撰写一段对客口播话术。"
            + "要求：口语化、礼貌、简明，第二人称直接对群众说话；只依据给定案情，"
            + "不编造事实、不作胜诉承诺、不代替官方结论；可包含安抚与明确的下一步引导，"
            + "直接输出话术正文，不要加标题、引号或解释。";
}
