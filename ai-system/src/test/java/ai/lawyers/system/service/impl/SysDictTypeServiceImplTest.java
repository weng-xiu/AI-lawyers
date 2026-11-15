package ai.lawyers.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import com.alibaba.fastjson2.JSONArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.core.domain.entity.SysDictData;
import ai.lawyers.common.core.domain.entity.SysDictType;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.spring.SpringUtils;
import ai.lawyers.system.mapper.SysDictDataMapper;
import ai.lawyers.system.mapper.SysDictTypeMapper;

/**
 * 字典类型服务单测。
 *
 * <p>覆盖字典类型查询、字典数据缓存回填与失效、字典删除前的数据占用校验、
 * 缓存加载/清空/重置以及字典类型唯一性校验。</p>
 *
 * @author ai-lawyers
 */
class SysDictTypeServiceImplTest
{
    private SysDictTypeServiceImpl service;

    @Mock private SysDictTypeMapper dictTypeMapper;
    @Mock private SysDictDataMapper dictDataMapper;
    @Mock private RedisCache redisCache;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new SysDictTypeServiceImpl();
        ReflectionTestUtils.setField(service, "dictTypeMapper", dictTypeMapper);
        ReflectionTestUtils.setField(service, "dictDataMapper", dictDataMapper);
    }

    /** 打开 SpringUtils 静态 Mock，使 DictUtils 能拿到 RedisCache。 */
    private MockedStatic<SpringUtils> mockSpringUtils()
    {
        MockedStatic<SpringUtils> mocked = mockStatic(SpringUtils.class);
        mocked.when(() -> SpringUtils.getBean(RedisCache.class)).thenReturn(redisCache);
        return mocked;
    }

    private static SysDictType dictType(Long dictId, String type, String name)
    {
        SysDictType dict = new SysDictType();
        dict.setDictId(dictId);
        dict.setDictType(type);
        dict.setDictName(name);
        return dict;
    }

    private static SysDictData dictData(Long dictCode, String type, String label, String value, Long sort)
    {
        SysDictData data = new SysDictData();
        data.setDictCode(dictCode);
        data.setDictType(type);
        data.setDictLabel(label);
        data.setDictValue(value);
        data.setDictSort(sort);
        return data;
    }

    @Test
    void selectDictTypeList_delegates()
    {
        SysDictType query = new SysDictType();
        List<SysDictType> list = Collections.singletonList(dictType(1L, "t", "n"));
        when(dictTypeMapper.selectDictTypeList(query)).thenReturn(list);

        assertSame(list, service.selectDictTypeList(query));
    }

    @Test
    void selectDictTypeAll_delegates()
    {
        List<SysDictType> list = Collections.singletonList(dictType(1L, "t", "n"));
        when(dictTypeMapper.selectDictTypeAll()).thenReturn(list);

        assertSame(list, service.selectDictTypeAll());
    }

    @Test
    void selectDictDataByType_cacheHit_skipsDatabase()
    {
        JSONArray cached = JSONArray.parseArray("[{\"dictCode\":1,\"dictType\":\"t\",\"dictLabel\":\"L\"}]");
        when(redisCache.getCacheObject("sys_dict:t")).thenReturn(cached);

        try (MockedStatic<SpringUtils> ignored = mockSpringUtils())
        {
            List<SysDictData> result = service.selectDictDataByType("t");

            assertEquals(1, result.size());
            assertEquals("L", result.get(0).getDictLabel());
        }
        verify(dictDataMapper, never()).selectDictDataByType(any());
    }

    @Test
    void selectDictDataByType_cacheMiss_queriesAndCaches()
    {
        List<SysDictData> dbData = Collections.singletonList(dictData(1L, "t", "L", "V", 1L));
        when(redisCache.getCacheObject("sys_dict:t")).thenReturn(null);
        when(dictDataMapper.selectDictDataByType("t")).thenReturn(dbData);

        try (MockedStatic<SpringUtils> ignored = mockSpringUtils())
        {
            assertSame(dbData, service.selectDictDataByType("t"));
        }
        verify(redisCache).setCacheObject("sys_dict:t", dbData);
    }

    @Test
    void selectDictDataByType_notFound_returnsNull()
    {
        when(redisCache.getCacheObject("sys_dict:t")).thenReturn(null);
        when(dictDataMapper.selectDictDataByType("t")).thenReturn(Collections.emptyList());

        try (MockedStatic<SpringUtils> ignored = mockSpringUtils())
        {
            assertNull(service.selectDictDataByType("t"));
        }
        verify(redisCache, never()).setCacheObject(any(), any());
    }

    @Test
    void selectDictTypeById_delegates()
    {
        SysDictType expected = dictType(1L, "t", "n");
        when(dictTypeMapper.selectDictTypeById(1L)).thenReturn(expected);

        assertSame(expected, service.selectDictTypeById(1L));
    }

    @Test
    void selectDictTypeByType_delegates()
    {
        SysDictType expected = dictType(1L, "t", "n");
        when(dictTypeMapper.selectDictTypeByType("t")).thenReturn(expected);

        assertSame(expected, service.selectDictTypeByType("t"));
    }

    @Test
    void deleteDictTypeByIds_hasDictData_throws()
    {
        when(dictTypeMapper.selectDictTypeById(1L)).thenReturn(dictType(1L, "t", "性别"));
        when(dictDataMapper.countDictDataByType("t")).thenReturn(1);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.deleteDictTypeByIds(new Long[] { 1L }));
        assertEquals("性别已分配,不能删除", ex.getMessage());
        verify(dictTypeMapper, never()).deleteDictTypeById(any());
    }

    @Test
    void deleteDictTypeByIds_normal_deletesAndEvictsCache()
    {
        when(dictTypeMapper.selectDictTypeById(1L)).thenReturn(dictType(1L, "t", "性别"));
        when(dictDataMapper.countDictDataByType("t")).thenReturn(0);

        try (MockedStatic<SpringUtils> ignored = mockSpringUtils())
        {
            service.deleteDictTypeByIds(new Long[] { 1L });
        }

        verify(dictTypeMapper).deleteDictTypeById(1L);
        verify(redisCache).deleteObject("sys_dict:t");
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadingDictCache_groupsByTypeAndSorts()
    {
        SysDictData first = dictData(1L, "t", "B", "2", 2L);
        SysDictData second = dictData(2L, "t", "A", "1", 1L);
        when(dictDataMapper.selectDictDataList(any(SysDictData.class)))
            .thenReturn(Arrays.asList(first, second));

        try (MockedStatic<SpringUtils> ignored = mockSpringUtils())
        {
            service.loadingDictCache();
        }

        ArgumentCaptor<List<SysDictData>> captor = ArgumentCaptor.forClass(List.class);
        verify(redisCache).setCacheObject(eq("sys_dict:t"), captor.capture());
        assertEquals(Arrays.asList(second, first), captor.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void clearDictCache_deletesAllDictKeys()
    {
        Collection<String> keys = Arrays.asList("sys_dict:t1", "sys_dict:t2");
        when(redisCache.keys("sys_dict:*")).thenReturn(keys);

        try (MockedStatic<SpringUtils> ignored = mockSpringUtils())
        {
            service.clearDictCache();
        }

        verify(redisCache).deleteObject(keys);
    }

    @Test
    void resetDictCache_clearsThenLoads()
    {
        Collection<String> keys = Collections.singletonList("sys_dict:t");
        when(redisCache.keys("sys_dict:*")).thenReturn(keys);
        when(dictDataMapper.selectDictDataList(any(SysDictData.class)))
            .thenReturn(Collections.singletonList(dictData(1L, "t", "L", "V", 1L)));

        try (MockedStatic<SpringUtils> ignored = mockSpringUtils())
        {
            service.resetDictCache();
        }

        verify(redisCache).deleteObject(keys);
        verify(redisCache).setCacheObject(eq("sys_dict:t"), any());
    }

    @Test
    void init_loadsDictCache()
    {
        when(dictDataMapper.selectDictDataList(any(SysDictData.class)))
            .thenReturn(Collections.emptyList());

        try (MockedStatic<SpringUtils> ignored = mockSpringUtils())
        {
            service.init();
        }

        verify(dictDataMapper).selectDictDataList(any(SysDictData.class));
    }

    @Test
    void insertDictType_success_initializesCache()
    {
        SysDictType dict = dictType(null, "t", "n");
        when(dictTypeMapper.insertDictType(dict)).thenReturn(1);

        try (MockedStatic<SpringUtils> ignored = mockSpringUtils())
        {
            assertEquals(1, service.insertDictType(dict));
        }

        verify(redisCache).setCacheObject("sys_dict:t", null);
    }

    @Test
    void insertDictType_failure_doesNotCache()
    {
        SysDictType dict = dictType(null, "t", "n");
        when(dictTypeMapper.insertDictType(dict)).thenReturn(0);

        try (MockedStatic<SpringUtils> ignored = mockSpringUtils())
        {
            assertEquals(0, service.insertDictType(dict));
        }

        verify(redisCache, never()).setCacheObject(any(), any());
    }

    @Test
    void updateDictType_success_updatesDataAndCaches()
    {
        SysDictType oldDict = dictType(1L, "old", "旧字典");
        SysDictType dict = dictType(1L, "new", "新字典");
        List<SysDictData> dictDatas = Collections.singletonList(dictData(1L, "new", "L", "V", 1L));
        when(dictTypeMapper.selectDictTypeById(1L)).thenReturn(oldDict);
        when(dictTypeMapper.updateDictType(dict)).thenReturn(1);
        when(dictDataMapper.selectDictDataByType("new")).thenReturn(dictDatas);

        try (MockedStatic<SpringUtils> ignored = mockSpringUtils())
        {
            assertEquals(1, service.updateDictType(dict));
        }

        verify(dictDataMapper).updateDictDataType("old", "new");
        verify(redisCache).setCacheObject("sys_dict:new", dictDatas);
    }

    @Test
    void updateDictType_failure_doesNotCache()
    {
        SysDictType oldDict = dictType(1L, "old", "旧字典");
        SysDictType dict = dictType(1L, "new", "新字典");
        when(dictTypeMapper.selectDictTypeById(1L)).thenReturn(oldDict);
        when(dictTypeMapper.updateDictType(dict)).thenReturn(0);

        try (MockedStatic<SpringUtils> ignored = mockSpringUtils())
        {
            assertEquals(0, service.updateDictType(dict));
        }

        verify(redisCache, never()).setCacheObject(any(), any());
    }

    @Test
    void checkDictTypeUnique_notExists_returnsUnique()
    {
        when(dictTypeMapper.checkDictTypeUnique("t")).thenReturn(null);

        assertTrue(service.checkDictTypeUnique(dictType(null, "t", "n")));
    }

    @Test
    void checkDictTypeUnique_sameId_returnsUnique()
    {
        when(dictTypeMapper.checkDictTypeUnique("t")).thenReturn(dictType(1L, "t", "n"));

        assertTrue(service.checkDictTypeUnique(dictType(1L, "t", "n")));
    }

    @Test
    void checkDictTypeUnique_otherId_returnsNotUnique()
    {
        when(dictTypeMapper.checkDictTypeUnique("t")).thenReturn(dictType(2L, "t", "n"));

        assertFalse(service.checkDictTypeUnique(dictType(1L, "t", "n")));
    }
}
