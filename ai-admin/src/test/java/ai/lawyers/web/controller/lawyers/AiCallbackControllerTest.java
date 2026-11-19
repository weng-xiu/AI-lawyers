package ai.lawyers.web.controller.lawyers;

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
import ai.lawyers.system.domain.lawyers.AiCallback;
import ai.lawyers.system.service.lawyers.IAiCallbackService;

/**
 * 客户回访接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiCallbackControllerTest
{
    @Mock
    private IAiCallbackService aiCallbackService;

    @InjectMocks
    private AiCallbackController controller;

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
        AiCallback query = new AiCallback();
        List<AiCallback> callbacks = new ArrayList<AiCallback>();
        callbacks.add(new AiCallback());
        when(aiCallbackService.selectAiCallbackList(query)).thenReturn(callbacks);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(callbacks, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        AiCallback callback = new AiCallback();
        callback.setVisitBy("张三");
        when(aiCallbackService.selectAiCallbackList(any(AiCallback.class)))
                .thenReturn(List.of(callback));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiCallback());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情透传服务结果()
    {
        AiCallback callback = new AiCallback();
        callback.setCallbackId(7L);
        when(aiCallbackService.selectAiCallbackByCallbackId(7L)).thenReturn(callback);

        AjaxResult result = controller.getInfo(7L);

        assertSame(callback, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增时回访人为空默认当前用户()
    {
        AiCallback callback = new AiCallback();
        when(aiCallbackService.insertAiCallback(any(AiCallback.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            AjaxResult result = controller.add(callback);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("agent", callback.getCreateBy());
        assertEquals("agent", callback.getVisitBy());
    }

    @Test
    void 新增时保留指定回访人()
    {
        AiCallback callback = new AiCallback();
        callback.setVisitBy("李四");
        when(aiCallbackService.insertAiCallback(any(AiCallback.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            controller.add(callback);
        }
        assertEquals("李四", callback.getVisitBy());
    }

    @Test
    void 修改写入更新人()
    {
        AiCallback callback = new AiCallback();
        when(aiCallbackService.updateAiCallback(any(AiCallback.class))).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("auditor"));
            AjaxResult result = controller.edit(callback);
            assertEquals(500, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("auditor", callback.getUpdateBy());
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] callbackIds = new Long[] { 1L, 2L };
        when(aiCallbackService.deleteAiCallbackByCallbackIds(callbackIds)).thenReturn(2);

        AjaxResult result = controller.remove(callbackIds);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiCallbackService).deleteAiCallbackByCallbackIds(callbackIds);
    }

    @Test
    void 统计与满意度趋势透传服务结果()
    {
        Map<String, Object> stats = new HashMap<String, Object>();
        stats.put("total", 10);
        List<Map<String, Object>> trend = new ArrayList<Map<String, Object>>();
        trend.add(new HashMap<String, Object>());
        when(aiCallbackService.selectCallbackStats()).thenReturn(stats);
        when(aiCallbackService.selectSatisfactionTrend(7)).thenReturn(trend);

        assertSame(stats, controller.getStats().get(AjaxResult.DATA_TAG));
        assertSame(trend, controller.getSatisfactionTrend(7).get(AjaxResult.DATA_TAG));
        verify(aiCallbackService).selectSatisfactionTrend(7);
    }
}
