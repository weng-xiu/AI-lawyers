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

        try
        {
            // 2. 检索知识
            String knowledgeRefs = null;
            String knowledgeContext = buildKnowledgeContext(userMessage, config, isUseKnowledge(config));
            if (StringUtils.isNotEmpty(config.getKnowledgeIds()))
            {
                knowledgeRefs = config.getKnowledgeIds();
            }

            // 3. 组装提示词（含多轮历史）
            int contextRounds = config.getContextRounds() == null ? 5 : config.getContextRounds();
            String history = loadHistoryText(sessionId, contextRounds);
            String system = buildSystemPrompt(config, knowledgeContext);
            String user = buildUserMessage(history, userMessage);

            // 4. 调用大模型，约束返回 JSON
            String raw = modelConfigService.chatJson(system, user);
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
            log.error("智能体对话异常 agentId={} sessionId={}: {}", agentId, sessionId, e.getMessage(), e);
            AgentChatResult result = fallback("抱歉，智能助手暂时无法服务，正在为您转接人工。", true, "服务异常：" + e.getMessage());
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
     * 判断是否启用知识库：systemPrompt 未显式关闭时默认启用（无 knowledgeIds 则全库检索）
     */
    private boolean isUseKnowledge(AiAgentConfig config)
    {
        // 简化：只要配置了知识库或未显式说明，均启用检索；无知识时检索结果为空
        return true;
    }

    /**
     * 检索法律知识并拼接上下文
     */
    private String buildKnowledgeContext(String userMessage, AiAgentConfig config, boolean useKnowledge)
    {
        if (!useKnowledge)
        {
            return "";
        }
        List<AiLegalKnowledge> knows;
        if (StringUtils.isNotEmpty(config.getKnowledgeIds()))
        {
            // 指定知识库范围
            List<Long> ids = parseIds(config.getKnowledgeIds());
            knows = new ArrayList<>();
            for (Long id : ids)
            {
                AiLegalKnowledge k = legalKnowledgeService.selectAiLegalKnowledgeByKnowledgeId(id);
                if (k != null && "0".equals(k.getStatus()))
                {
                    knows.add(k);
                }
            }
        }
        else
        {
            knows = legalKnowledgeService.searchAiLegalKnowledge(userMessage);
            if (knows == null)
            {
                knows = Collections.emptyList();
            }
            // 只取启用状态
            knows = knows.stream().filter(k -> "0".equals(k.getStatus())).collect(Collectors.toList());
        }
        if (knows.isEmpty())
        {
            return "";
        }
        int topK = DEFAULT_TOP_K;
        if (knows.size() > topK)
        {
            knows = knows.subList(0, topK);
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
     * 从 Redis 读取最近 N 轮历史，拼接成文本；Redis 不可用时回退到 MySQL
     */
    @SuppressWarnings("unchecked")
    private String loadHistoryText(String sessionId, int rounds)
    {
        List<AiAgentMessage> history = null;
        try
        {
            Object cached = redisCache.getCacheObject(SESSION_KEY_PREFIX + sessionId);
            if (cached instanceof List)
            {
                history = (List<AiAgentMessage>) cached;
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

    /**
     * 双写：Redis 热缓存 + MySQL 持久化
     */
    @SuppressWarnings("unchecked")
    private void persistTurn(AiAgentConfig config, String sessionId, String userMessage,
                             AgentChatResult result, Long recordId, Long flowId, Long nodeId,
                             String callerNumber)
    {
        Integer maxTurn = agentMessageMapper.selectMaxTurnNo(sessionId);
        int turnNo = (maxTurn == null ? 0 : maxTurn) + 1;
        result.setTurnNo(turnNo);

        AiAgentMessage userMsg = newMessage(sessionId, config.getAgentId(), flowId, nodeId,
                recordId, callerNumber, "user", userMessage, "0", null, result.getKnowledgeRefs(), turnNo);
        AiAgentMessage botMsg = newMessage(sessionId, config.getAgentId(), flowId, nodeId,
                recordId, callerNumber, "assistant", result.getReply(),
                result.isHandoff() ? "1" : "0", result.getReason(), result.getKnowledgeRefs(), turnNo);

        // MySQL 持久化
        try
        {
            agentMessageMapper.insertAiAgentMessage(userMsg);
            agentMessageMapper.insertAiAgentMessage(botMsg);
        }
        catch (Exception e)
        {
            log.warn("智能体对话消息落库失败 sessionId={}: {}", sessionId, e.getMessage());
        }

        // Redis 缓存（更新最近若干轮）
        try
        {
            String key = SESSION_KEY_PREFIX + sessionId;
            Object cached = redisCache.getCacheObject(key);
            List<AiAgentMessage> list;
            if (cached instanceof List)
            {
                list = (List<AiAgentMessage>) cached;
            }
            else
            {
                list = new ArrayList<>();
            }
            list.add(userMsg);
            list.add(botMsg);
            int keep = (config.getContextRounds() == null ? 5 : config.getContextRounds()) * 2;
            if (list.size() > keep)
            {
                list = new ArrayList<>(list.subList(list.size() - keep, list.size()));
            }
            redisCache.setCacheObject(key, list, (int) SESSION_TTL_MINUTES, TimeUnit.MINUTES);
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
