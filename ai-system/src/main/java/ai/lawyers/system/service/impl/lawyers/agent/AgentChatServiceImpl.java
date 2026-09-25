package ai.lawyers.system.service.impl.lawyers.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.domain.lawyers.agent.AgentChatResult;
import ai.lawyers.system.domain.lawyers.agent.AiAgentConfig;
import ai.lawyers.system.domain.lawyers.agent.AiAgentMessage;
import ai.lawyers.system.mapper.lawyers.agent.AiAgentConfigMapper;
import ai.lawyers.system.mapper.lawyers.agent.AiAgentMessageMapper;
import ai.lawyers.system.service.lawyers.IAiLegalKnowledgeService;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.agent.IAgentChatService;
import ai.lawyers.system.service.lawyers.rag.RagChunk;
import ai.lawyers.system.service.lawyers.rag.RagSearchService;

/**
 * 智能体对话服务实现
 *
 * <p>本期实现 provider=local：基于本项目法律知识库 + 默认大模型（RAG 简易实现），
 * 会话上下文采用 Redis 热缓存 + MySQL 持久化双写。</p>
 *
 * @author ai-lawyers
 */
@Service
public class AgentChatServiceImpl implements IAgentChatService
{
    private static final Logger log = LoggerFactory.getLogger(AgentChatServiceImpl.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 会话历史 Redis key 前缀 */
    private static final String SESSION_KEY_PREFIX = "ivr:agent:chat:";

    /** 会话历史 TTL（分钟） */
    private static final long SESSION_TTL_MINUTES = 30L;

    /** 默认检索知识条数 */
    private static final int DEFAULT_TOP_K = 3;

    /** 单条知识内容最大拼接长度 */
    private static final int KNOWLEDGE_MAX_LEN = 500;

    @Autowired
    private AiAgentConfigMapper agentConfigMapper;

    @Autowired
    private AiAgentMessageMapper agentMessageMapper;

    @Autowired
    private IAiLegalKnowledgeService legalKnowledgeService;

    @Autowired
    private IAiModelConfigService modelConfigService;

    /** T3 RAG 混合检索（关键词 FULLTEXT + 向量近邻 + RRF 融合） */
    @Autowired
    private RagSearchService ragSearchService;

    @Autowired
    private RedisCache redisCache;

    @Override
    public AgentChatResult chat(Long agentId, String sessionId, String userMessage,
                                Long recordId, Long flowId, Long nodeId, String callerNumber)
    {
        if (agentId == null)
        {
            return fallback("未配置智能体，为您转接人工。", true, "未配置智能体");
        }
        AiAgentConfig config = agentConfigMapper.selectAiAgentConfigByAgentId(agentId);
        if (config == null)
        {
            return fallback("智能体不存在或已停用，为您转接人工。", true, "智能体不存在");
        }
        if (StringUtils.isEmpty(userMessage))
        {
            return fallback(config.getWelcome() != null ? config.getWelcome()
                    : "您好，请问有什么可以帮您？", false, null);
        }

        // 1. 关键词转人工兜底（优先于模型，防止漏判）
        if (containsHandoffKeyword(userMessage, config.getHandoffKeywords()))
        {
            AgentChatResult result = new AgentChatResult("好的，正在为您转接人工坐席，请稍候。", true, "命中转人工关键词");
            persistTurn(config, sessionId, userMessage, result, recordId, flowId, nodeId, callerNumber);
            return result;
        }

        // 2. 检索知识（提到 try 外，R3：模型故障时用知识库话术兜底，避免一律转人工打爆人工坐席）
        String knowledgeRefs = null;
        String knowledgeContext = "";
        if (isUseKnowledge(config))
        {
            List<Long> scopeIds = parseIds(config.getKnowledgeIds());
            knowledgeContext = buildKnowledgeContext(userMessage, scopeIds);
            // knowledgeRefs 优先留痕智能体配置的限定知识范围；混合检索命中时留痕实际命中的知识
            knowledgeRefs = StringUtils.isNotEmpty(config.getKnowledgeIds())
                    ? config.getKnowledgeIds() : null;
            if (StringUtils.isEmpty(knowledgeContext))
            {
                // RAG 分块路无结果（或分块表未就绪）：限定范围时按 ID 取整知识，全库时走旧 LIKE 检索
                knowledgeContext = scopeIds.isEmpty()
                        ? buildLegacyKnowledgeContext(userMessage)
                        : buildScopedKnowledgeContext(scopeIds);
            }
        }

        try
        {
            // 3. 组装提示词（含多轮历史）
            int contextRounds = config.getContextRounds() == null ? 5 : config.getContextRounds();
            String history = loadHistoryText(sessionId, contextRounds);
            String system = buildSystemPrompt(config, knowledgeContext);
            String user = buildUserMessage(history, userMessage);

            // 4. 调用大模型，约束返回 JSON
            String raw = modelConfigService.chatJson(system, user,
                    ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder.SCENE_AGENT);
            JsonNode node = MAPPER.readTree(cleanJson(raw));
            String reply = node.path("reply").asText("");
            boolean handoff = node.path("handoff").asBoolean(false);
            String reason = node.path("reason").asText("");
            Long categoryId = node.has("categoryId") && !node.get("categoryId").isNull()
                    ? node.get("categoryId").asLong() : config.getCategoryId();

            if (StringUtils.isEmpty(reply))
            {
                reply = "抱歉，我暂时无法理解您的问题，为您转接人工。";
                handoff = true;
                reason = "模型无有效回复";
            }

            AgentChatResult result = new AgentChatResult(reply, handoff, reason);
            result.setCategoryId(categoryId);
            result.setKnowledgeRefs(knowledgeRefs);

            // 5. 双写会话历史
            persistTurn(config, sessionId, userMessage, result, recordId, flowId, nodeId, callerNumber);
            return result;
        }
        catch (Exception e)
        {
            log.warn("智能体大模型调用异常，进入分级降级 agentId={} sessionId={}: {}",
                    agentId, sessionId, e.getMessage());
            // R3 分级降级：模型故障时优先用已检索到的知识库话术兜底（不转人工），
            // 避免大模型抖动导致全部来电压向人工造成雪崩；确无知识可用时才有序转人工。
            AgentChatResult result = buildDegradedReply(knowledgeContext, e.getMessage());
            result.setKnowledgeRefs(knowledgeRefs);
            // 异常也尽量落库留痕
            try
            {
                persistTurn(config, sessionId, userMessage, result, recordId, flowId, nodeId, callerNumber);
            }
            catch (Exception ignore) {}
            return result;
        }
    }

    /**
     * T3-2 判断是否启用知识库：RAG 总开关（ai.rag.enabled）开启且智能体启用时生效。
     * 智能体状态为停用（status='0'，本系统约定 1启用）时不检索知识。
     */
    private boolean isUseKnowledge(AiAgentConfig config)
    {
        if (!ragSearchService.isEnabled())
        {
            return false;
        }
        // 智能体停用（status 约定 1=启用）时不检索
        return config.getStatus() == null || "1".equals(config.getStatus());
    }

    /**
     * T3 RAG 混合检索并拼接上下文：关键词路（FULLTEXT/LIKE）+ 向量路近邻 → RRF 融合 → Top-K。
     * 分块表未就绪/两路均无命中时返回空串，由调用方回退旧整知识 LIKE 路。
     *
     * @param userMessage 用户问句
     * @param scopeIds    智能体限定的知识ID范围（空表示全库检索）
     */
    private String buildKnowledgeContext(String userMessage, List<Long> scopeIds)
    {
        try
        {
            List<RagChunk> hits = ragSearchService.search(userMessage, scopeIds);
            if (hits == null || hits.isEmpty())
            {
                return "";
            }
            return ragSearchService.buildContext(hits);
        }
        catch (Exception e)
        {
            log.warn("RAG 混合检索异常，将回退旧检索路：{}", e.getMessage());
            return "";
        }
    }

    /**
     * 限定知识范围的兜底：按智能体配置的 knowledgeIds 逐条取整知识拼接（RAG 分块路无命中时）。
     */
    private String buildScopedKnowledgeContext(List<Long> scopeIds)
    {
        List<AiLegalKnowledge> knows = new ArrayList<>();
        for (Long id : scopeIds)
        {
            AiLegalKnowledge k = legalKnowledgeService.selectAiLegalKnowledgeByKnowledgeId(id);
            if (k != null && "0".equals(k.getStatus()))
            {
                knows.add(k);
            }
        }
        return formatKnowledge(knows);
    }

    /**
     * 旧整知识 LIKE 检索路（RAG 分块路不可用/无命中时兜底）：保留原有"整句 LIKE 三字段 →
     * 取前 N 条 → 内容截断拼 Prompt"逻辑，保证问答功能始终可用。
     */
    private String buildLegacyKnowledgeContext(String userMessage)
    {
        List<AiLegalKnowledge> knows = legalKnowledgeService.searchAiLegalKnowledge(userMessage);
        if (knows == null)
        {
            knows = Collections.emptyList();
        }
        knows = knows.stream().filter(k -> "0".equals(k.getStatus())).collect(Collectors.toList());
        return formatKnowledge(knows);
    }

    /**
     * 将整知识列表拼为 Prompt 上下文（标题：内容（法条：…）），供旧 LIKE 路/限定范围兜底复用。
     */
    private String formatKnowledge(List<AiLegalKnowledge> knows)
    {
        if (knows == null || knows.isEmpty())
        {
            return "";
        }
        if (knows.size() > DEFAULT_TOP_K)
        {
            knows = knows.subList(0, DEFAULT_TOP_K);
        }
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (AiLegalKnowledge k : knows)
        {
            String content = k.getContent();
            if (content != null && content.length() > KNOWLEDGE_MAX_LEN)
            {
                content = content.substring(0, KNOWLEDGE_MAX_LEN) + "...";
            }
            sb.append(idx++).append(". ").append(safe(k.getTitle()))
              .append("：").append(safe(content));
            if (StringUtils.isNotEmpty(k.getLawArticle()))
            {
                sb.append("（法条：").append(k.getLawArticle()).append("）");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String buildSystemPrompt(AiAgentConfig config, String knowledgeContext)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(StringUtils.isNotEmpty(config.getSystemPrompt()) ? config.getSystemPrompt()
                : "你是12348公共法律服务热线的智能法律助手，请基于参考知识准确、通俗地回答群众的法律咨询。");
        sb.append("\n\n输出要求：仅返回一个JSON对象，格式为 "
                + "{\"reply\":\"给用户的回答\",\"handoff\":false,\"reason\":\"若需转人工说明原因，否则空字符串\",\"categoryId\":null}。");
        sb.append("\n其中 handoff 为布尔值；当用户明确要求人工/律师、情绪激动、投诉信访、问题超出法律知识范围或你无法准确回答时置为 true。");
        sb.append("\n不要输出JSON以外的任何内容。");
        if (StringUtils.isNotEmpty(knowledgeContext))
        {
            sb.append("\n\n【参考法律知识】\n").append(knowledgeContext);
        }
        return sb.toString();
    }

    private String buildUserMessage(String history, String userMessage)
    {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotEmpty(history))
        {
            sb.append("【近期对话历史】\n").append(history).append("\n\n");
        }
        sb.append("【用户当前问题】\n").append(userMessage);
        return sb.toString();
    }

    /**
     * 从 Redis 读取最近 N 轮历史，拼接成文本；Redis 不可用时回退到 MySQL。
     * T1-7：历史改用 Redis list（RPUSH+LTRIM 原子追加），消除旧 get→set 整串覆盖的并发丢消息问题；
     * 升级前遗留的 string 结构 key 在 LRANGE 时会抛 WRONGTYPE，捕获后回退 DB，待 TTL 自然过期。
     */
    private String loadHistoryText(String sessionId, int rounds)
    {
        List<AiAgentMessage> history = null;
        try
        {
            List<Object> raw = redisCache.redisTemplate.opsForList()
                    .range(SESSION_KEY_PREFIX + sessionId, 0, -1);
            if (raw != null && !raw.isEmpty())
            {
                history = new ArrayList<>();
                for (Object o : raw)
                {
                    if (o instanceof AiAgentMessage)
                    {
                        history.add((AiAgentMessage) o);
                    }
                }
            }
        }
        catch (Exception e)
        {
            log.debug("读取智能体会话Redis缓存失败，回退DB: {}", e.getMessage());
        }
        if (history == null || history.isEmpty())
        {
            history = agentMessageMapper.selectRecentBySession(sessionId, rounds * 2);
            if (history == null)
            {
                history = Collections.emptyList();
            }
        }
        // 只保留最后 rounds*2 条
        if (history.size() > rounds * 2)
        {
            history = history.subList(history.size() - rounds * 2, history.size());
        }
        StringBuilder sb = new StringBuilder();
        for (AiAgentMessage m : history)
        {
            sb.append("user".equals(m.getRole()) ? "用户：" : "助手：")
              .append(safe(m.getContent())).append("\n");
        }
        return sb.toString().trim();
    }

    /** 会话轮次计数器 Redis key 前缀（T1-7 原子取号） */
    private static final String SESSION_TURN_KEY_PREFIX = "ivr:agent:turn:";

    /** 单会话历史在 Redis list 中保留的最大条数（contextRounds 缺省 5 轮 * 2 条） */
    private static final int HISTORY_KEEP_MESSAGES = 20;

    /**
     * 双写：MySQL 持久化 + Redis 热缓存。
     * T1-7（C7）：turnNo 由 Redis INCR 原子分配（每条消息独立递增，配合 uk_session_turn 唯一约束）；
     * Redis 历史改 list 原子追加（RPUSH+LTRIM），消除旧"读整串→追加→写整串"的并发覆盖丢消息。
     */
    private void persistTurn(AiAgentConfig config, String sessionId, String userMessage,
                             AgentChatResult result, Long recordId, Long flowId, Long nodeId,
                             String callerNumber)
    {
        int userTurn = nextTurnNo(sessionId);
        int botTurn = nextTurnNo(sessionId);
        result.setTurnNo(userTurn);

        AiAgentMessage userMsg = newMessage(sessionId, config.getAgentId(), flowId, nodeId,
                recordId, callerNumber, "user", userMessage, "0", null, result.getKnowledgeRefs(), userTurn);
        AiAgentMessage botMsg = newMessage(sessionId, config.getAgentId(), flowId, nodeId,
                recordId, callerNumber, "assistant", result.getReply(),
                result.isHandoff() ? "1" : "0", result.getReason(), result.getKnowledgeRefs(), botTurn);

        // MySQL 持久化（uk_session_turn 冲突时重新取号重试，兜底 Redis 计数器与 DB 漂移）
        insertWithRetry(userMsg);
        insertWithRetry(botMsg);

        // Redis 历史 list 原子追加并裁剪，逐条独立失败不影响已落库结果
        appendHistory(sessionId, config.getContextRounds(), userMsg, botMsg);
    }

    /**
     * T1-7 原子分配会话内消息序号：Redis INCR 保证并发唯一；
     * 计数器首次出现（返回 1）时以 DB 现有最大 turn_no 播种，避免 Redis 重启/清库后小号撞唯一约束；
     * Redis 不可用则降级为 DB max(turn_no)+1（单实例可用，多实例靠插入重试兜底）。
     */
    private int nextTurnNo(String sessionId)
    {
        String key = SESSION_TURN_KEY_PREFIX + sessionId;
        try
        {
            Long seq = redisCache.redisTemplate.opsForValue().increment(key);
            if (seq != null && seq == 1L)
            {
                Integer maxTurn = agentMessageMapper.selectMaxTurnNo(sessionId);
                if (maxTurn != null && maxTurn > 0)
                {
                    // 播种到 DB 最大值，再 INCR 取下一号
                    redisCache.redisTemplate.opsForValue().set(key, maxTurn, SESSION_TTL_MINUTES, TimeUnit.MINUTES);
                    seq = redisCache.redisTemplate.opsForValue().increment(key);
                }
            }
            redisCache.expire(key, SESSION_TTL_MINUTES * 60);
            if (seq != null)
            {
                return seq.intValue();
            }
        }
        catch (Exception e)
        {
            log.warn("智能体turnNo原子取号失败，降级DB取号 sessionId={}: {}", sessionId, e.getMessage());
        }
        Integer maxTurn = agentMessageMapper.selectMaxTurnNo(sessionId);
        return (maxTurn == null ? 0 : maxTurn) + 1;
    }

    /**
     * 插入消息，遇 uk_session_turn 唯一键冲突时重新取号重试，最多 3 次。
     */
    private void insertWithRetry(AiAgentMessage msg)
    {
        for (int attempt = 1; attempt <= 3; attempt++)
        {
            try
            {
                agentMessageMapper.insertAiAgentMessage(msg);
                return;
            }
            catch (org.springframework.dao.DuplicateKeyException e)
            {
                log.warn("智能体消息turnNo唯一键冲突，重新取号重试 sessionId={} turnNo={} attempt={}",
                        msg.getSessionId(), msg.getTurnNo(), attempt);
                msg.setTurnNo(nextTurnNo(msg.getSessionId()));
            }
        }
        log.error("智能体对话消息落库重试3次仍失败 sessionId={}", msg.getSessionId());
    }

    /**
     * T1-7 Redis list 原子追加历史：RPUSH 追加后 LTRIM 只保留最近 keep 条，并刷新 TTL；
     * 缓存失败仅影响热读（可回退 DB），不影响主流程。
     */
    private void appendHistory(String sessionId, Integer contextRounds, AiAgentMessage... msgs)
    {
        try
        {
            String key = SESSION_KEY_PREFIX + sessionId;
            int keep = (contextRounds == null || contextRounds <= 0 ? 5 : contextRounds) * 2;
            if (keep > HISTORY_KEEP_MESSAGES)
            {
                keep = HISTORY_KEEP_MESSAGES;
            }
            redisCache.redisTemplate.opsForList().rightPushAll(key, msgs);
            // LTRIM 保留最后 keep 条（索引 -keep 到 -1）
            redisCache.redisTemplate.opsForList().trim(key, -keep, -1);
            redisCache.expire(key, SESSION_TTL_MINUTES * 60);
        }
        catch (Exception e)
        {
            log.debug("智能体会话Redis写入失败（仅影响缓存）: {}", e.getMessage());
        }
    }

    private AiAgentMessage newMessage(String sessionId, Long agentId, Long flowId, Long nodeId,
                                      Long recordId, String callerNumber, String role, String content,
                                      String handoff, String reason, String knowledgeRefs, int turnNo)
    {
        AiAgentMessage m = new AiAgentMessage();
        m.setSessionId(sessionId);
        m.setAgentId(agentId);
        m.setFlowId(flowId);
        m.setNodeId(nodeId);
        m.setRecordId(recordId);
        m.setCallerNumber(callerNumber);
        m.setRole(role);
        m.setContent(content);
        m.setHandoff(handoff);
        m.setReason(reason);
        m.setKnowledgeRefs(knowledgeRefs);
        m.setTurnNo(turnNo);
        return m;
    }

    private boolean containsHandoffKeyword(String text, String keywords)
    {
        if (StringUtils.isEmpty(text) || StringUtils.isEmpty(keywords))
        {
            return false;
        }
        for (String kw : keywords.split(","))
        {
            String k = kw.trim();
            if (!k.isEmpty() && text.contains(k))
            {
                return true;
            }
        }
        return false;
    }

    private List<Long> parseIds(String ids)
    {
        if (StringUtils.isEmpty(ids))
        {
            return Collections.emptyList();
        }
        return Arrays.stream(ids.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::valueOf).collect(Collectors.toList());
    }

    private AgentChatResult fallback(String reply, boolean handoff, String reason)
    {
        return new AgentChatResult(reply, handoff, reason);
    }

    /**
     * R3 分级降级：大模型不可用时，用已检索的知识库内容组织兜底答复（不转人工）；
     * 无可用知识时才转人工，避免模型抖动引发人工坐席雪崩。
     *
     * @param knowledgeContext 检索到的知识上下文（buildKnowledgeContext 产物）
     */
    private AgentChatResult buildDegradedReply(String knowledgeContext, String errorMsg)
    {
        if (StringUtils.isNotEmpty(knowledgeContext))
        {
            // 取第一条知识作为参考答复（知识上下文格式："序号. 标题：内容（法条：…）"）
            String firstLine = knowledgeContext.trim();
            int nl = firstLine.indexOf('\n');
            if (nl > 0)
            {
                firstLine = firstLine.substring(0, nl);
            }
            // 去掉前导 "1. " 序号
            firstLine = firstLine.replaceFirst("^\\d+\\.\\s*", "");
            String reply = "智能助手当前响应较慢，先为您提供相关法律参考：" + firstLine
                    + "。如未能解决您的问题，可随时要求转接人工律师。";
            AgentChatResult result = new AgentChatResult(reply, false, "模型故障，知识库话术兜底");
            return result;
        }
        return new AgentChatResult("抱歉，智能助手暂时无法服务，正在为您转接人工坐席，请稍候。",
                true, "模型故障且无知识库可兜底：" + errorMsg);
    }

    private String safe(String s)
    {
        return s == null ? "" : s;
    }

    /**
     * 截取首个 { 到末个 } 之间的内容，兼容模型返回 markdown 代码块包裹
     */
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
