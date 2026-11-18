package ai.lawyers.web.controller.lawyers.collab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiSlaPolicy;
import ai.lawyers.system.service.lawyers.IAiSlaPolicyService;

/**
 * 工单 SLA 策略接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiSlaPolicyControllerTest
{
    @Mock
    private IAiSlaPolicyService slaPolicyService;

    @InjectMocks
    private AiSlaPolicyController controller;

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
        AiSlaPolicy query = new AiSlaPolicy();
        List<AiSlaPolicy> policies = new ArrayList<AiSlaPolicy>();
        policies.add(new AiSlaPolicy());
        policies.add(new AiSlaPolicy());
        when(slaPolicyService.selectAiSlaPolicyList(query)).thenReturn(policies);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(2, table.getTotal());
        assertEquals(policies, table.getRows());
    }

    @Test
    void 详情透传服务结果()
    {
        AiSlaPolicy policy = new AiSlaPolicy();
        policy.setPolicyId(3L);
        when(slaPolicyService.selectAiSlaPolicyByPolicyId(3L)).thenReturn(policy);

        AjaxResult result = controller.getInfo(3L);

        assertSame(policy, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增写入操作人()
    {
        AiSlaPolicy policy = new AiSlaPolicy();
        when(slaPolicyService.insertAiSlaPolicy(any(AiSlaPolicy.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("sla-admin"));
            AjaxResult result = controller.add(policy);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("sla-admin", policy.getCreateBy());
        verify(slaPolicyService).insertAiSlaPolicy(policy);
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] policyIds = new Long[] { 1L, 2L };
        when(slaPolicyService.deleteAiSlaPolicyByPolicyIds(policyIds)).thenReturn(2);

        AjaxResult result = controller.remove(policyIds);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(slaPolicyService).deleteAiSlaPolicyByPolicyIds(policyIds);
    }
}
