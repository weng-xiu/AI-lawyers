package ai.lawyers.web.controller.lawyers.ivr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import ai.lawyers.system.domain.lawyers.ivr.AiIvrIntention;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrIntentionService;

/**
 * IVR 意图定义接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiIvrIntentionControllerTest
{
    @Mock
    private IAiIvrIntentionService aiIvrIntentionService;

    @InjectMocks
    private AiIvrIntentionController controller;

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

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiIvrIntention query = new AiIvrIntention();
        List<AiIvrIntention> intentions = new ArrayList<AiIvrIntention>();
        intentions.add(new AiIvrIntention());
        when(aiIvrIntentionService.selectAiIvrIntentionList(query)).thenReturn(intentions);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(intentions, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        AiIvrIntention intention = new AiIvrIntention();
        intention.setIntentionName("退款咨询");
        when(aiIvrIntentionService.selectAiIvrIntentionList(any(AiIvrIntention.class)))
                .thenReturn(List.of(intention));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiIvrIntention());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情透传服务结果()
    {
        AiIvrIntention intention = new AiIvrIntention();
        intention.setIntentionId(6L);
        when(aiIvrIntentionService.selectAiIvrIntentionByIntentionId(6L)).thenReturn(intention);

        AjaxResult result = controller.getInfo(6L);

        assertSame(intention, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增写入操作人()
    {
        AiIvrIntention intention = new AiIvrIntention();
        when(aiIvrIntentionService.insertAiIvrIntention(any(AiIvrIntention.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            AjaxResult result = controller.add(intention);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", intention.getCreateBy());
    }

    @Test
    void 修改写入更新人()
    {
        AiIvrIntention intention = new AiIvrIntention();
        when(aiIvrIntentionService.updateAiIvrIntention(any(AiIvrIntention.class))).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("auditor"));
            AjaxResult result = controller.edit(intention);
            assertEquals(500, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("auditor", intention.getUpdateBy());
    }

    @Test
    void 启用意图列表透传服务结果()
    {
        List<AiIvrIntention> active = new ArrayList<AiIvrIntention>();
        active.add(new AiIvrIntention());
        when(aiIvrIntentionService.selectActiveIntentions()).thenReturn(active);

        AjaxResult result = controller.getActiveIntentions();

        assertSame(active, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 意图匹配按输入文本调用服务()
    {
        Map<String, String> params = new HashMap<String, String>();
        params.put("inputText", "我要退款");
        AiIvrIntention matched = new AiIvrIntention();
        matched.setIntentionCode("REFUND");
        when(aiIvrIntentionService.matchIntention("我要退款")).thenReturn(matched);

        AjaxResult result = controller.matchIntention(params);

        assertSame(matched, result.get(AjaxResult.DATA_TAG));
        verify(aiIvrIntentionService).matchIntention("我要退款");
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] intentionIds = new Long[] { 2L, 3L };
        when(aiIvrIntentionService.deleteAiIvrIntentionByIntentionIds(intentionIds)).thenReturn(2);

        AjaxResult result = controller.remove(intentionIds);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiIvrIntentionService).deleteAiIvrIntentionByIntentionIds(intentionIds);
    }
}
