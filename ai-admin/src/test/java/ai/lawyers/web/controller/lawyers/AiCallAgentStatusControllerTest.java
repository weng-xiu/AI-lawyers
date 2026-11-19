package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.utils.sign.CallbackSignUtils;
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;

/**
 * 坐席状态接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiCallAgentStatusControllerTest
{
    @Mock
    private IAiCallAgentStatusService agentStatusService;

    @InjectMocks
    private AiCallAgentStatusController controller;

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

    private Map<String, Object> params(Object... kv)
    {
        Map<String, Object> map = new HashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2)
        {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiCallAgentStatus query = new AiCallAgentStatus();
        List<AiCallAgentStatus> list = new ArrayList<AiCallAgentStatus>();
        list.add(new AiCallAgentStatus());
        when(agentStatusService.selectAiCallAgentStatusList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(list, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(agentStatusService.selectAiCallAgentStatusList(any(AiCallAgentStatus.class)))
                .thenReturn(List.of(new AiCallAgentStatus()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiCallAgentStatus());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情透传服务结果()
    {
        AiCallAgentStatus agent = new AiCallAgentStatus();
        when(agentStatusService.selectAiCallAgentStatusByAgentId(5L)).thenReturn(agent);

        assertSame(agent, controller.getInfo(5L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增写操作人()
    {
        AiCallAgentStatus agent = new AiCallAgentStatus();
        when(agentStatusService.insertAiCallAgentStatus(agent)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            assertEquals(200, controller.add(agent).get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", agent.getCreateBy());
    }

    @Test
    void 修改写更新人()
    {
        AiCallAgentStatus agent = new AiCallAgentStatus();
        when(agentStatusService.updateAiCallAgentStatus(agent)).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            assertEquals(500, controller.edit(agent).get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", agent.getUpdateBy());
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] agentIds = new Long[] { 1L, 2L };
        when(agentStatusService.deleteAiCallAgentStatusByAgentIds(agentIds)).thenReturn(2);

        assertEquals(200, controller.remove(agentIds).get(AjaxResult.CODE_TAG));
        verify(agentStatusService).deleteAiCallAgentStatusByAgentIds(agentIds);
    }

    @Test
    void 坐席登录返回三种结果()
    {
        when(agentStatusService.agentLogin(1L, 2L, "10.0.0.1")).thenReturn(1, -1, 0);

        assertEquals("坐席登录成功", controller.agentLogin(params("agentId", 1, "userId", 2, "ip", "10.0.0.1"))
                .get(AjaxResult.MSG_TAG));
        assertEquals("该工号已绑定其他账号，请使用本人绑定的工号签入",
                controller.agentLogin(params("agentId", 1, "userId", 2, "ip", "10.0.0.1")).get(AjaxResult.MSG_TAG));
        assertEquals("坐席登录失败，坐席不存在",
                controller.agentLogin(params("agentId", 1, "userId", 2, "ip", "10.0.0.1")).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 坐席注销成功与失败()
    {
        when(agentStatusService.agentLogout(1L, 2L)).thenReturn(1, 0);
        Map<String, Object> params = params("agentId", 1, "userId", 2);

        assertEquals("坐席注销成功", controller.agentLogout(params).get(AjaxResult.MSG_TAG));
        assertEquals("坐席注销失败", controller.agentLogout(params).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 我的坐席按当前用户查询()
    {
        AiCallAgentStatus agent = new AiCallAgentStatus();
        when(agentStatusService.selectAiCallAgentStatusByUserId(1L)).thenReturn(agent);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            assertSame(agent, controller.myAgent().get(AjaxResult.DATA_TAG));
        }
    }

    @Test
    void 自动签出已登录直接签出()
    {
        when(agentStatusService.agentLogout(1L, 2L)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            assertEquals(200, controller.autoLogout(1L, 2L, new MockHttpServletRequest()).get(AjaxResult.CODE_TAG));
        }
    }

    @Test
    void 自动签出签名校验失败返回403()
    {
        ReflectionTestUtils.setField(controller, "callbackSignEnabled", true);
        ReflectionTestUtils.setField(controller, "callbackSignSecret", "secret");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Callback-Timestamp", "1");
        request.addHeader("X-Callback-Sign", "bad");

        try (MockedStatic<CallbackSignUtils> sign = mockStatic(CallbackSignUtils.class))
        {
            sign.when(() -> CallbackSignUtils.verify("secret", "1|2", "1", "bad")).thenReturn(false);
            AjaxResult result = controller.autoLogout(1L, 2L, request);

            assertEquals(403, result.get(AjaxResult.CODE_TAG));
            assertEquals("回调签名校验失败", result.get(AjaxResult.MSG_TAG));
        }
        verify(agentStatusService, never()).agentLogout(anyLong(), anyLong());
    }

    @Test
    void 自动签出签名通过后签出()
    {
        ReflectionTestUtils.setField(controller, "callbackSignEnabled", true);
        ReflectionTestUtils.setField(controller, "callbackSignSecret", "secret");
        when(agentStatusService.agentLogout(1L, 2L)).thenReturn(1);

        try (MockedStatic<CallbackSignUtils> sign = mockStatic(CallbackSignUtils.class))
        {
            sign.when(() -> CallbackSignUtils.verify(anyString(), anyString(), any(), any())).thenReturn(true);
            assertEquals(200, controller.autoLogout(1L, 2L, new MockHttpServletRequest()).get(AjaxResult.CODE_TAG));
        }
    }

    @Test
    void 自动签出无在线坐席返回提示()
    {
        when(agentStatusService.agentLogout(1L, 2L)).thenReturn(0);

        AjaxResult result = controller.autoLogout(1L, 2L, new MockHttpServletRequest());

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("无在线坐席需要签出", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void 状态切换成功与失败()
    {
        when(agentStatusService.updateAgentStatus(1L, "busy")).thenReturn(1, 0);
        Map<String, Object> params = params("agentId", 1, "status", "busy");

        assertEquals("状态切换成功", controller.updateAgentStatus(params).get(AjaxResult.MSG_TAG));
        assertEquals("状态切换失败", controller.updateAgentStatus(params).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 在线坐席与今日记录透传()
    {
        List<AiCallAgentStatus> online = List.of(new AiCallAgentStatus());
        List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
        records.add(new HashMap<String, Object>());
        when(agentStatusService.selectOnlineAgents()).thenReturn(online);
        when(agentStatusService.selectTodayRecordsByAgentId(1L)).thenReturn(records);

        assertSame(online, controller.getOnlineAgents().get(AjaxResult.DATA_TAG));
        assertSame(records, controller.getTodayRecords(1L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 应答模式切换成功与失败()
    {
        when(agentStatusService.updateCallMode(1L, "auto")).thenReturn(1, 0);
        Map<String, Object> params = params("agentId", 1, "callMode", "auto");

        assertEquals("应答模式切换成功", controller.updateCallMode(params).get(AjaxResult.MSG_TAG));
        assertEquals("应答模式切换失败", controller.updateCallMode(params).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 外呼成功与坐席不存在()
    {
        AiCallAgentStatus agent = new AiCallAgentStatus();
        when(agentStatusService.makeCall(1L, "13800000000")).thenReturn(agent, (AiCallAgentStatus) null);
        Map<String, Object> params = params("agentId", 1, "phone", "13800000000");

        assertSame(agent, controller.makeCall(params).get(AjaxResult.DATA_TAG));
        assertEquals("外呼失败，坐席不存在", controller.makeCall(params).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 保持与恢复返回提示()
    {
        when(agentStatusService.holdCall(1L)).thenReturn(1);
        when(agentStatusService.resumeCall(1L)).thenReturn(0);
        Map<String, Object> params = params("agentId", 1);

        assertEquals("通话已保持", controller.holdCall(params).get(AjaxResult.MSG_TAG));
        assertEquals("通话恢复失败，当前未处于保持状态", controller.resumeCall(params).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 转接咨询与三方通话委托服务()
    {
        when(agentStatusService.transferCall(1L, 2L, "")).thenReturn(1);
        when(agentStatusService.consultCall(1L, 2L)).thenReturn(1);
        when(agentStatusService.threeWayCall(1L, 2L)).thenReturn(0);
        Map<String, Object> params = params("agentId", 1, "toAgentId", 2);

        assertEquals("通话已转接", controller.transferCall(params).get(AjaxResult.MSG_TAG));
        assertEquals("咨询已发起", controller.consultCall(params).get(AjaxResult.MSG_TAG));
        assertEquals("三方通话失败，当前无通话或状态异常", controller.threeWayCall(params).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 话后整理挂机机器人接管与IVR转接()
    {
        when(agentStatusService.afterWork(1L)).thenReturn(1);
        when(agentStatusService.hangup(1L)).thenReturn(1);
        when(agentStatusService.robotTakeover(1L)).thenReturn(0);
        when(agentStatusService.ivrTransfer(1L, "")).thenReturn(1);
        Map<String, Object> params = params("agentId", 1);

        assertEquals("已进入话后整理", controller.afterWork(params).get(AjaxResult.MSG_TAG));
        assertEquals("通话已挂断", controller.hangup(params).get(AjaxResult.MSG_TAG));
        assertEquals("机器人接管失败，当前无通话", controller.robotTakeover(params).get(AjaxResult.MSG_TAG));
        assertEquals("已转接至IVR", controller.ivrTransfer(params).get(AjaxResult.MSG_TAG));
    }
}
