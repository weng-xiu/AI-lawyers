package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiLegalConsultation;
import ai.lawyers.system.service.lawyers.IAiLegalConsultationService;

/**
 * 法律咨询记录接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiLegalConsultationControllerTest
{
    @Mock
    private IAiLegalConsultationService aiLegalConsultationService;

    @InjectMocks
    private AiLegalConsultationController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private LoginUser loginUser(String username)
    {
        SysUser user = new SysUser();
        user.setUserName(username);
        return new LoginUser(1L, 2L, user, null);
    }

    private AiLegalConsultation consultation(Long consultationId)
    {
        AiLegalConsultation consultation = new AiLegalConsultation();
        consultation.setConsultationId(consultationId);
        consultation.setUserId(3L);
        consultation.setQuestion("劳动合同到期不续签有补偿吗");
        consultation.setCategoryId(2L);
        return consultation;
    }

    @Test
    void 咨询列表分页返回()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiLegalConsultation query = new AiLegalConsultation();
        List<AiLegalConsultation> list = new ArrayList<AiLegalConsultation>();
        list.add(consultation(5L));
        when(aiLegalConsultationService.selectAiLegalConsultationList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertSame(list, table.getRows());
    }

    @Test
    void 导出咨询记录Excel()
    {
        when(aiLegalConsultationService.selectAiLegalConsultationList(any(AiLegalConsultation.class)))
                .thenReturn(List.of(consultation(5L)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiLegalConsultation());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 咨询详情与用户历史记录()
    {
        AiLegalConsultation detail = consultation(5L);
        List<AiLegalConsultation> history = List.of(detail);
        when(aiLegalConsultationService.selectAiLegalConsultationByConsultationId(5L)).thenReturn(detail);
        when(aiLegalConsultationService.selectAiLegalConsultationByUserId(3L)).thenReturn(history);

        assertSame(detail, controller.getInfo(5L).get(AjaxResult.DATA_TAG));
        assertSame(history, controller.getUserConsultations(3L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增咨询生成AI回答并写创建人()
    {
        AiLegalConsultation consultation = consultation(null);
        when(aiLegalConsultationService.generateLegalAnswer("劳动合同到期不续签有补偿吗", 2L))
                .thenReturn("可以主张经济补偿，按工作年限计算。");
        when(aiLegalConsultationService.insertAiLegalConsultation(consultation)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("portal"));
            assertEquals(200, controller.add(consultation).get(AjaxResult.CODE_TAG));
        }
        assertEquals("portal", consultation.getCreateBy());
        assertEquals("可以主张经济补偿，按工作年限计算。", consultation.getAnswer());
        assertEquals("0", consultation.getStatus());

        ArgumentCaptor<AiLegalConsultation> captor = ArgumentCaptor.forClass(AiLegalConsultation.class);
        org.mockito.Mockito.verify(aiLegalConsultationService).insertAiLegalConsultation(captor.capture());
        assertSame(consultation, captor.getValue());
    }

    @Test
    void 修改与删除咨询记录()
    {
        AiLegalConsultation consultation = consultation(5L);
        Long[] ids = new Long[] { 5L };
        when(aiLegalConsultationService.updateAiLegalConsultation(consultation)).thenReturn(1);
        when(aiLegalConsultationService.deleteAiLegalConsultationByConsultationIds(ids)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            assertEquals(200, controller.edit(consultation).get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", consultation.getUpdateBy());
        assertEquals(200, controller.remove(ids).get(AjaxResult.CODE_TAG));
    }

    @Test
    void 咨询统计接口透传()
    {
        Map<String, Object> statistics = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> byCategory = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> byDate = new ArrayList<Map<String, Object>>();
        Map<String, Object> channelStats = new LinkedHashMap<String, Object>();
        when(aiLegalConsultationService.getConsultationStatistics()).thenReturn(statistics);
        when(aiLegalConsultationService.getConsultationByCategory()).thenReturn(byCategory);
        when(aiLegalConsultationService.getConsultationByDate(7)).thenReturn(byDate);
        when(aiLegalConsultationService.getConsultationChannelStats()).thenReturn(channelStats);

        assertSame(statistics, controller.getStatistics().get(AjaxResult.DATA_TAG));
        assertSame(byCategory, controller.getStatisticsByCategory().get(AjaxResult.DATA_TAG));
        assertSame(byDate, controller.getStatisticsByDate(7).get(AjaxResult.DATA_TAG));
        assertSame(channelStats, controller.getChannelStats().get(AjaxResult.DATA_TAG));
    }
}
