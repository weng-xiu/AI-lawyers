package ai.lawyers.quartz.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import ai.lawyers.common.constant.ScheduleConstants;
import ai.lawyers.quartz.domain.SysJob;
import ai.lawyers.quartz.mapper.SysJobMapper;
import ai.lawyers.quartz.util.ScheduleUtils;

/**
 * 定时任务调度服务单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class SysJobServiceImplTest
{
    @Mock
    private Scheduler scheduler;

    @Mock
    private SysJobMapper jobMapper;

    @InjectMocks
    private SysJobServiceImpl jobService;

    private SysJob job(Long jobId)
    {
        SysJob job = new SysJob();
        job.setJobId(jobId);
        job.setJobName("测试任务");
        job.setJobGroup("DEFAULT");
        job.setCronExpression("0/10 * * * * ?");
        job.setInvokeTarget("ai.lawyers.quartz.task.RyTask.ryNoParams");
        job.setStatus(ScheduleConstants.Status.PAUSE.getValue());
        return job;
    }

    private JobKey key(Long jobId)
    {
        return ScheduleUtils.getJobKey(jobId, "DEFAULT");
    }

    @Test
    void 启动时清空调度器并按库中任务重建() throws Exception
    {
        when(jobMapper.selectJobAll()).thenReturn(Arrays.asList(job(1L), job(2L)));
        try (MockedStatic<ScheduleUtils> mocked = mockStatic(ScheduleUtils.class))
        {
            jobService.init();
            verify(scheduler).clear();
            mocked.verify(() -> ScheduleUtils.createScheduleJob(eq(scheduler), any(SysJob.class)), times(2));
        }
    }

    @Test
    void 列表与详情查询委托Mapper()
    {
        SysJob query = new SysJob();
        List<SysJob> list = Arrays.asList(job(1L));
        when(jobMapper.selectJobList(query)).thenReturn(list);
        when(jobMapper.selectJobById(1L)).thenReturn(list.get(0));

        assertEquals(list, jobService.selectJobList(query));
        assertEquals(list.get(0), jobService.selectJobById(1L));
    }

    @Test
    void 暂停任务成功后置暂停状态并暂停调度() throws Exception
    {
        SysJob job = job(1L);
        JobKey jobKey = key(1L);
        when(jobMapper.updateJob(job)).thenReturn(1);
        try (MockedStatic<ScheduleUtils> mocked = mockStatic(ScheduleUtils.class))
        {
            mocked.when(() -> ScheduleUtils.getJobKey(1L, "DEFAULT")).thenReturn(jobKey);
            assertEquals(1, jobService.pauseJob(job));
            assertEquals(ScheduleConstants.Status.PAUSE.getValue(), job.getStatus());
            verify(scheduler).pauseJob(jobKey);
        }
    }

    @Test
    void 暂停任务未更新时不操作调度器() throws Exception
    {
        SysJob job = job(1L);
        when(jobMapper.updateJob(job)).thenReturn(0);
        try (MockedStatic<ScheduleUtils> ignored = mockStatic(ScheduleUtils.class))
        {
            assertEquals(0, jobService.pauseJob(job));
            verify(scheduler, never()).pauseJob(any(JobKey.class));
        }
    }

    @Test
    void 恢复任务成功后置正常状态并恢复调度() throws Exception
    {
        SysJob job = job(1L);
        JobKey jobKey = key(1L);
        when(jobMapper.updateJob(job)).thenReturn(1);
        try (MockedStatic<ScheduleUtils> mocked = mockStatic(ScheduleUtils.class))
        {
            mocked.when(() -> ScheduleUtils.getJobKey(1L, "DEFAULT")).thenReturn(jobKey);
            assertEquals(1, jobService.resumeJob(job));
            assertEquals(ScheduleConstants.Status.NORMAL.getValue(), job.getStatus());
            verify(scheduler).resumeJob(jobKey);
        }
    }

    @Test
    void 删除任务成功后移除调度() throws Exception
    {
        SysJob job = job(1L);
        JobKey jobKey = key(1L);
        when(jobMapper.deleteJobById(1L)).thenReturn(1);
        try (MockedStatic<ScheduleUtils> mocked = mockStatic(ScheduleUtils.class))
        {
            mocked.when(() -> ScheduleUtils.getJobKey(1L, "DEFAULT")).thenReturn(jobKey);
            assertEquals(1, jobService.deleteJob(job));
            verify(scheduler).deleteJob(jobKey);
        }
    }

    @Test
    void 删除任务未成功时不操作调度器() throws Exception
    {
        SysJob job = job(1L);
        when(jobMapper.deleteJobById(1L)).thenReturn(0);
        try (MockedStatic<ScheduleUtils> ignored = mockStatic(ScheduleUtils.class))
        {
            assertEquals(0, jobService.deleteJob(job));
            verify(scheduler, never()).deleteJob(any(JobKey.class));
        }
    }

    @Test
    void 批量删除逐个查询并移除调度() throws Exception
    {
        when(jobMapper.selectJobById(1L)).thenReturn(job(1L));
        when(jobMapper.selectJobById(2L)).thenReturn(job(2L));
        when(jobMapper.deleteJobById(1L)).thenReturn(1);
        when(jobMapper.deleteJobById(2L)).thenReturn(1);
        JobKey firstKey = key(1L);
        JobKey secondKey = key(2L);
        try (MockedStatic<ScheduleUtils> mocked = mockStatic(ScheduleUtils.class))
        {
            mocked.when(() -> ScheduleUtils.getJobKey(1L, "DEFAULT")).thenReturn(firstKey);
            mocked.when(() -> ScheduleUtils.getJobKey(2L, "DEFAULT")).thenReturn(secondKey);
            jobService.deleteJobByIds(new Long[] { 1L, 2L });
            verify(scheduler).deleteJob(firstKey);
            verify(scheduler).deleteJob(secondKey);
        }
    }

    @Test
    void 状态变更按目标状态分派恢复或暂停() throws Exception
    {
        try (MockedStatic<ScheduleUtils> mocked = mockStatic(ScheduleUtils.class))
        {
            mocked.when(() -> ScheduleUtils.getJobKey(anyLong(), anyString()))
                    .thenAnswer(invocation -> JobKey.jobKey("TASK" + invocation.getArgument(0), invocation.getArgument(1)));

            SysJob normal = job(1L);
            normal.setStatus(ScheduleConstants.Status.NORMAL.getValue());
            when(jobMapper.updateJob(normal)).thenReturn(1);
            assertEquals(1, jobService.changeStatus(normal));
            verify(scheduler).resumeJob(any(JobKey.class));

            SysJob pause = job(2L);
            pause.setStatus(ScheduleConstants.Status.PAUSE.getValue());
            when(jobMapper.updateJob(pause)).thenReturn(1);
            assertEquals(1, jobService.changeStatus(pause));
            verify(scheduler).pauseJob(any(JobKey.class));
        }
    }

    @Test
    void 未知状态返回零且不更新数据库() throws Exception
    {
        SysJob job = job(1L);
        job.setStatus("9");
        try (MockedStatic<ScheduleUtils> ignored = mockStatic(ScheduleUtils.class))
        {
            assertEquals(0, jobService.changeStatus(job));
            verify(jobMapper, never()).updateJob(any(SysJob.class));
        }
    }

    @Test
    void 立即运行任务在调度存在时触发() throws Exception
    {
        SysJob job = job(1L);
        JobKey jobKey = key(1L);
        when(jobMapper.selectJobById(1L)).thenReturn(job);
        when(scheduler.checkExists(jobKey)).thenReturn(true);
        try (MockedStatic<ScheduleUtils> mocked = mockStatic(ScheduleUtils.class))
        {
            mocked.when(() -> ScheduleUtils.getJobKey(1L, "DEFAULT")).thenReturn(jobKey);
            assertTrue(jobService.run(job));
            verify(scheduler).triggerJob(eq(jobKey), any(JobDataMap.class));
        }
    }

    @Test
    void 立即运行任务在调度不存在时返回false() throws Exception
    {
        SysJob job = job(1L);
        JobKey jobKey = key(1L);
        when(jobMapper.selectJobById(1L)).thenReturn(job);
        when(scheduler.checkExists(jobKey)).thenReturn(false);
        try (MockedStatic<ScheduleUtils> mocked = mockStatic(ScheduleUtils.class))
        {
            mocked.when(() -> ScheduleUtils.getJobKey(1L, "DEFAULT")).thenReturn(jobKey);
            assertFalse(jobService.run(job));
            verify(scheduler, never()).triggerJob(any(JobKey.class), any(JobDataMap.class));
        }
    }

    @Test
    void 新增任务默认暂停并注册调度() throws Exception
    {
        SysJob job = job(1L);
        job.setStatus(null);
        when(jobMapper.insertJob(job)).thenReturn(1);
        try (MockedStatic<ScheduleUtils> mocked = mockStatic(ScheduleUtils.class))
        {
            assertEquals(1, jobService.insertJob(job));
            assertEquals(ScheduleConstants.Status.PAUSE.getValue(), job.getStatus());
            mocked.verify(() -> ScheduleUtils.createScheduleJob(eq(scheduler), eq(job)));
        }
    }

    @Test
    void 新增任务未写入时不注册调度() throws Exception
    {
        SysJob job = job(1L);
        when(jobMapper.insertJob(job)).thenReturn(0);
        try (MockedStatic<ScheduleUtils> ignored = mockStatic(ScheduleUtils.class))
        {
            assertEquals(0, jobService.insertJob(job));
        }
    }

    @Test
    void 更新任务后重建调度() throws Exception
    {
        SysJob job = job(1L);
        JobKey jobKey = key(1L);
        when(jobMapper.selectJobById(1L)).thenReturn(job(1L));
        when(jobMapper.updateJob(job)).thenReturn(1);
        when(scheduler.checkExists(jobKey)).thenReturn(true);
        try (MockedStatic<ScheduleUtils> mocked = mockStatic(ScheduleUtils.class))
        {
            mocked.when(() -> ScheduleUtils.getJobKey(1L, "DEFAULT")).thenReturn(jobKey);
            assertEquals(1, jobService.updateJob(job));
            verify(scheduler).deleteJob(jobKey);
            mocked.verify(() -> ScheduleUtils.createScheduleJob(eq(scheduler), eq(job)));
        }
    }

    @Test
    void 更新任务未写入时不重建调度() throws Exception
    {
        SysJob job = job(1L);
        when(jobMapper.selectJobById(1L)).thenReturn(job(1L));
        when(jobMapper.updateJob(job)).thenReturn(0);
        try (MockedStatic<ScheduleUtils> ignored = mockStatic(ScheduleUtils.class))
        {
            assertEquals(0, jobService.updateJob(job));
        }
    }

    @Test
    void cron表达式合法性校验()
    {
        assertTrue(jobService.checkCronExpressionIsValid("0/10 * * * * ?"));
        assertFalse(jobService.checkCronExpressionIsValid("not-a-cron"));
    }
}
