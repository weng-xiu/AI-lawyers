package ai.lawyers.quartz.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ai.lawyers.quartz.domain.SysJobLog;
import ai.lawyers.quartz.mapper.SysJobLogMapper;

/**
 * 定时任务日志服务单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class SysJobLogServiceImplTest
{
    @Mock
    private SysJobLogMapper jobLogMapper;

    @InjectMocks
    private SysJobLogServiceImpl jobLogService;

    private SysJobLog jobLog(Long jobLogId)
    {
        SysJobLog log = new SysJobLog();
        log.setJobLogId(jobLogId);
        log.setJobName("测试任务");
        log.setJobGroup("DEFAULT");
        log.setStatus("0");
        return log;
    }

    @Test
    void 日志列表与详情查询委托Mapper()
    {
        SysJobLog query = new SysJobLog();
        List<SysJobLog> list = Arrays.asList(jobLog(1L));
        when(jobLogMapper.selectJobLogList(query)).thenReturn(list);
        when(jobLogMapper.selectJobLogById(1L)).thenReturn(list.get(0));

        assertSame(list, jobLogService.selectJobLogList(query));
        assertSame(list.get(0), jobLogService.selectJobLogById(1L));
    }

    @Test
    void 新增任务日志写入Mapper()
    {
        SysJobLog log = jobLog(1L);

        jobLogService.addJobLog(log);

        verify(jobLogMapper).insertJobLog(log);
    }

    @Test
    void 批量删除与单条删除返回影响行数()
    {
        Long[] logIds = new Long[] { 1L, 2L };
        when(jobLogMapper.deleteJobLogByIds(logIds)).thenReturn(2);
        when(jobLogMapper.deleteJobLogById(3L)).thenReturn(1);

        assertEquals(2, jobLogService.deleteJobLogByIds(logIds));
        assertEquals(1, jobLogService.deleteJobLogById(3L));
    }

    @Test
    void 清空任务日志委托Mapper()
    {
        jobLogService.cleanJobLog();

        verify(jobLogMapper).cleanJobLog();
    }
}
