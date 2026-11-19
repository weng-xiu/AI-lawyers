package ai.lawyers.web.controller.lawyers.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
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
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.utils.sign.CallbackSignUtils;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundTask;
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundTaskService;
import ai.lawyers.system.service.lawyers.outbound.IOutboundExecutionService;

/**
 * 外呼任务接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiOutboundTaskControllerTest
{
    @Mock
    private IAiOutboundTaskService aiOutboundTaskService;

    @Mock
    private IOutboundExecutionService outboundExecutionService;

    @InjectMocks
    private AiOutboundTaskController controller;

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

    private AiOutboundTask task()
    {
        AiOutboundTask task = new AiOutboundTask();
        task.setTaskId(5L);
        return task;
    }

    private Map<String, Object> event(String uuid, String event)
    {
        Map<String, Object> param = new LinkedHashMap<String, Object>();
        param.put("callUuid", uuid);
        param.put("event", event);
        return param;
    }

    @Test
    void 任务列表分页返回()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiOutboundTask query = new AiOutboundTask();
        List<AiOutboundTask> list = new ArrayList<AiOutboundTask>();
        list.add(task());
        when(aiOutboundTaskService.selectAiOutboundTaskList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertSame(list, table.getRows());
    }

    @Test
    void 导出外呼任务Excel()
    {
        when(aiOutboundTaskService.selectAiOutboundTaskList(any(AiOutboundTask.class))).thenReturn(List.of(task()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiOutboundTask());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情与新增修改删除()
    {
        AiOutboundTask task = task();
        Long[] ids = new Long[] { 5L };
        when(aiOutboundTaskService.selectAiOutboundTaskByTaskId(5L)).thenReturn(task);
        when(aiOutboundTaskService.insertAiOutboundTask(task)).thenReturn(1);
        when(aiOutboundTaskService.updateAiOutboundTask(task)).thenReturn(0);
        when(aiOutboundTaskService.deleteAiOutboundTaskByTaskIds(ids)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            assertSame(task, controller.getInfo(5L).get(AjaxResult.DATA_TAG));
            assertEquals(200, controller.add(task).get(AjaxResult.CODE_TAG));
            assertEquals(500, controller.edit(task).get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", task.getCreateBy());
        assertEquals("admin", task.getUpdateBy());
        assertEquals(200, controller.remove(ids).get(AjaxResult.CODE_TAG));
    }

    @Test
    void 生成任务号与任务启停()
    {
        when(aiOutboundTaskService.generateTaskNo()).thenReturn("OT20261120001");
        when(aiOutboundTaskService.startTask(5L)).thenReturn(1);
        when(aiOutboundTaskService.pauseTask(5L)).thenReturn(0);
        when(aiOutboundTaskService.stopTask(5L)).thenReturn(1);

        assertEquals("OT20261120001", controller.generateTaskNo().get(AjaxResult.MSG_TAG));
        assertEquals(200, controller.startTask(5L).get(AjaxResult.CODE_TAG));
        assertEquals(500, controller.pauseTask(5L).get(AjaxResult.CODE_TAG));
        assertEquals(200, controller.stopTask(5L).get(AjaxResult.CODE_TAG));
    }

    @Test
    void 立即执行任务返回处理数量()
    {
        when(outboundExecutionService.executeTask(5L)).thenReturn(8);

        AjaxResult result = controller.executeTask(5L);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        assertEquals(8, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 网关事件回调参数校验与透传()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertEquals("callUuid 与 event 不能为空", controller.event(new LinkedHashMap<String, Object>(), request)
                .get(AjaxResult.MSG_TAG));
        assertEquals("callUuid 与 event 不能为空", controller.event(event("uuid-1", null), request)
                .get(AjaxResult.MSG_TAG));

        Map<String, Object> param = event("uuid-1", "ANSWERED");
        assertEquals(200, controller.event(param, request).get(AjaxResult.CODE_TAG));
        verify(outboundExecutionService).onCallEvent("uuid-1", "ANSWERED", param);
    }

    @Test
    void 网关事件回调签名校验()
    {
        ReflectionTestUtils.setField(controller, "callbackSignEnabled", true);
        ReflectionTestUtils.setField(controller, "callbackSignSecret", "secret-key");
        Map<String, Object> param = event("uuid-1", "HANGUP");

        AjaxResult rejected = controller.event(param, new MockHttpServletRequest());
        assertEquals(403, rejected.get(AjaxResult.CODE_TAG));
        assertEquals("回调签名校验失败", rejected.get(AjaxResult.MSG_TAG));

        String timestamp = String.valueOf(System.currentTimeMillis());
        MockHttpServletRequest signed = new MockHttpServletRequest();
        signed.addHeader("X-Callback-Timestamp", timestamp);
        signed.addHeader("X-Callback-Sign", CallbackSignUtils.sign("secret-key", "uuid-1|HANGUP", timestamp));

        assertEquals(200, controller.event(param, signed).get(AjaxResult.CODE_TAG));
        verify(outboundExecutionService).onCallEvent("uuid-1", "HANGUP", param);
    }
}
