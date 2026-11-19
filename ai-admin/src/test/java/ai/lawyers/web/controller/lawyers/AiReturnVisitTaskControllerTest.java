package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
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
import ai.lawyers.system.domain.lawyers.AiReturnVisitTask;
import ai.lawyers.system.domain.lawyers.trunk.DialRequest;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.service.lawyers.IAiReturnVisitTaskService;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;

/**
 * 回访任务接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiReturnVisitTaskControllerTest
{
    @Mock
    private IAiReturnVisitTaskService aiReturnVisitTaskService;

    @Mock
    private ICallDispatchService callDispatchService;

    @InjectMocks
    private AiReturnVisitTaskController controller;

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

    private AiReturnVisitTask task()
    {
        AiReturnVisitTask task = new AiReturnVisitTask();
        task.setTaskId(9L);
        task.setTaskNo("RV-001");
        task.setCallerNumber(" 13800000000 ");
        task.setCallerName("张三");
        return task;
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiReturnVisitTask query = new AiReturnVisitTask();
        List<AiReturnVisitTask> tasks = new ArrayList<AiReturnVisitTask>();
        tasks.add(task());
        when(aiReturnVisitTaskService.selectAiReturnVisitTaskList(query)).thenReturn(tasks);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(tasks, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        AiReturnVisitTask task = task();
        when(aiReturnVisitTaskService.selectAiReturnVisitTaskList(any(AiReturnVisitTask.class)))
                .thenReturn(List.of(task));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiReturnVisitTask());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情透传服务结果()
    {
        AiReturnVisitTask task = task();
        when(aiReturnVisitTaskService.selectAiReturnVisitTaskByTaskId(9L)).thenReturn(task);

        AjaxResult result = controller.getInfo(9L);

        assertSame(task, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增时补默认受理人与状态优先级()
    {
        AiReturnVisitTask task = new AiReturnVisitTask();
        when(aiReturnVisitTaskService.insertAiReturnVisitTask(any(AiReturnVisitTask.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            assertEquals(200, controller.add(task).get(AjaxResult.CODE_TAG));
        }
        assertEquals("agent", task.getCreateBy());
        assertEquals("agent", task.getAssignee());
        assertEquals("0", task.getStatus());
        assertEquals("2", task.getPriority());
    }

    @Test
    void 新增时保留指定受理人与优先级()
    {
        AiReturnVisitTask task = task();
        task.setAssignee("李四");
        task.setStatus("1");
        task.setPriority("1");
        when(aiReturnVisitTaskService.insertAiReturnVisitTask(any(AiReturnVisitTask.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            controller.add(task);
        }
        assertEquals("李四", task.getAssignee());
        assertEquals("1", task.getStatus());
        assertEquals("1", task.getPriority());
    }

    @Test
    void 修改写入更新人()
    {
        AiReturnVisitTask task = task();
        when(aiReturnVisitTaskService.updateAiReturnVisitTask(any(AiReturnVisitTask.class))).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("auditor"));
            assertEquals(500, controller.edit(task).get(AjaxResult.CODE_TAG));
        }
        assertEquals("auditor", task.getUpdateBy());
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] taskIds = new Long[] { 1L, 2L };
        when(aiReturnVisitTaskService.deleteAiReturnVisitTaskByTaskIds(taskIds)).thenReturn(2);

        AjaxResult result = controller.remove(taskIds);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiReturnVisitTaskService).deleteAiReturnVisitTaskByTaskIds(taskIds);
    }

    @Test
    void 统计透传服务结果()
    {
        Map<String, Object> stats = new HashMap<String, Object>();
        stats.put("pending", 3);
        when(aiReturnVisitTaskService.selectReturnVisitTaskStats()).thenReturn(stats);

        assertSame(stats, controller.getStats().get(AjaxResult.DATA_TAG));
    }

    @Test
    void 一键外呼任务不存在()
    {
        when(aiReturnVisitTaskService.selectAiReturnVisitTaskByTaskId(1L)).thenReturn(null);

        AjaxResult result = controller.call(1L);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("回访任务不存在", result.get(AjaxResult.MSG_TAG));
        verify(callDispatchService, never()).dialWithQueue(any(DialRequest.class));
    }

    @Test
    void 一键外呼无号码时拒绝()
    {
        AiReturnVisitTask task = task();
        task.setCallerNumber("   ");
        when(aiReturnVisitTaskService.selectAiReturnVisitTaskByTaskId(9L)).thenReturn(task);

        AjaxResult result = controller.call(9L);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("该回访任务无来电号码，无法外呼", result.get(AjaxResult.MSG_TAG));
        verify(callDispatchService, never()).dialWithQueue(any(DialRequest.class));
    }

    @Test
    void 一键外呼成功提交调度器()
    {
        AiReturnVisitTask task = task();
        DialResult dialResult = DialResult.ok("uuid-1");
        when(aiReturnVisitTaskService.selectAiReturnVisitTaskByTaskId(9L)).thenReturn(task);
        when(callDispatchService.dialWithQueue(any(DialRequest.class))).thenReturn(dialResult);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            AjaxResult result = controller.call(9L);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            assertEquals("外呼已发起", result.get(AjaxResult.MSG_TAG));
            assertSame(dialResult, result.get("data"));
        }
        ArgumentCaptor<DialRequest> captor = ArgumentCaptor.forClass(DialRequest.class);
        verify(callDispatchService).dialWithQueue(captor.capture());
        assertEquals("13800000000", captor.getValue().getCalleeNumber());
        assertEquals("张三", captor.getValue().getCallerNumber());
        assertEquals(50, captor.getValue().getPriority());
        assertEquals("agent", captor.getValue().getCreateBy());
        assertTrue(captor.getValue().getRemark().contains("RV-001"));
    }

    @Test
    void 一键外呼失败返回原因()
    {
        AiReturnVisitTask task = task();
        when(aiReturnVisitTaskService.selectAiReturnVisitTaskByTaskId(9L)).thenReturn(task);
        when(callDispatchService.dialWithQueue(any(DialRequest.class)))
                .thenReturn(null, DialResult.fail("E1", "线路故障"));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));

            assertEquals("外呼发起失败：无可用线路", controller.call(9L).get(AjaxResult.MSG_TAG));
            assertEquals("外呼发起失败：线路故障", controller.call(9L).get(AjaxResult.MSG_TAG));
        }
    }
}
