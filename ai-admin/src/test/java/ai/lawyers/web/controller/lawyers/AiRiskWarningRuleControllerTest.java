package ai.lawyers.web.controller.lawyers;

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
import ai.lawyers.system.domain.lawyers.AiRiskWarningRule;
import ai.lawyers.system.service.lawyers.IAiRiskWarningRuleService;

/**
 * 风险预警规则接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiRiskWarningRuleControllerTest
{
    @Mock
    private IAiRiskWarningRuleService aiRiskWarningRuleService;

    @InjectMocks
    private AiRiskWarningRuleController controller;

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
        AiRiskWarningRule query = new AiRiskWarningRule();
        List<AiRiskWarningRule> rules = new ArrayList<AiRiskWarningRule>();
        rules.add(new AiRiskWarningRule());
        when(aiRiskWarningRuleService.selectAiRiskWarningRuleList(query)).thenReturn(rules);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(rules, table.getRows());
    }

    @Test
    void 详情透传服务结果()
    {
        AiRiskWarningRule rule = new AiRiskWarningRule();
        rule.setRuleId(6L);
        when(aiRiskWarningRuleService.selectAiRiskWarningRuleByRuleId(6L)).thenReturn(rule);

        AjaxResult result = controller.getInfo(6L);

        assertSame(rule, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增写入操作人()
    {
        AiRiskWarningRule rule = new AiRiskWarningRule();
        when(aiRiskWarningRuleService.insertAiRiskWarningRule(any(AiRiskWarningRule.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            AjaxResult result = controller.add(rule);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", rule.getCreateBy());
        verify(aiRiskWarningRuleService).insertAiRiskWarningRule(rule);
    }

    @Test
    void 修改写入更新人()
    {
        AiRiskWarningRule rule = new AiRiskWarningRule();
        when(aiRiskWarningRuleService.updateAiRiskWarningRule(any(AiRiskWarningRule.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("auditor"));
            AjaxResult result = controller.edit(rule);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("auditor", rule.getUpdateBy());
        verify(aiRiskWarningRuleService).updateAiRiskWarningRule(rule);
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] ruleIds = new Long[] { 1L, 2L, 3L };
        when(aiRiskWarningRuleService.deleteAiRiskWarningRuleByRuleIds(ruleIds)).thenReturn(3);

        AjaxResult result = controller.remove(ruleIds);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiRiskWarningRuleService).deleteAiRiskWarningRuleByRuleIds(ruleIds);
    }
}
