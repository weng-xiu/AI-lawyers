package ai.lawyers.quartz.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
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
import ai.lawyers.quartz.domain.SysJob;
import ai.lawyers.quartz.service.ISysJobService;
import ai.lawyers.quartz.util.ScheduleUtils;

/**
 * 定时任务接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class SysJobControllerTest
{
    @Mock
    private ISysJobService jobService;

    @InjectMocks
    private SysJobController controller;

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

    private SysJob job(Long jobId, String cronExpression, String invokeTarget)
    {
        SysJob job = new SysJob();
        job.setJobId(jobId);
        job.setJobName("测试任务");
        job.setJobGroup("DEFAULT");
        job.setCronExpression(cronExpression);
        job.setInvokeTarget(invokeTarget);
        job.setStatus("0");
        return job;
    }

    @Test
    void 任务列表分页返回()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        SysJob query = new SysJob();
        List<SysJob> list = new ArrayList<SysJob>();
        list.add(job(1L, "0/10 * * * * ?", "ai.lawyers.quartz.task.RyTask.ryNoParams"));
        when(jobService.selectJobList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertSame(list, table.getRows());
    }

    @Test
    void 导出任务Excel()
    {
        when(jobService.selectJobList(any(SysJob.class)))
                .thenReturn(List.of(job(1L, "0/10 * * * * ?", "ai.lawyers.quartz.task.RyTask.ryNoParams")));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new SysJob());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 任务详情查询()
    {
        SysJob job = job(1L, "0/10 * * * * ?", "ai.lawyers.quartz.task.RyTask.ryNoParams");
        when(jobService.selectJobById(1L)).thenReturn(job);

        assertSame(job, controller.getInfo(1L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增任务参数校验分支() throws Exception
    {
        assertEquals("新增任务'测试任务'失败，Cron表达式不正确",
                controller.add(job(1L, "bad", "ai.lawyers.quartz.task.RyTask.ryNoParams"))
                        .get(AjaxResult.MSG_TAG));
        assertEquals("新增任务'测试任务'失败，目标字符串不允许'rmi'调用",
                controller.add(job(2L, "0/10 * * * * ?", "rmi://127.0.0.1:1099/test"))
                        .get(AjaxResult.MSG_TAG));
        assertEquals("新增任务'测试任务'失败，目标字符串不允许'ldap(s)'调用",
                controller.add(job(3L, "0/10 * * * * ?", "ldap://127.0.0.1/test"))
                        .get(AjaxResult.MSG_TAG));
        assertEquals("新增任务'测试任务'失败，目标字符串不允许'http(s)'调用",
                controller.add(job(4L, "0/10 * * * * ?", "https://example.com/test"))
                        .get(AjaxResult.MSG_TAG));
        assertEquals("新增任务'测试任务'失败，目标字符串存在违规",
                controller.add(job(5L, "0/10 * * * * ?", "org.springframework.context.Test"))
                        .get(AjaxResult.MSG_TAG));

        try (MockedStatic<ScheduleUtils> mocked = mockStatic(ScheduleUtils.class))
        {
            assertEquals("新增任务'测试任务'失败，目标字符串不在白名单内",
                    controller.add(job(6L, "0/10 * * * * ?", "com.example.Test.run"))
                            .get(AjaxResult.MSG_TAG));
        }
    }

    @Test
    void 新增与修改任务成功写操作人() throws Exception
    {
        SysJob addJob = job(1L, "0/10 * * * * ?", "ai.lawyers.quartz.task.RyTask.ryNoParams");
        when(jobService.insertJob(addJob)).thenReturn(1);
        try (MockedStatic<ScheduleUtils> scheduler = mockStatic(ScheduleUtils.class);
             MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            scheduler.when(() -> ScheduleUtils.whiteList(addJob.getInvokeTarget())).thenReturn(true);
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            assertEquals(200, controller.add(addJob).get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", addJob.getCreateBy());

        SysJob editJob = job(2L, "0/10 * * * * ?", "ai.lawyers.quartz.task.RyTask.ryNoParams");
        when(jobService.updateJob(editJob)).thenReturn(0);
        try (MockedStatic<ScheduleUtils> scheduler = mockStatic(ScheduleUtils.class);
             MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            scheduler.when(() -> ScheduleUtils.whiteList(editJob.getInvokeTarget())).thenReturn(true);
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            assertEquals(500, controller.edit(editJob).get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", editJob.getUpdateBy());
    }

    @Test
    void 状态切换与立即执行() throws Exception
    {
        SysJob existing = job(5L, "0/10 * * * * ?", "ai.lawyers.quartz.task.RyTask.ryNoParams");
        SysJob change = new SysJob();
        change.setJobId(5L);
        change.setStatus("1");
        SysJob runJob = new SysJob();
        when(jobService.selectJobById(5L)).thenReturn(existing);
        when(jobService.changeStatus(existing)).thenReturn(1);
        when(jobService.run(runJob)).thenReturn(true, false);

        assertEquals(200, controller.changeStatus(change).get(AjaxResult.CODE_TAG));
        assertEquals("1", existing.getStatus());
        assertEquals(200, controller.run(runJob).get(AjaxResult.CODE_TAG));
        assertEquals("任务不存在或已过期！", controller.run(runJob).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 删除定时任务() throws Exception
    {
        Long[] ids = new Long[] { 1L, 2L };

        assertEquals(200, controller.remove(ids).get(AjaxResult.CODE_TAG));
        verify(jobService).deleteJobByIds(ids);
    }
}
