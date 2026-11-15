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
import ai.lawyers.system.domain.SysLogininfor;
import ai.lawyers.system.mapper.SysLogininforMapper;

/**
 * 登录日志服务单测。
 *
 * <p>覆盖登录日志新增、查询、批量删除与清空。</p>
 *
 * @author ai-lawyers
 */
class SysLogininforServiceImplTest
{
    private SysLogininforServiceImpl service;

    @Mock private SysLogininforMapper logininforMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new SysLogininforServiceImpl();
        ReflectionTestUtils.setField(service, "logininforMapper", logininforMapper);
    }

    @Test
    void insertLogininfor_delegates()
    {
        SysLogininfor logininfor = new SysLogininfor();

        service.insertLogininfor(logininfor);

        verify(logininforMapper).insertLogininfor(logininfor);
    }

    @Test
    void selectLogininforList_delegates()
    {
        SysLogininfor query = new SysLogininfor();
        List<SysLogininfor> list = Collections.singletonList(new SysLogininfor());
        when(logininforMapper.selectLogininforList(query)).thenReturn(list);

        assertSame(list, service.selectLogininforList(query));
    }

    @Test
    void deleteLogininforByIds_delegates()
    {
        Long[] ids = new Long[] { 1L };
        when(logininforMapper.deleteLogininforByIds(ids)).thenReturn(1);

        assertEquals(1, service.deleteLogininforByIds(ids));
    }

    @Test
    void cleanLogininfor_delegates()
    {
        service.cleanLogininfor();

        verify(logininforMapper).cleanLogininfor();
    }
}
