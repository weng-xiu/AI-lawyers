package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.domain.lawyers.AiKnowledgeAudit;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.mapper.lawyers.AiKnowledgeAuditMapper;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeMapper;

/**
 * 法律知识审核发布服务单测。
 *
 * <p>覆盖审核列表、提交/通过/驳回/发布/下架的状态流转与审核日志落库，
 * 以及知识不存在的校验。</p>
 *
 * @author ai-lawyers
 */
class AiKnowledgeAuditServiceImplTest
{
    private AiKnowledgeAuditServiceImpl service;

    @Mock private AiKnowledgeAuditMapper auditMapper;
    @Mock private AiLegalKnowledgeMapper knowledgeMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiKnowledgeAuditServiceImpl();
        ReflectionTestUtils.setField(service, "auditMapper", auditMapper);
        ReflectionTestUtils.setField(service, "knowledgeMapper", knowledgeMapper);
    }

    private static AiLegalKnowledge knowledge(String auditStatus, String status, Integer publishVersion)
    {
        AiLegalKnowledge knowledge = new AiLegalKnowledge();
        knowledge.setAuditStatus(auditStatus);
        knowledge.setStatus(status);
        knowledge.setPublishVersion(publishVersion);
        return knowledge;
    }

    private AiKnowledgeAudit captureLog()
    {
        ArgumentCaptor<AiKnowledgeAudit> captor = ArgumentCaptor.forClass(AiKnowledgeAudit.class);
        verify(auditMapper).insertAiKnowledgeAudit(captor.capture());
        return captor.getValue();
    }

    @Test
    void selectAuditList_delegates()
    {
        AiKnowledgeAudit query = new AiKnowledgeAudit();
        List<AiKnowledgeAudit> list = Collections.singletonList(new AiKnowledgeAudit());
        when(auditMapper.selectAiKnowledgeAuditList(query)).thenReturn(list);

        assertSame(list, service.selectAuditList(query));
    }

    @Test
    void submit_writesStateAndLog()
    {
        when(knowledgeMapper.selectAiLegalKnowledgeById(1L)).thenReturn(knowledge("2", "1", 3));

        service.submit(1L, "admin");

        verify(auditMapper).updateKnowledgeAuditState(1L, "0", "1", "admin", "提交审核");
        AiKnowledgeAudit log = captureLog();
        assertEquals("SUBMIT", log.getAction());
        assertEquals("2", log.getFromStatus());
        assertEquals("0", log.getToStatus());
        assertEquals(3, log.getPublishVersion());
        assertEquals("admin", log.getCreateBy());
        assertNotNull(log.getAuditTime());
    }

    @Test
    void submit_missingKnowledgeId_throws()
    {
        assertEquals("知识ID不能为空",
            assertThrows(ServiceException.class, () -> service.submit(null, "admin")).getMessage());
    }

    @Test
    void submit_knowledgeNotFound_throws()
    {
        when(knowledgeMapper.selectAiLegalKnowledgeById(1L)).thenReturn(null);

        assertEquals("知识条目不存在",
            assertThrows(ServiceException.class, () -> service.submit(1L, "admin")).getMessage());
    }

    @Test
    void approve_pending_updatesAndLogs()
    {
        when(knowledgeMapper.selectAiLegalKnowledgeById(1L)).thenReturn(knowledge("0", "0", 1));

        service.approve(1L, "通过", "auditor");

        verify(auditMapper).updateKnowledgeAuditState(1L, "1", "0", "auditor", "通过");
        AiKnowledgeAudit log = captureLog();
        assertEquals("APPROVE", log.getAction());
        assertEquals("0", log.getFromStatus());
        assertEquals("1", log.getToStatus());
        assertEquals("通过", log.getAuditOpinion());
    }

    @Test
    void approve_notPending_throws()
    {
        when(knowledgeMapper.selectAiLegalKnowledgeById(1L)).thenReturn(knowledge("1", "0", 1));

        assertEquals("仅待审核状态的知识可以审核通过",
            assertThrows(ServiceException.class, () -> service.approve(1L, "通过", "auditor")).getMessage());
        verify(auditMapper, never()).insertAiKnowledgeAudit(any());
    }

    @Test
    void reject_pending_updatesAndLogs()
    {
        when(knowledgeMapper.selectAiLegalKnowledgeById(1L)).thenReturn(knowledge("0", "0", 1));

        service.reject(1L, "不合规", "auditor");

        verify(auditMapper).updateKnowledgeAuditState(1L, "2", "0", "auditor", "不合规");
        AiKnowledgeAudit log = captureLog();
        assertEquals("REJECT", log.getAction());
        assertEquals("2", log.getToStatus());
    }

    @Test
    void reject_notPending_throws()
    {
        when(knowledgeMapper.selectAiLegalKnowledgeById(1L)).thenReturn(knowledge(null, "0", 1));

        assertEquals("仅待审核状态的知识可以驳回",
            assertThrows(ServiceException.class, () -> service.reject(1L, "不合规", "auditor")).getMessage());
    }

    @Test
    void publish_rejected_throws()
    {
        when(knowledgeMapper.selectAiLegalKnowledgeById(1L)).thenReturn(knowledge("2", "0", 1));

        assertEquals("审核驳回的知识不能发布，请重新提交审核",
            assertThrows(ServiceException.class, () -> service.publish(1L, "发布", "auditor")).getMessage());
        verify(auditMapper, never()).incrementPublishVersion(any());
    }

    @Test
    void publish_normal_incrementsVersionAndLogs()
    {
        when(knowledgeMapper.selectAiLegalKnowledgeById(1L)).thenReturn(knowledge("1", "0", 4));

        service.publish(1L, "发布", "auditor");

        verify(auditMapper).updateKnowledgeAuditState(1L, "1", "0", "auditor", "发布");
        verify(auditMapper).incrementPublishVersion(1L);
        AiKnowledgeAudit log = captureLog();
        assertEquals("PUBLISH", log.getAction());
        assertEquals(5, log.getPublishVersion());
        assertEquals("PUBLISHED", log.getToStatus());
    }

    @Test
    void publish_nullVersion_usesOne()
    {
        when(knowledgeMapper.selectAiLegalKnowledgeById(1L)).thenReturn(knowledge("1", "0", null));

        service.publish(1L, "发布", "auditor");

        assertEquals(1, captureLog().getPublishVersion());
    }

    @Test
    void offline_updatesStatusAndLogs()
    {
        when(knowledgeMapper.selectAiLegalKnowledgeById(1L)).thenReturn(knowledge(null, "0", 2));

        service.offline(1L, "下架", "auditor");

        verify(auditMapper).updateKnowledgeAuditState(1L, "1", "1", "auditor", "下架");
        AiKnowledgeAudit log = captureLog();
        assertEquals("OFFLINE", log.getAction());
        assertEquals("OFFLINE", log.getToStatus());
    }

    @Test
    void offline_keepsAuditStatusWhenPresent()
    {
        when(knowledgeMapper.selectAiLegalKnowledgeById(1L)).thenReturn(knowledge("1", "1", 2));

        service.offline(1L, "下架", "auditor");

        verify(auditMapper).updateKnowledgeAuditState(1L, "1", "1", "auditor", "下架");
    }
}
