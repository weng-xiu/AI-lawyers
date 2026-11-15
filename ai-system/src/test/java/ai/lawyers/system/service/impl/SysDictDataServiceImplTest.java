package ai.lawyers.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.core.domain.entity.SysDictData;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.common.utils.spring.SpringUtils;
import ai.lawyers.system.mapper.SysDictDataMapper;

/**
 * 字典数据服务单测。
 *
 * <p>覆盖字典数据查询、删除/新增/修改后按字典类型刷新缓存。</p>
 *
 * @author ai-lawyers
 */
class SysDictDataServiceImplTest
{
    private SysDictDataServiceImpl service;

    @Mock private SysDictDataMapper dictDataMapper;
    @Mock private RedisCache redisCache;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new SysDictDataServiceImpl();
        ReflectionTestUtils.setField(service, "dictDataMapper", dictDataMapper);
    }

    /** 打开 SpringUtils 静态 Mock，使 DictUtils 能拿到 RedisCache。 */
    private MockedStatic<SpringUtils> mockSpringUtils()
    {
        MockedStatic<SpringUtils> mocked = mockStatic(SpringUtils.class);
        mocked.when(() -> SpringUtils.getBean(RedisCache.class)).thenReturn(redisCache);
        return mocked;
    }

    private static SysDictData dictData(Long dictCode, String type, String label, String value)
    {
        SysDictData data = new SysDictData();
        data.setDictCode(dictCode);
        data.setDictType(type);
        data.setDictLabel(label);
        data.setDictValue(value);
        return data;
    }

    @Test
    void selectDictDataList_delegates()
    {
        SysDictData query = new SysDictData();
        List<SysDictData> list = Collections.singletonList(dictData(1L, "t", "L", "V"));
        when(dictDataMapper.selectDictDataList(query)).thenReturn(list);

        assertSame(list, service.selectDictDataList(query));
    }

    @Test
    void selectDictLabel_delegates()
    {
        when(dictDataMapper.selectDictLabel("t", "V")).thenReturn("标签");

        assertEquals("标签", service.selectDictLabel("t", "V"));
    }

    @Test
    void selectDictDataById_delegates()
    {
        SysDictData expected = dictData(1L, "t", "L", "V");
        when(dictDataMapper.selectDictDataById(1L)).thenReturn(expected);

        assertSame(expected, service.selectDictDataById(1L));
    }

    @Test
    void deleteDictDataByIds_deletesAndRefreshesCache()
    {
        SysDictData data = dictData(1L, "t", "L", "V");
        List<SysDictData> remain = Collections.singletonList(dictData(2L, "t", "L2", "V2"));
        when(dictDataMapper.selectDictDataById(1L)).thenReturn(data);
        when(dictDataMapper.selectDictDataByType("t")).thenReturn(remain);

        try (MockedStatic<SpringUtils> ignored = mockSpringUtils())
        {
            service.deleteDictDataByIds(new Long[] { 1L });
        }

        verify(dictDataMapper).deleteDictDataById(1L);
        verify(redisCache).setCacheObject("sys_dict:t", remain);
    }

    @Test
    void insertDictData_success_refreshesCache()
    {
        SysDictData data = dictData(null, "t", "L", "V");
        List<SysDictData> all = Collections.singletonList(data);
        when(dictDataMapper.insertDictData(data)).thenReturn(1);
        when(dictDataMapper.selectDictDataByType("t")).thenReturn(all);

        try (MockedStatic<SpringUtils> ignored = mockSpringUtils())
        {
            assertEquals(1, service.insertDictData(data));
        }

        verify(redisCache).setCacheObject("sys_dict:t", all);
    }

    @Test
    void insertDictData_failure_doesNotCache()
    {
        SysDictData data = dictData(null, "t", "L", "V");
        when(dictDataMapper.insertDictData(data)).thenReturn(0);

        try (MockedStatic<SpringUtils> ignored = mockSpringUtils())
        {
            assertEquals(0, service.insertDictData(data));
        }

        verify(redisCache, never()).setCacheObject(any(), any());
    }

    @Test
    void updateDictData_success_refreshesCache()
    {
        SysDictData data = dictData(1L, "t", "L", "V");
        List<SysDictData> all = Collections.singletonList(data);
        when(dictDataMapper.updateDictData(data)).thenReturn(1);
        when(dictDataMapper.selectDictDataByType("t")).thenReturn(all);

        try (MockedStatic<SpringUtils> ignored = mockSpringUtils())
        {
            assertEquals(1, service.updateDictData(data));
        }

        verify(redisCache).setCacheObject("sys_dict:t", all);
    }

    @Test
    void updateDictData_failure_doesNotCache()
    {
        SysDictData data = dictData(1L, "t", "L", "V");
        when(dictDataMapper.updateDictData(data)).thenReturn(0);

        try (MockedStatic<SpringUtils> ignored = mockSpringUtils())
        {
            assertEquals(0, service.updateDictData(data));
        }

        verify(redisCache, never()).setCacheObject(any(), any());
    }
}
