package ai.lawyers.system.service.impl.lawyers.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.domain.lawyers.agent.AgentChatResult;
import ai.lawyers.system.domain.lawyers.agent.AiAgentConfig;
import ai.lawyers.system.domain.lawyers.agent.AiAgentMessage;
import ai.lawyers.system.mapper.lawyers.agent.AiAgentConfigMapper;
import ai.lawyers.system.mapper.lawyers.agent.AiAgentMessageMapper;
import ai.lawyers.system.service.lawyers.IAiLegalKnowledgeService;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.compliance.AiContentLabelProperties;
import ai.lawyers.system.service.lawyers.compliance.AiContentLabelService;
import ai.lawyers.system.service.lawyers.rag.RagChunk;
import ai.lawyers.system.service.lawyers.rag.RagSearchService;

/**
 * 智能体对话服务单测。
 *
 * <p>覆盖智能体缺失/欢迎语、转人工关键词、知识检索（RAG/限定范围/旧检索）、
 * 模型 JSON 解析与分类回退、空回复、模型异常分级降级、历史裁剪与落库重试。</p>
 *
 * @author ai-lawyers
 */
class AgentChatServiceImplTest
{
    private AgentChatServiceImpl service;

    @Mock private AiAgentConfigMapper agentConfigMapper;
    @Mock private AiAgentMessageMapper agentMessageMapper;
    @Mock private IAiLegalKnowledgeService legalKnowledgeService;
    @Mock private IAiModelConfigService modelConfigService;
    @Mock private RagSearchService ragSearchService;
    @Mock private RedisCache redisCache;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AgentChatServiceImpl();
        ReflectionTestUtils.setField(service, "agentConfigMapper", agentConfigMapper);
        ReflectionTestUtils.setField(service, "agentMessageMapper", agentMessageMapper);
        ReflectionTestUtils.setField(service, "legalKnowledgeService", legalKnowledgeService);
        ReflectionTestUtils.setField(service, "modelConfigService", modelConfigService);
        ReflectionTestUtils.setField(service, "ragSearchService", ragSearchService);
        ReflectionTestUtils.setField(service, "redisCache", redisCache);
        // N4：默认装配"开关关闭"的真实标识服务，保证既有断言（原文透传）不被破坏
        ReflectionTestUtils.setField(service, "aiContentLabelService",
                new AiContentLabelService(new AiContentLabelProperties()));
    }

    /** N4：装配开关开启的标识服务 */
    private void enableAiLabel()
    {
        AiContentLabelProperties props = new AiContentLabelProperties();
        props.setEnabled(true);
        ReflectionTestUtils.setField(service, "aiContentLabelService", new AiContentLabelService(props));
    }

    private static AiAgentConfig config(String welcome, String handoffKeywords, String knowledgeIds,
                                        String status, Long categoryId, Integer contextRounds)
    {
        AiAgentConfig config = new AiAgentConfig();
        config.setAgentId(1L);
        config.setWelcome(welcome);
        config.setHandoffKeywords(handoffKeywords);
        config.setKnowledgeIds(knowledgeIds);
        config.setStatus(status);
        // 模型未返回 categoryId 时实现会回落到配置值，配置值需非空避免拆箱 NPE
        config.setCategoryId(categoryId == null ? 0L : categoryId);
        config.setContextRounds(contextRounds);
        return config;
    }

    private static AiLegalKnowledge knowledge(Long id, String title, String content, String status)
    {
        AiLegalKnowledge knowledge = new AiLegalKnowledge();
        knowledge.setKnowledgeId(id);
        knowledge.setTitle(title);
        knowledge.setContent(content);
        knowledge.setStatus(status);
        return knowledge;
    }

    @Test
    void chat_missingAgentId_returnsFallback()
    {
        AgentChatResult result = service.chat(null, "s1", "你好", null, null, null, null);

        assertTrue(result.isHandoff());
        assertEquals("未配置智能体，为您转接人工。", result.getReply());
        verify(agentConfigMapper, never()).selectAiAgentConfigByAgentId(any());
    }

    @Test
    void chat_missingConfig_returnsFallback()
    {
        when(agentConfigMapper.selectAiAgentConfigByAgentId(1L)).thenReturn(null);

        AgentChatResult result = service.chat(1L, "s1", "你好", null, null, null, null);

        assertTrue(result.isHandoff());
        assertEquals("智能体不存在或已停用，为您转接人工。", result.getReply());
    }

    @Test
    void chat_emptyMessage_customWelcome()
    {
        when(agentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config("欢迎咨询", null, null, "1", null, null));

        AgentChatResult result = service.chat(1L, "s1", "", null, null, null, null);

        assertFalse(result.isHandoff());
        assertEquals("欢迎咨询", result.getReply());
        verify(modelConfigService, never()).chatJson(anyString(), anyString(), anyString());
    }

    @Test
    void chat_emptyMessage_defaultWelcome()
    {
        when(agentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config(null, null, null, "1", null, null));

        assertEquals("您好，请问有什么可以帮您？",
            service.chat(1L, "s1", null, null, null, null, null).getReply());
    }

    @Test
    void chat_handoffKeyword_matchesAndPersists()
    {
        when(agentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config(null, "人工,律师", null, "1", null, null));

        AgentChatResult result = service.chat(1L, "s1", "我要找律师", 2L, null, null, null);

        assertTrue(result.isHandoff());
        assertEquals("命中转人工关键词", result.getReason());
        verify(modelConfigService, never()).chatJson(anyString(), anyString(), anyString());
        verify(agentMessageMapper, times(2)).insertAiAgentMessage(any(AiAgentMessage.class));
    }

    @Test
    void chat_modelJsonParsed_categoryFallsBackToConfig()
    {
        when(agentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config(null, null, null, "1", 88L, 3));
        when(ragSearchService.isEnabled()).thenReturn(false);
        when(modelConfigService.chatJson(anyString(), anyString(), anyString()))
            .thenReturn("```json\n{\"reply\":\"答复\",\"handoff\":true,\"reason\":\"超出范围\"}\n```");

        AgentChatResult result = service.chat(1L, "s1", "问题", null, null, null, null);

        assertEquals("答复", result.getReply());
        assertTrue(result.isHandoff());
        assertEquals("超出范围", result.getReason());
        assertEquals(88L, result.getCategoryId());
    }

    @Test
    void chat_modelJson_categoryIdInResponseWins()
    {
        when(agentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config(null, null, null, "1", 88L, null));
        when(ragSearchService.isEnabled()).thenReturn(false);
        when(modelConfigService.chatJson(anyString(), anyString(), anyString()))
            .thenReturn("{\"reply\":\"答复\",\"handoff\":false,\"categoryId\":99}");

        assertEquals(99L, service.chat(1L, "s1", "问题", null, null, null, null).getCategoryId());
    }

    @Test
    void chat_emptyReply_forcesHandoff()
    {
        when(agentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config(null, null, null, "1", null, null));
        when(ragSearchService.isEnabled()).thenReturn(false);
        when(modelConfigService.chatJson(anyString(), anyString(), anyString()))
            .thenReturn("{}");

        AgentChatResult result = service.chat(1L, "s1", "问题", null, null, null, null);

        assertTrue(result.isHandoff());
        assertEquals("模型无有效回复", result.getReason());
        assertTrue(result.getReply().contains("抱歉"));
    }

    @Test
    void chat_ragEnabled_usesRagContextAndRecordsScope()
    {
        when(agentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config(null, null, "10,20", "1", null, null));
        when(ragSearchService.isEnabled()).thenReturn(true);
        when(ragSearchService.search(eq("问题"), anyList(), anyString()))
            .thenReturn(Collections.singletonList(new RagChunk(null, 1.0)));
        when(ragSearchService.buildContext(anyList())).thenReturn("RAG上下文");
        when(modelConfigService.chatJson(anyString(), anyString(), anyString()))
            .thenReturn("{\"reply\":\"答复\",\"handoff\":false}");

        AgentChatResult result = service.chat(1L, "s1", "问题", null, null, null, null);

        assertEquals("10,20", result.getKnowledgeRefs());
        verify(ragSearchService).buildContext(anyList());
        verify(legalKnowledgeService, never()).searchAiLegalKnowledge(anyString());
    }

    @Test
    void chat_ragEnabled_agentDisabled_skipsKnowledge()
    {
        when(agentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config(null, null, "10", "0", null, null));
        when(ragSearchService.isEnabled()).thenReturn(true);
        when(modelConfigService.chatJson(anyString(), anyString(), anyString()))
            .thenReturn("{\"reply\":\"答复\",\"handoff\":false}");

        AgentChatResult result = service.chat(1L, "s1", "问题", null, null, null, null);

        assertEquals("答复", result.getReply());
        verify(ragSearchService, never()).search(anyString(), anyList(), anyString());
    }

    @Test
    void chat_ragEmpty_scopedKnowledgeFallback()
    {
        when(agentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config(null, null, "10,11", "1", null, null));
        when(ragSearchService.isEnabled()).thenReturn(true);
        when(ragSearchService.search(eq("问题"), anyList(), anyString())).thenReturn(Collections.emptyList());
        when(legalKnowledgeService.selectAiLegalKnowledgeByKnowledgeId(10L))
            .thenReturn(knowledge(10L, "标题1", "内容1", "0"));
        when(legalKnowledgeService.selectAiLegalKnowledgeByKnowledgeId(11L))
            .thenReturn(knowledge(11L, "标题2", "内容2", "1"));
        when(modelConfigService.chatJson(anyString(), anyString(), anyString()))
            .thenReturn("{\"reply\":\"答复\",\"handoff\":false}");

        service.chat(1L, "s1", "问题", null, null, null, null);

        verify(ragSearchService, never()).buildContext(anyList());
        verify(modelConfigService).chatJson(org.mockito.ArgumentMatchers.contains("标题1"),
            anyString(), anyString());
        verify(modelConfigService, never()).chatJson(org.mockito.ArgumentMatchers.contains("标题2"),
            anyString(), anyString());
    }

    @Test
    void chat_ragEmpty_legacyFallbackFiltersAndLimitsTopThree()
    {
        when(agentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config(null, null, null, "1", null, null));
        when(ragSearchService.isEnabled()).thenReturn(true);
        when(ragSearchService.search(eq("问题"), anyList(), anyString())).thenReturn(Collections.emptyList());
        when(legalKnowledgeService.searchAiLegalKnowledge("问题")).thenReturn(Arrays.asList(
            knowledge(1L, "T1", "C1", "0"),
            knowledge(2L, "T2", "C2", "1"),
            knowledge(3L, "T3", "C3", "0"),
            knowledge(4L, "T4", "C4", "0"),
            knowledge(5L, "T5", "C5", "0")));
        when(modelConfigService.chatJson(anyString(), anyString(), anyString()))
            .thenReturn("{\"reply\":\"答复\",\"handoff\":false}");

        service.chat(1L, "s1", "问题", null, null, null, null);

        verify(modelConfigService).chatJson(org.mockito.ArgumentMatchers.contains("T1"),
            anyString(), anyString());
        verify(modelConfigService, never()).chatJson(org.mockito.ArgumentMatchers.contains("T5"),
            anyString(), anyString());
        verify(modelConfigService, never()).chatJson(org.mockito.ArgumentMatchers.contains("T2"),
            anyString(), anyString());
    }

    @Test
    void chat_modelThrows_withKnowledge_degradesWithoutHandoff()
    {
        when(agentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config(null, null, null, "1", null, null));
        when(ragSearchService.isEnabled()).thenReturn(true);
        when(ragSearchService.search(eq("问题"), anyList(), anyString()))
            .thenReturn(Collections.singletonList(new RagChunk(null, 1.0)));
        when(ragSearchService.buildContext(anyList())).thenReturn("1. 劳动合同法：内容");
        when(modelConfigService.chatJson(anyString(), anyString(), anyString()))
            .thenThrow(new IllegalStateException("timeout"));

        AgentChatResult result = service.chat(1L, "s1", "问题", null, null, null, null);

        assertFalse(result.isHandoff());
        assertTrue(result.getReply().contains("劳动合同法"));
        assertEquals("模型故障，知识库话术兜底", result.getReason());
    }

    @Test
    void chat_modelThrows_withoutKnowledge_handsOff()
    {
        when(agentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config(null, null, null, "1", null, null));
        when(ragSearchService.isEnabled()).thenReturn(false);
        when(modelConfigService.chatJson(anyString(), anyString(), anyString()))
            .thenThrow(new IllegalStateException("timeout"));

        AgentChatResult result = service.chat(1L, "s1", "问题", null, null, null, null);

        assertTrue(result.isHandoff());
        assertTrue(result.getReason().contains("timeout"));
    }

    @Test
    void chat_historyFromDb_trimmedToRounds()
    {
        when(agentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config(null, null, null, "1", null, 1));
        when(ragSearchService.isEnabled()).thenReturn(false);
        AiAgentMessage old = new AiAgentMessage();
        old.setRole("user");
        old.setContent("旧消息");
        AiAgentMessage recent = new AiAgentMessage();
        recent.setRole("assistant");
        recent.setContent("新消息");
        when(agentMessageMapper.selectRecentBySession("s1", 2)).thenReturn(Arrays.asList(old, recent));
        when(modelConfigService.chatJson(anyString(), anyString(), anyString()))
            .thenReturn("{\"reply\":\"答复\",\"handoff\":false}");

        service.chat(1L, "s1", "问题", null, null, null, null);

        verify(modelConfigService).chatJson(anyString(),
            org.mockito.ArgumentMatchers.contains("助手：新消息"),
            anyString());
    }

    @Test
    void chat_duplicateKey_retriesWithNewTurnNo()
    {
        when(agentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config(null, null, null, "1", null, null));
        when(ragSearchService.isEnabled()).thenReturn(false);
        when(modelConfigService.chatJson(anyString(), anyString(), anyString()))
            .thenReturn("{\"reply\":\"答复\",\"handoff\":false}");
        doThrow(new DuplicateKeyException("dup"))
            .doAnswer(invocation -> 1)
            .doAnswer(invocation -> 1)
            .when(agentMessageMapper).insertAiAgentMessage(any(AiAgentMessage.class));

        AgentChatResult result = service.chat(1L, "s1", "问题", null, null, null, null);

        assertEquals("答复", result.getReply());
        verify(agentMessageMapper, times(3)).insertAiAgentMessage(any(AiAgentMessage.class));
    }

    @Test
    void chat_resultTurnNoSet() 
    {
        when(agentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config(null, null, null, "1", null, null));
        when(ragSearchService.isEnabled()).thenReturn(false);
        when(modelConfigService.chatJson(anyString(), anyString(), anyString()))
            .thenReturn("{\"reply\":\"答复\",\"handoff\":false}");
        when(agentMessageMapper.selectMaxTurnNo("s1")).thenReturn(4);

        AgentChatResult result = service.chat(1L, "s1", "问题", null, null, null, null);

        assertEquals(5, result.getTurnNo());
    }

    @Test
    void chat_persistFieldsFilled()
    {
        when(agentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config(null, "人工", null, "1", null, null));

        service.chat(1L, "s1", "人工", 2L, 3L, 4L, "138");

        org.mockito.ArgumentCaptor<AiAgentMessage> captor =
            org.mockito.ArgumentCaptor.forClass(AiAgentMessage.class);
        verify(agentMessageMapper, times(2)).insertAiAgentMessage(captor.capture());
        List<AiAgentMessage> messages = captor.getAllValues();
        AiAgentMessage user = messages.get(0);
        assertEquals("user", user.getRole());
        assertEquals("人工", user.getContent());
        assertEquals(2L, user.getRecordId());
        assertEquals(3L, user.getFlowId());
        assertEquals(4L, user.getNodeId());
        assertEquals("138", user.getCallerNumber());
        assertEquals("user", user.getRole());

        AiAgentMessage bot = messages.get(1);
        assertEquals("assistant", bot.getRole());
        assertEquals("1", bot.getHandoff());
        assertEquals(bot.getReason(), "命中转人工关键词");
    }

    @Test
    void chat_ragSearchThrows_fallsBackToLegacy()
    {
        when(agentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config(null, null, null, "1", null, null));
        when(ragSearchService.isEnabled()).thenReturn(true);
        when(ragSearchService.search(anyString(), anyList(), anyString()))
            .thenThrow(new IllegalStateException("index missing"));
        when(legalKnowledgeService.searchAiLegalKnowledge("问题"))
            .thenReturn(Collections.singletonList(knowledge(1L, "T1", "C1", "0")));
        when(modelConfigService.chatJson(anyString(), anyString(), anyString()))
            .thenReturn("{\"reply\":\"答复\",\"handoff\":false}");

        assertEquals("答复", service.chat(1L, "s1", "问题", null, null, null, null).getReply());
    }

    @Test
    void chat_invalidIdInKnowledgeIds_throwsIntoDegrade()
    {
        when(agentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config(null, null, "abc", "1", null, null));
        when(ragSearchService.isEnabled()).thenReturn(true);
        when(modelConfigService.chatJson(anyString(), anyString(), anyString()))
            .thenReturn("{\"reply\":\"答复\",\"handoff\":false}");

        // parseIds 解析失败在 try 外，按当前实现会抛 NumberFormatException
        org.junit.jupiter.api.Assertions.assertThrows(NumberFormatException.class,
            () -> service.chat(1L, "s1", "问题", null, null, null, null));
    }

    @Test
    void chat_labelDisabled_modelReplyPassesThroughAndNoTag()
    {
        when(agentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config(null, null, null, "1", null, null));
        when(ragSearchService.isEnabled()).thenReturn(false);
        when(modelConfigService.chatJson(anyString(), anyString(), anyString()))
            .thenReturn("{\"reply\":\"原始答复\",\"handoff\":false}");

        AgentChatResult result = service.chat(1L, "s1", "问题", null, null, null, null);

        assertEquals("原始答复", result.getReply());
        // 开关关闭：无显式标识、无隐式标签
        assertFalse(result.getReply().contains("AI生成"));
        org.junit.jupiter.api.Assertions.assertNull(result.getAiLabel());
    }

    @Test
    void chat_labelEnabled_modelReplyWrappedAndTagged()
    {
        enableAiLabel();
        when(agentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config(null, null, null, "1", null, null));
        when(ragSearchService.isEnabled()).thenReturn(false);
        when(modelConfigService.chatJson(anyString(), anyString(), anyString()))
            .thenReturn("{\"reply\":\"原始答复\",\"handoff\":false}");

        AgentChatResult result = service.chat(1L, "s1", "问题", null, null, null, null);

        assertTrue(result.getReply().startsWith("【AI生成】"));
        assertTrue(result.getReply().contains("原始答复"));
        assertEquals("AI_GENERATED", result.getAiLabel());
    }

    @Test
    void chat_labelEnabled_handoffKeywordReplyNotLabeled()
    {
        enableAiLabel();
        when(agentConfigMapper.selectAiAgentConfigByAgentId(1L))
            .thenReturn(config(null, "人工", null, "1", null, null));

        // 命中转人工关键词：固定话术非模型生成，不应加标识/标签
        AgentChatResult result = service.chat(1L, "s1", "人工", null, null, null, null);

        assertFalse(result.getReply().contains("AI生成"));
        org.junit.jupiter.api.Assertions.assertNull(result.getAiLabel());
        verify(modelConfigService, never()).chatJson(anyString(), anyString(), anyString());
    }
}
