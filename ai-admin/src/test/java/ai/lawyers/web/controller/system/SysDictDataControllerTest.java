package ai.lawyers.web.controller.system;

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
import ai.lawyers.common.core.domain.entity.SysDictData;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.service.ISysDictDataService;
import ai.lawyers.system.service.ISysDictTypeService;

/**
 * 字典数据接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class SysDictDataControllerTest
{
    @Mock
    private ISysDictDataService dictDataService;

    @Mock
    private ISysDictTypeService dictTypeService;

    @InjectMocks
    private SysDictDataController controller;

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

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        SysDictData query = new SysDictData();
        List<SysDictData> list = new ArrayList<SysDictData>();
        list.add(new SysDictData());
        when(dictDataService.selectDictDataList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(list, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(dictDataService.selectDictDataList(any(SysDictData.class)))
                .thenReturn(List.of(new SysDictData()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new SysDictData());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情透传服务结果()
    {
        SysDictData data = new SysDictData();
        data.setDictCode(5L);
        when(dictDataService.selectDictDataById(5L)).thenReturn(data);

        AjaxResult result = controller.getInfo(5L);

        assertSame(data, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 按类型查询为空时返回空集合()
    {
        List<SysDictData> data = new ArrayList<SysDictData>();
        when(dictTypeService.selectDictDataByType("sys_yes_no")).thenReturn(data);
        when(dictTypeService.selectDictDataByType("missing")).thenReturn(null);

        assertSame(data, controller.dictType("sys_yes_no").get(AjaxResult.DATA_TAG));
        assertEquals(0, ((List<?>) controller.dictType("missing").get(AjaxResult.DATA_TAG)).size());
    }

    @Test
    void 新增写入操作人()
    {
        SysDictData data = new SysDictData();
        when(dictDataService.insertDictData(data)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            AjaxResult result = controller.add(data);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", data.getCreateBy());
    }

    @Test
    void 修改写入更新人()
    {
        SysDictData data = new SysDictData();
        when(dictDataService.updateDictData(data)).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("auditor"));
            AjaxResult result = controller.edit(data);
            assertEquals(500, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("auditor", data.getUpdateBy());
    }

    @Test
    void 批量删除后返回成功()
    {
        Long[] dictCodes = new Long[] { 1L, 2L };

        AjaxResult result = controller.remove(dictCodes);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(dictDataService).deleteDictDataByIds(dictCodes);
    }
}
