package ai.lawyers.web.controller.lawyers.sms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.sms.AiSmsTemplate;
import ai.lawyers.system.mapper.lawyers.sms.AiSmsTemplateMapper;

/**
 * 短信模板接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiSmsTemplateControllerTest
{
    @Mock
    private AiSmsTemplateMapper aiSmsTemplateMapper;

    @InjectMocks
    private AiSmsTemplateController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiSmsTemplate query = new AiSmsTemplate();
        List<AiSmsTemplate> templates = new ArrayList<AiSmsTemplate>();
        templates.add(new AiSmsTemplate());
        templates.add(new AiSmsTemplate());
        when(aiSmsTemplateMapper.selectAiSmsTemplateList(query)).thenReturn(templates);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(2, table.getTotal());
        assertEquals(templates, table.getRows());
    }

    @Test
    void 详情透传服务结果()
    {
        AiSmsTemplate template = new AiSmsTemplate();
        template.setTemplateId(4L);
        when(aiSmsTemplateMapper.selectAiSmsTemplateByTemplateId(4L)).thenReturn(template);

        AjaxResult result = controller.getInfo(4L);

        assertSame(template, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增写入操作人()
    {
        SysUser user = new SysUser();
        user.setUserName("sms-admin");
        AiSmsTemplate template = new AiSmsTemplate();
        when(aiSmsTemplateMapper.insertAiSmsTemplate(any(AiSmsTemplate.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(new LoginUser(9L, 10L, user, null));
            AjaxResult result = controller.add(template);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("sms-admin", template.getCreateBy());
        verify(aiSmsTemplateMapper).insertAiSmsTemplate(template);
    }

    @Test
    void 启用模板下拉仅查询启用状态()
    {
        List<AiSmsTemplate> enabled = new ArrayList<AiSmsTemplate>();
        enabled.add(new AiSmsTemplate());
        when(aiSmsTemplateMapper.selectAiSmsTemplateList(any(AiSmsTemplate.class))).thenReturn(enabled);

        AjaxResult result = controller.enabled();

        ArgumentCaptor<AiSmsTemplate> captor = ArgumentCaptor.forClass(AiSmsTemplate.class);
        verify(aiSmsTemplateMapper).selectAiSmsTemplateList(captor.capture());
        assertEquals("1", captor.getValue().getStatus());
        assertSame(enabled, result.get(AjaxResult.DATA_TAG));
    }
}
