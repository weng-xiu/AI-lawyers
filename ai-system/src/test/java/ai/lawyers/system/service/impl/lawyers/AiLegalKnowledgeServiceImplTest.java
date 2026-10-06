package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.mapper.SysUserMapper;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeMapper;
import ai.lawyers.system.service.lawyers.queue.MessageNotifyDispatcher;
import ai.lawyers.system.service.lawyers.rag.IRagIndexService;

/**
 * P1-10：{@link AiLegalKnowledgeServiceImpl#markValidStatus} 效力标记单测——
 * 合法标记触发落库 + RAG 单条重建 + 管理员站内信；非法状态/空ID/不存在一律拒绝且不落库。
 *
 * @author ai-lawyers
 */
class AiLegalKnowledgeServiceImplTest
{
    private static final Long KID = 50L;

    private AiLegalKnowledgeServiceImpl service;

    @Mock private AiLegalKnowledgeMapper knowledgeMapper;
    @Mock private IRagIndexService ragIndexService;
    @Mock private MessageNotifyDispatcher messageNotifyDispatcher;
    @Mock private SysUserMapper sysUserMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiLegalKnowledgeServiceImpl();
        ReflectionTestUtils.setField(service, "aiLegalKnowledgeMapper", knowledgeMapper);
        ReflectionTestUtils.setField(service, "ragIndexService", ragIndexService);
        ReflectionTestUtils.setField(service, "messageNotifyDispatcher", messageNotifyDispatcher);
        ReflectionTestUtils.setField(service, "sysUserMapper", sysUserMapper);
    }

    private AiLegalKnowledge givenKnowledge()
    {
        AiLegalKnowledge k = new AiLegalKnowledge();
        k.setKnowledgeId(KID);
        k.setTitle("中华人民共和国民法典");
        k.setValidStatus("1");
        return k;
    }

    @Test
    void markValidStatus_validInvalid_updatesAndRebuildsAndNotifiesAdmins()
    {
        when(knowledgeMapper.selectAiLegalKnowledgeById(KID)).thenReturn(givenKnowledge());
        when(knowledgeMapper.updateAiLegalKnowledge(any())).thenReturn(1);
        when(sysUserMapper.selectUserIdsByRoleKey("admin")).thenReturn(Arrays.asList(1L, 2L));

        int rows = service.markValidStatus(KID, "3", "新法已取代", "admin01");

        assertEquals(1, rows);
        verify(knowledgeMapper).updateAiLegalKnowledge(any());
        // 失效立即触发 RAG 单条重建（内部已按 valid_status 过滤，移出向量索引）
        verify(ragIndexService).rebuildKnowledgeAsync(KID);
        // 两名管理员各发一条效力变更站内信
        verify(messageNotifyDispatcher).notify(eq(1L), eq("1"), any(), any(),
                eq("knowledge"), eq(KID), eq("system"));
        verify(messageNotifyDispatcher).notify(eq(2L), eq("1"), any(), any(),
                eq("knowledge"), eq(KID), eq("system"));
    }

    @Test
    void markValidStatus_nullId_throwsNoUpdate()
    {
        assertThrows(ServiceException.class,
                () -> service.markValidStatus(null, "3", null, "admin01"));
        verify(knowledgeMapper, never()).updateAiLegalKnowledge(any());
        verify(ragIndexService, never()).rebuildKnowledgeAsync(any());
    }

    @Test
    void markValidStatus_illegalStatus_throwsNoUpdate()
    {
        assertThrows(ServiceException.class,
                () -> service.markValidStatus(KID, "9", null, "admin01"));
        verify(knowledgeMapper, never()).updateAiLegalKnowledge(any());
        verify(ragIndexService, never()).rebuildKnowledgeAsync(any());
    }

    @Test
    void markValidStatus_notExists_throwsNoUpdate()
    {
        when(knowledgeMapper.selectAiLegalKnowledgeById(KID)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> service.markValidStatus(KID, "2", null, "admin01"));
        verify(knowledgeMapper, never()).updateAiLegalKnowledge(any());
        verify(ragIndexService, never()).rebuildKnowledgeAsync(any());
    }

    @Test
    void markValidStatus_updateReturnsZero_skipsRebuildAndNotify()
    {
        when(knowledgeMapper.selectAiLegalKnowledgeById(KID)).thenReturn(givenKnowledge());
        when(knowledgeMapper.updateAiLegalKnowledge(any())).thenReturn(0);

        int rows = service.markValidStatus(KID, "2", null, "admin01");

        assertEquals(0, rows);
        verify(ragIndexService, never()).rebuildKnowledgeAsync(any());
        verify(messageNotifyDispatcher, never()).notify(any(), any(), any(), any(),
                any(), any(), any());
    }

    @Test
    void markValidStatus_notifyFailure_doesNotBreakMainFlow()
    {
        when(knowledgeMapper.selectAiLegalKnowledgeById(KID)).thenReturn(givenKnowledge());
        when(knowledgeMapper.updateAiLegalKnowledge(any())).thenReturn(1);
        when(sysUserMapper.selectUserIdsByRoleKey("admin"))
                .thenReturn(Collections.singletonList(1L));
        // 通知抛异常不应导致主方法失败
        org.mockito.Mockito.doThrow(new RuntimeException("ws down"))
                .when(messageNotifyDispatcher)
                .notify(any(), any(), any(), any(), any(), any(), any());

        int rows = service.markValidStatus(KID, "3", null, "admin01");

        assertEquals(1, rows);
        verify(ragIndexService).rebuildKnowledgeAsync(KID);
    }
}
