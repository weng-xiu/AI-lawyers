package ai.lawyers.system.service.lawyers.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiUserConsultation;
import ai.lawyers.system.domain.lawyers.AiUserEvaluation;
import ai.lawyers.system.mapper.lawyers.AiUserConsultationMapper;
import ai.lawyers.system.mapper.lawyers.AiUserEvaluationMapper;

/**
 * F10 公众端补充材料 / 评价状态单测：归属校验、状态校验（仅 COMPLETED）、
 * 附件与说明至少一项、原附件/原说明追加合并、附件白名单与数量上限、已评价判定。
 */
class AiUserConsultationServiceImplTest
{
    private static final Long USER_ID = 1L;

    private static final Long CONSULTATION_ID = 8L;

    @Mock
    private AiUserConsultationMapper aiUserConsultationMapper;

    @Mock
    private AiUserEvaluationMapper aiUserEvaluationMapper;

    @TempDir
    Path tempDir;

    private AiUserConsultationServiceImpl service;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiUserConsultationServiceImpl();
        ReflectionTestUtils.setField(service, "aiUserConsultationMapper", aiUserConsultationMapper);
        ReflectionTestUtils.setField(service, "aiUserEvaluationMapper", aiUserEvaluationMapper);
        ReflectionTestUtils.setField(service, "contentSafetyService", new ContentSafetyService());
        ReflectionTestUtils.setField(service, "filePath", tempDir.toString());
        securityUtilsMock = Mockito.mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getUserId).thenReturn(USER_ID);
        securityUtilsMock.when(SecurityUtils::getUsername).thenReturn("portal-user");
    }

    @AfterEach
    void tearDown()
    {
        securityUtilsMock.close();
    }

    @Test
    void supplementRejectsOtherUsersConsultation()
    {
        when(aiUserConsultationMapper.selectAiUserConsultationById(CONSULTATION_ID))
                .thenReturn(consultation("COMPLETED", 99L, null, null));

        assertThrows(ServiceException.class,
                () -> service.supplementOwnConsultation(CONSULTATION_ID, null, "补充说明"));
        verify(aiUserConsultationMapper, never()).updateAiUserConsultation(any());
    }

    @Test
    void supplementRejectsProcessingOrFailedConsultation()
    {
        when(aiUserConsultationMapper.selectAiUserConsultationById(CONSULTATION_ID))
                .thenReturn(consultation("PROCESSING", USER_ID, null, null));
        assertThrows(ServiceException.class,
                () -> service.supplementOwnConsultation(CONSULTATION_ID, null, "补充说明"));

        when(aiUserConsultationMapper.selectAiUserConsultationById(CONSULTATION_ID))
                .thenReturn(consultation("FAILED", USER_ID, null, null));
        assertThrows(ServiceException.class,
                () -> service.supplementOwnConsultation(CONSULTATION_ID, null, "补充说明"));

        verify(aiUserConsultationMapper, never()).updateAiUserConsultation(any());
    }

    @Test
    void supplementRequiresNoteOrFile()
    {
        when(aiUserConsultationMapper.selectAiUserConsultationById(CONSULTATION_ID))
                .thenReturn(consultation("COMPLETED", USER_ID, null, null));

        assertThrows(ServiceException.class,
                () -> service.supplementOwnConsultation(CONSULTATION_ID, null, "   "));
        // 空文件列表同样视为未提供材料
        MultipartFile empty = new MockMultipartFile("files", "a.pdf", "application/pdf", new byte[0]);
        assertThrows(ServiceException.class,
                () -> service.supplementOwnConsultation(CONSULTATION_ID, Collections.singletonList(empty), null));
        verify(aiUserConsultationMapper, never()).updateAiUserConsultation(any());
    }

    @Test
    void supplementNoteOnlyMergesRemarkWithoutAttachments()
    {
        AiUserConsultation existed = consultation("COMPLETED", USER_ID, "/consultation/origin.pdf", "原说明");
        AiUserConsultation latest = consultation("COMPLETED", USER_ID, "/consultation/origin.pdf",
                "原说明；补充说明");
        when(aiUserConsultationMapper.selectAiUserConsultationById(CONSULTATION_ID)).thenReturn(existed, latest);

        assertEquals(latest, service.supplementOwnConsultation(CONSULTATION_ID, null, " 补充说明 "));

        AiUserConsultation update = captureUpdate();
        assertEquals("原说明；补充说明", update.getRemark());
        assertNull(update.getAttachments());
        assertEquals("portal-user", update.getUpdateBy());
    }

    @Test
    void supplementFileAndNoteAppendsToExistingAttachments()
    {
        AiUserConsultation existed = consultation("COMPLETED", USER_ID, "/consultation/origin.pdf", "原说明");
        AiUserConsultation latest = consultation("COMPLETED", USER_ID, null, null);
        when(aiUserConsultationMapper.selectAiUserConsultationById(CONSULTATION_ID)).thenReturn(existed, latest);

        MultipartFile file = new MockMultipartFile("files", "凭证.pdf", "application/pdf",
                "hello".getBytes());

        assertEquals(latest, service.supplementOwnConsultation(CONSULTATION_ID,
                Collections.singletonList(file), "补充说明"));

        AiUserConsultation update = captureUpdate();
        assertTrue(update.getAttachments().startsWith("/consultation/origin.pdf,/consultation/"),
                "原附件必须保留并追加新附件");
        assertEquals("原说明；补充说明", update.getRemark());
        assertTrue(java.nio.file.Files.exists(tempDir.resolve("consultation")),
                "附件应落入配置的上传目录");
    }

    @Test
    void supplementRejectsUnsupportedExtensionAndTooManyFiles()
    {
        when(aiUserConsultationMapper.selectAiUserConsultationById(CONSULTATION_ID))
                .thenReturn(consultation("COMPLETED", USER_ID, null, null));

        MultipartFile bad = new MockMultipartFile("files", "a.exe", "application/octet-stream",
                "x".getBytes());
        assertThrows(ServiceException.class,
                () -> service.supplementOwnConsultation(CONSULTATION_ID,
                        Collections.singletonList(bad), null));

        List<MultipartFile> tooMany = java.util.Arrays.asList(
                new MockMultipartFile("files", "1.pdf", "application/pdf", "1".getBytes()),
                new MockMultipartFile("files", "2.pdf", "application/pdf", "2".getBytes()),
                new MockMultipartFile("files", "3.pdf", "application/pdf", "3".getBytes()),
                new MockMultipartFile("files", "4.pdf", "application/pdf", "4".getBytes()));
        assertThrows(ServiceException.class,
                () -> service.supplementOwnConsultation(CONSULTATION_ID, tooMany, null));

        verify(aiUserConsultationMapper, never()).updateAiUserConsultation(any());
    }

    @Test
    void evaluatedReturnsFalseWhenNoEvaluation()
    {
        when(aiUserConsultationMapper.selectAiUserConsultationById(CONSULTATION_ID))
                .thenReturn(consultation("COMPLETED", USER_ID, null, null));
        when(aiUserEvaluationMapper.selectAiUserEvaluationList(any()))
                .thenReturn(Collections.emptyList());

        assertFalse(service.isOwnConsultationEvaluated(CONSULTATION_ID));
    }

    @Test
    void evaluatedReturnsTrueWhenEvaluationExists()
    {
        when(aiUserConsultationMapper.selectAiUserConsultationById(CONSULTATION_ID))
                .thenReturn(consultation("COMPLETED", USER_ID, null, null));
        AiUserEvaluation evaluation = new AiUserEvaluation();
        evaluation.setEvaluationId(1L);
        when(aiUserEvaluationMapper.selectAiUserEvaluationList(any()))
                .thenReturn(Collections.singletonList(evaluation));

        assertTrue(service.isOwnConsultationEvaluated(CONSULTATION_ID));
    }

    private AiUserConsultation captureUpdate()
    {
        ArgumentCaptor<AiUserConsultation> captor = ArgumentCaptor.forClass(AiUserConsultation.class);
        verify(aiUserConsultationMapper).updateAiUserConsultation(captor.capture());
        return captor.getValue();
    }

    private static AiUserConsultation consultation(String status, Long userId, String attachments,
                                                   String remark)
    {
        AiUserConsultation consultation = new AiUserConsultation();
        consultation.setConsultationId(CONSULTATION_ID);
        consultation.setUserId(userId);
        consultation.setStatus(status);
        consultation.setAttachments(attachments);
        consultation.setRemark(remark);
        return consultation;
    }
}
