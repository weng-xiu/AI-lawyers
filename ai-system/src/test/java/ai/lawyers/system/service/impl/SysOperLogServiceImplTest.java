package ai.lawyers.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.SysOperLog;
import ai.lawyers.system.mapper.SysOperLogMapper;

/**
 * 操作日志服务单测。
 *
 * <p>覆盖操作日志新增、查询、按 ID 查询、批量删除与清空。</p>
 *
 * @author ai-lawyers
 */
class SysOperLogServiceImplTest
{
    private SysOperLogServiceImpl service;

    @Mock private SysOperLogMapper operLogMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new SysOperLogServiceImpl();
        ReflectionTestUtils.setField(service, "operLogMapper", operLogMapper);
    }

    @Test
    void insertOperlog_delegates()
    {
        SysOperLog operLog = new SysOperLog();

        service.insertOperlog(operLog);

        verify(operLogMapper).insertOperlog(operLog);
    }

    @Test
    void selectOperLogList_delegates()
    {
        SysOperLog query = new SysOperLog();
        List<SysOperLog> list = Collections.singletonList(new SysOperLog());
        when(operLogMapper.selectOperLogList(query)).thenReturn(list);

        assertSame(list, service.selectOperLogList(query));
    }

    @Test
    void deleteOperLogByIds_delegates()
    {
        Long[] ids = new Long[] { 1L };
        when(operLogMapper.deleteOperLogByIds(ids)).thenReturn(1);

        assertEquals(1, service.deleteOperLogByIds(ids));
    }

    @Test
    void selectOperLogById_delegates()
    {
        SysOperLog expected = new SysOperLog();
        when(operLogMapper.selectOperLogById(1L)).thenReturn(expected);

        assertSame(expected, service.selectOperLogById(1L));
    }

    @Test
    void cleanOperLog_delegates()
    {
        service.cleanOperLog();

        verify(operLogMapper).cleanOperLog();
    }
}
