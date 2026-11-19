package ai.lawyers.web.controller.lawyers.collab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiKnowledgeAudit;
import ai.lawyers.system.service.lawyers.IAiKnowledgeAuditService;

/**
 * 法律知识审核发布接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiKnowledgeAuditControllerTest
{
    @Mock
    private IAiKnowledgeAuditService knowledgeAuditService;

    @InjectMocks
    private AiKnowledgeAuditController controller;

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
    void 审核流水返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiKnowledgeAudit query = new AiKnowledgeAudit();
        List<AiKnowledgeAudit> list = new ArrayList<AiKnowledgeAudit>();
        list.add(new AiKnowledgeAudit());
        when(knowledgeAuditService.selectAuditList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
    }

    @Test
    void 提交审核携带当前用户()
    {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("editor"));
            AjaxResult result = controller.submit(5L);

            assertEquals("已提交审核", result.get(AjaxResult.MSG_TAG));
        }
        verify(knowledgeAuditService).submit(5L, "editor");
    }

    @Test
    void 审核通过读取意见字段()
    {
        Map<String, String> body = new HashMap<String, String>();
        body.put("opinion", "内容准确");
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("auditor"));
            assertEquals("审核通过", controller.approve(5L, body).get(AjaxResult.MSG_TAG));
        }
        verify(knowledgeAuditService).approve(5L, "内容准确", "auditor");
    }

    @Test
    void 审核驳回兼容auditRemark且允许空body()
    {
        Map<String, String> body = new HashMap<String, String>();
        body.put("auditRemark", "法条已失效");
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("auditor"));
            assertEquals("已驳回", controller.reject(5L, body).get(AjaxResult.MSG_TAG));
            controller.reject(6L, null);
        }
        verify(knowledgeAuditService).reject(5L, "法条已失效", "auditor");
        verify(knowledgeAuditService).reject(6L, null, "auditor");
    }

    @Test
    void 发布与下线返回状态提示()
    {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("auditor"));
            assertEquals("已发布", controller.publish(5L, null).get(AjaxResult.MSG_TAG));
            assertEquals("已下线", controller.offline(5L, null).get(AjaxResult.MSG_TAG));
        }
        verify(knowledgeAuditService).publish(5L, null, "auditor");
        verify(knowledgeAuditService).offline(5L, null, "auditor");
    }
}
