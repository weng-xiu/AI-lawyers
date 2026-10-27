package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.domain.lawyers.AiFormDraft;
import ai.lawyers.system.mapper.lawyers.AiFormDraftMapper;

/**
 * U4 服务端草稿单测：按用户隔离、空内容等价删除、非法表单标识拒绝。
 *
 * @author ai-lawyers
 */
class AiFormDraftServiceImplTest
{
    @Mock
    private AiFormDraftMapper formDraftMapper;

    private AiFormDraftServiceImpl service;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiFormDraftServiceImpl();
        ReflectionTestUtils.setField(service, "formDraftMapper", formDraftMapper);
    }

    @Test
    void selectDraftTrimsKeyAndScopesToUser()
    {
        AiFormDraft draft = new AiFormDraft();
        when(formDraftMapper.selectDraft(7L, "workOrder:admin")).thenReturn(draft);

        assertEquals(draft, service.selectDraft(7L, " workOrder:admin "));
        verify(formDraftMapper).selectDraft(7L, "workOrder:admin");
    }

    @Test
    void saveDraftUpsertsWithUserAndTimestamp()
    {
        service.saveDraft(7L, " workOrder:admin ", "{\"ts\":1,\"data\":{\"title\":\"咨询\"}}");

        ArgumentCaptor<AiFormDraft> captor = ArgumentCaptor.forClass(AiFormDraft.class);
        verify(formDraftMapper).upsertDraft(captor.capture());
        AiFormDraft saved = captor.getValue();
        assertEquals(7L, saved.getUserId());
        assertEquals("workOrder:admin", saved.getFormKey());
        assertEquals("{\"ts\":1,\"data\":{\"title\":\"咨询\"}}", saved.getContent());
        assertNotNull(saved.getUpdateTime());
    }

    @Test
    void saveEmptyContentDeletesDraftInsteadOfKeepingBlank()
    {
        service.saveDraft(7L, "workOrder:admin", "   ");

        verify(formDraftMapper).deleteDraft(7L, "workOrder:admin");
        verify(formDraftMapper, never()).upsertDraft(any());
    }

    @Test
    void blankOrOversizedFormKeyIsRejected()
    {
        assertThrows(ServiceException.class, () -> service.selectDraft(7L, ""));
        assertThrows(ServiceException.class, () -> service.saveDraft(7L, null, "x"));
        assertThrows(ServiceException.class, () -> service.deleteDraft(7L, repeat("k", 129)));
    }

    @Test
    void missingUserIsRejected()
    {
        assertThrows(ServiceException.class, () -> service.selectDraft(null, "workOrder:admin"));
    }

    @Test
    void missingDraftReturnsNull()
    {
        when(formDraftMapper.selectDraft(eq(7L), eq("knowledge:admin"))).thenReturn(null);
        assertNull(service.selectDraft(7L, "knowledge:admin"));
    }

    private static String repeat(String s, int times)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++)
        {
            sb.append(s);
        }
        return sb.toString();
    }
}
