package ai.lawyers.web.controller.lawyers.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.system.domain.lawyers.skill.AiCallQueue;
import ai.lawyers.system.domain.lawyers.skill.DispatchContext;
import ai.lawyers.system.domain.lawyers.skill.DispatchResult;
import ai.lawyers.system.mapper.lawyers.skill.AiCallQueueMapper;
import ai.lawyers.system.service.lawyers.skill.IAgentDispatchService;

/**
 * 排队监控与智能分配接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AgentDispatchControllerTest
{
    @Mock
    private IAgentDispatchService agentDispatchService;

    @Mock
    private AiCallQueueMapper aiCallQueueMapper;

    @InjectMocks
    private AgentDispatchController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private DispatchResult result(boolean success, String message)
    {
        DispatchResult result = new DispatchResult();
        result.setSuccess(success);
        result.setMessage(message);
        return result;
    }

    @Test
    void 排队流水返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiCallQueue query = new AiCallQueue();
        List<AiCallQueue> queues = new ArrayList<AiCallQueue>();
        queues.add(new AiCallQueue());
        when(aiCallQueueMapper.selectAiCallQueueList(query)).thenReturn(queues);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(queues, table.getRows());
    }

    @Test
    void 实时排队列表按技能组查询()
    {
        List<AiCallQueue> queues = new ArrayList<AiCallQueue>();
        when(agentDispatchService.listQueuing(3L)).thenReturn(queues);

        AjaxResult result = controller.queuing(3L);

        assertSame(queues, result.get(AjaxResult.DATA_TAG));
        verify(agentDispatchService).listQueuing(3L);
    }

    @Test
    void 触发分配时为空上下文补默认值()
    {
        DispatchResult dispatchResult = result(true, "已分配");
        when(agentDispatchService.dispatch(eq(3L), any(DispatchContext.class))).thenReturn(dispatchResult);

        AjaxResult result = controller.dispatch(3L, null);

        assertSame(dispatchResult, result.get(AjaxResult.DATA_TAG));
        verify(agentDispatchService).dispatch(eq(3L), any(DispatchContext.class));
    }

    @Test
    void 手动分配失败返回错误消息()
    {
        when(agentDispatchService.assignManually(5L, 9L)).thenReturn(result(false, "坐席忙线"));

        AjaxResult result = controller.assignManually(5L, 9L);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("坐席忙线", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void 手动分配成功透传结果()
    {
        DispatchResult dispatchResult = result(true, "已分配");
        when(agentDispatchService.assignManually(5L, 9L)).thenReturn(dispatchResult);

        AjaxResult result = controller.assignManually(5L, 9L);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        assertSame(dispatchResult, result.get(AjaxResult.DATA_TAG));
        assertTrue(dispatchResult.isSuccess());
        assertEquals("操作成功", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void 踢除排队透传影响行数()
    {
        when(agentDispatchService.kickQueue(5L, "客户已挂断")).thenReturn(1);

        AjaxResult result = controller.kick(5L, "客户已挂断");

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(agentDispatchService).kickQueue(5L, "客户已挂断");
    }
}
