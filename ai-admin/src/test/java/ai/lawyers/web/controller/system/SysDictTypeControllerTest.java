package ai.lawyers.web.controller.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
import ai.lawyers.common.core.domain.entity.SysDictType;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.service.ISysDictTypeService;

/**
 * 字典类型接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class SysDictTypeControllerTest
{
    @Mock
    private ISysDictTypeService dictTypeService;

    @InjectMocks
    private SysDictTypeController controller;

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

    private SysDictType dictType(String name)
    {
        SysDictType dictType = new SysDictType();
        dictType.setDictName(name);
        return dictType;
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        SysDictType query = new SysDictType();
        List<SysDictType> list = new ArrayList<SysDictType>();
        list.add(new SysDictType());
        when(dictTypeService.selectDictTypeList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(dictTypeService.selectDictTypeList(any(SysDictType.class)))
                .thenReturn(List.of(new SysDictType()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new SysDictType());

        assertTrue(response.getContentType().contains("spreadsheetml"));
    }

    @Test
    void 详情与下拉列表透传服务结果()
    {
        SysDictType dictType = new SysDictType();
        dictType.setDictId(5L);
        List<SysDictType> all = new ArrayList<SysDictType>();
        when(dictTypeService.selectDictTypeById(5L)).thenReturn(dictType);
        when(dictTypeService.selectDictTypeAll()).thenReturn(all);

        assertSame(dictType, controller.getInfo(5L).get(AjaxResult.DATA_TAG));
        assertSame(all, controller.optionselect().get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增字典校验唯一后写入操作人()
    {
        SysDictType dictType = dictType("性别");
        when(dictTypeService.checkDictTypeUnique(dictType)).thenReturn(true);
        when(dictTypeService.insertDictType(dictType)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            assertEquals(200, controller.add(dictType).get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", dictType.getCreateBy());
    }

    @Test
    void 新增字典重复返回错误()
    {
        SysDictType dictType = dictType("性别");
        when(dictTypeService.checkDictTypeUnique(dictType)).thenReturn(false);

        AjaxResult result = controller.add(dictType);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("新增字典'性别'失败，字典类型已存在", result.get(AjaxResult.MSG_TAG));
        verify(dictTypeService, never()).insertDictType(any(SysDictType.class));
    }

    @Test
    void 修改字典写入更新人()
    {
        SysDictType dictType = dictType("性别");
        when(dictTypeService.checkDictTypeUnique(dictType)).thenReturn(true);
        when(dictTypeService.updateDictType(dictType)).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("auditor"));
            assertEquals(500, controller.edit(dictType).get(AjaxResult.CODE_TAG));
        }
        assertEquals("auditor", dictType.getUpdateBy());
    }

    @Test
    void 修改字典重复返回错误()
    {
        SysDictType dictType = dictType("性别");
        when(dictTypeService.checkDictTypeUnique(dictType)).thenReturn(false);

        assertEquals("修改字典'性别'失败，字典类型已存在",
                controller.edit(dictType).get(AjaxResult.MSG_TAG));
        verify(dictTypeService, never()).updateDictType(any(SysDictType.class));
    }

    @Test
    void 删除与刷新缓存均返回成功()
    {
        Long[] dictIds = new Long[] { 1L, 2L };

        assertEquals(200, controller.remove(dictIds).get(AjaxResult.CODE_TAG));
        assertEquals(200, controller.refreshCache().get(AjaxResult.CODE_TAG));
        verify(dictTypeService).deleteDictTypeByIds(dictIds);
        verify(dictTypeService).resetDictCache();
    }
}
