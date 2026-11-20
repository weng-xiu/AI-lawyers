package ai.lawyers.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiUserConsultation;
import ai.lawyers.system.domain.lawyers.AiUserEvaluation;
import ai.lawyers.system.service.lawyers.IAiUserConsultationService;

/**
 * 公众端法律咨询接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiUserConsultationPortalControllerTest
{
    @Mock
    private IAiUserConsultationService consultationService;

    @InjectMocks
    private AiUserConsultationPortalController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private AiUserConsultation consultation(Long id)
    {
        AiUserConsultation consultation = new AiUserConsultation();
        consultation.setConsultationId(id);
        consultation.setUserId(1L);
        consultation.setCategory("合同纠纷");
        return consultation;
    }

    private MockMultipartHttpServletRequest multipart(String... pairs)
    {
        MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();
        for (int i = 0; i + 1 < pairs.length; i += 2)
        {
            request.addParameter(pairs[i], pairs[i + 1]);
        }
        return request;
    }

    @Test
    void 公众端提交咨询透传渠道与附件()
    {
        MockMultipartHttpServletRequest request = multipart("category", "合同纠纷", "content", "合同到期补偿",
                "channelType", "H5");
        request.addFile(new MockMultipartFile("files", "a.pdf", "application/pdf", "pdf".getBytes()));
        AiUserConsultation consultation = consultation(1L);
        when(consultationService.submitConsultation(eq("合同纠纷"), eq("合同到期补偿"), anyList(), eq("H5")))
                .thenReturn(consultation);

        AjaxResult result = controller.submit(request);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        assertEquals("咨询提交成功", result.get(AjaxResult.MSG_TAG));
        assertSame(consultation, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 本人咨询结果与详情()
    {
        AiUserConsultation consultation = consultation(1L);
        when(consultationService.selectOwnConsultationById(1L)).thenReturn(consultation);

        assertSame(consultation, controller.result(1L).get(AjaxResult.DATA_TAG));
        assertSame(consultation, controller.info(1L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 本人咨询被越权访问时抛出业务异常()
    {
        when(consultationService.selectOwnConsultationById(9L))
                .thenThrow(new ServiceException("咨询记录不存在或无权查看"));

        ServiceException exception = assertThrows(ServiceException.class, () -> controller.result(9L));

        assertEquals("咨询记录不存在或无权查看", exception.getMessage());
    }

    @Test
    void 咨询历史强制按登录用户过滤()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiUserConsultation query = new AiUserConsultation();
        query.setUserId(99L);
        List<AiUserConsultation> list = new ArrayList<AiUserConsultation>();
        list.add(consultation(1L));
        when(consultationService.selectAiUserConsultationList(any(AiUserConsultation.class))).thenReturn(list);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            TableDataInfo table = controller.history(query);
            assertEquals(200, table.getCode());
            assertSame(list, table.getRows());
        }
        ArgumentCaptor<AiUserConsultation> captor = ArgumentCaptor.forClass(AiUserConsultation.class);
        verify(consultationService).selectAiUserConsultationList(captor.capture());
        assertEquals(1L, captor.getValue().getUserId());
    }

    @Test
    void 提交评价与已评价状态()
    {
        AiUserEvaluation evaluation = new AiUserEvaluation();
        evaluation.setConsultationId(1L);
        when(consultationService.isOwnConsultationEvaluated(1L)).thenReturn(true);

        AjaxResult result = controller.evaluation(evaluation);
        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        assertEquals("评价提交成功", result.get(AjaxResult.MSG_TAG));
        verify(consultationService).submitOwnEvaluation(evaluation);
        assertEquals(Boolean.TRUE, controller.evaluated(1L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 补充咨询材料()
    {
        MockMultipartHttpServletRequest request = multipart("note", "补充劳动合同照片");
        request.addFile(new MockMultipartFile("files", "b.jpg", "image/jpeg", "jpg".getBytes()));
        AiUserConsultation consultation = consultation(1L);
        when(consultationService.supplementOwnConsultation(eq(1L), anyList(), eq("补充劳动合同照片")))
                .thenReturn(consultation);

        AjaxResult result = controller.supplement(1L, request);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        assertEquals("材料补充成功", result.get(AjaxResult.MSG_TAG));
        assertSame(consultation, result.get(AjaxResult.DATA_TAG));
    }
}
