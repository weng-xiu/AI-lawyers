package ai.lawyers.admin.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.system.domain.lawyers.AiUserConsultation;
import ai.lawyers.system.domain.lawyers.AiUserEvaluation;
import ai.lawyers.system.service.lawyers.IAiUserConsultationService;

/**
 * 用户咨询接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiUserConsultationControllerTest
{
    @Mock
    private IAiUserConsultationService aiUserConsultationService;

    @InjectMocks
    private AiUserConsultationController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private AiUserConsultation consultation(Long id)
    {
        AiUserConsultation consultation = new AiUserConsultation();
        consultation.setConsultationId(id);
        consultation.setCategory("合同纠纷");
        consultation.setContent("合同到期不续签是否有补偿");
        return consultation;
    }

    @Test
    void 提交咨询携带附件与渠道()
    {
        MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();
        request.addParameter("category", "合同纠纷");
        request.addParameter("content", "合同到期不续签是否有补偿");
        request.addParameter("channelType", "WECHAT_MINI");
        request.addFile(new MockMultipartFile("files", "evidence.pdf", "application/pdf", "pdf".getBytes()));
        AiUserConsultation consultation = consultation(1L);
        when(aiUserConsultationService.submitConsultation(eq("合同纠纷"), eq("合同到期不续签是否有补偿"),
                anyList(), eq("WECHAT_MINI"))).thenReturn(consultation);

        AjaxResult result = controller.submitConsultation(request);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        assertEquals("咨询提交成功", result.get(AjaxResult.MSG_TAG));
        assertSame(consultation, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 提交咨询异常转为失败提示()
    {
        MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();
        when(aiUserConsultationService.submitConsultation(any(), any(), anyList(), any()))
                .thenThrow(new RuntimeException("附件解析失败"));

        AjaxResult result = controller.submitConsultation(request);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("咨询提交失败：附件解析失败", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void 咨询结果与信息查询()
    {
        AiUserConsultation consultation = consultation(1L);
        when(aiUserConsultationService.selectAiUserConsultationById(1L)).thenReturn(consultation);
        when(aiUserConsultationService.selectAiUserConsultationById(2L)).thenReturn(null);

        assertSame(consultation, controller.getConsultationResult(1L).get(AjaxResult.DATA_TAG));
        assertEquals("咨询记录不存在", controller.getConsultationResult(2L).get(AjaxResult.MSG_TAG));
        assertSame(consultation, controller.getConsultationInfo(1L).get(AjaxResult.DATA_TAG));
        assertEquals("咨询记录不存在", controller.getConsultationInfo(2L).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 咨询历史分页返回()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiUserConsultation query = new AiUserConsultation();
        List<AiUserConsultation> list = new ArrayList<AiUserConsultation>();
        list.add(consultation(1L));
        when(aiUserConsultationService.selectAiUserConsultationList(query)).thenReturn(list);

        TableDataInfo table = controller.getConsultationHistory(query);

        assertEquals(200, table.getCode());
        assertSame(list, table.getRows());
    }

    @Test
    void 提交评价成功与失败()
    {
        AiUserEvaluation evaluation = new AiUserEvaluation();
        evaluation.setConsultationId(1L);
        when(aiUserConsultationService.submitEvaluation(evaluation)).thenReturn(1);
        AiUserEvaluation failed = new AiUserEvaluation();
        failed.setConsultationId(2L);
        when(aiUserConsultationService.submitEvaluation(failed)).thenThrow(new RuntimeException("已评价"));

        AjaxResult ok = controller.submitEvaluation(evaluation);
        assertEquals(200, ok.get(AjaxResult.CODE_TAG));
        assertEquals("评价提交成功", ok.get(AjaxResult.MSG_TAG));

        AjaxResult error = controller.submitEvaluation(failed);
        assertEquals(500, error.get(AjaxResult.CODE_TAG));
        assertTrue(String.valueOf(error.get(AjaxResult.MSG_TAG)).startsWith("评价提交失败："));
    }

    @Test
    void 问题分类与删除咨询()
    {
        List<Map<String, Object>> categories = new ArrayList<Map<String, Object>>();
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("name", "合同纠纷");
        categories.add(item);
        when(aiUserConsultationService.getQuestionCategories()).thenReturn(categories);
        when(aiUserConsultationService.deleteAiUserConsultationById(1L)).thenReturn(1);

        assertSame(categories, controller.getQuestionCategories().get(AjaxResult.DATA_TAG));
        assertEquals(200, controller.remove(1L).get(AjaxResult.CODE_TAG));
    }
}
