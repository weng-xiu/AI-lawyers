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
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.SysConfig;
import ai.lawyers.system.service.ISysConfigService;

/**
 * 参数配置接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class SysConfigControllerTest
{
    @Mock
    private ISysConfigService configService;

    @InjectMocks
    private SysConfigController controller;

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

    private SysConfig config(String name)
    {
        SysConfig config = new SysConfig();
        config.setConfigName(name);
        return config;
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        SysConfig query = new SysConfig();
        List<SysConfig> list = new ArrayList<SysConfig>();
        list.add(new SysConfig());
        when(configService.selectConfigList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(configService.selectConfigList(any(SysConfig.class))).thenReturn(List.of(new SysConfig()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new SysConfig());

        assertTrue(response.getContentType().contains("spreadsheetml"));
    }

    @Test
    void 详情与键名查询透传服务结果()
    {
        SysConfig config = config("账号初始密码");
        when(configService.selectConfigById(5L)).thenReturn(config);
        when(configService.selectConfigByKey("sys.user.initPassword")).thenReturn("123456");

        assertSame(config, controller.getInfo(5L).get(AjaxResult.DATA_TAG));
        assertEquals("123456", controller.getConfigKey("sys.user.initPassword").get(AjaxResult.MSG_TAG));
    }

    @Test
    void 新增参数键名重复返回错误()
    {
        SysConfig config = config("账号初始密码");
        when(configService.checkConfigKeyUnique(config)).thenReturn(false);

        AjaxResult result = controller.add(config);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("新增参数'账号初始密码'失败，参数键名已存在", result.get(AjaxResult.MSG_TAG));
        verify(configService, never()).insertConfig(any(SysConfig.class));
    }

    @Test
    void 新增参数写入操作人()
    {
        SysConfig config = config("账号初始密码");
        when(configService.checkConfigKeyUnique(config)).thenReturn(true);
        when(configService.insertConfig(config)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            assertEquals(200, controller.add(config).get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", config.getCreateBy());
    }

    @Test
    void 修改参数写入更新人()
    {
        SysConfig config = config("账号初始密码");
        when(configService.checkConfigKeyUnique(config)).thenReturn(true);
        when(configService.updateConfig(config)).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("auditor"));
            assertEquals(500, controller.edit(config).get(AjaxResult.CODE_TAG));
        }
        assertEquals("auditor", config.getUpdateBy());
    }

    @Test
    void 修改参数键名重复返回错误()
    {
        SysConfig config = config("账号初始密码");
        when(configService.checkConfigKeyUnique(config)).thenReturn(false);

        assertEquals("修改参数'账号初始密码'失败，参数键名已存在",
                controller.edit(config).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 删除与刷新缓存返回成功()
    {
        Long[] configIds = new Long[] { 1L, 2L };

        assertEquals(200, controller.remove(configIds).get(AjaxResult.CODE_TAG));
        assertEquals(200, controller.refreshCache().get(AjaxResult.CODE_TAG));
        verify(configService).deleteConfigByIds(configIds);
        verify(configService).resetConfigCache();
    }
}
