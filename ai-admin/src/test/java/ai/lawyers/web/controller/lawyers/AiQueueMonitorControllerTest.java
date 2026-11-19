package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.system.service.lawyers.queue.StreamQueueService;

/**
 * Stream 队列监控接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiQueueMonitorControllerTest
{
    @Mock
    private StreamQueueService streamQueueService;

    @InjectMocks
    private AiQueueMonitorController controller;

    private Set<String> queues;

    @BeforeEach
    void setUp()
    {
        queues = new HashSet<String>();
        queues.add("call-result");
    }

    @Test
    void 水位概览逐队列返回指标()
    {
        when(streamQueueService.registeredQueues()).thenReturn(queues);
        when(streamQueueService.streamLength("call-result")).thenReturn(12L);
        when(streamQueueService.pendingCount("call-result")).thenReturn(3L);
        when(streamQueueService.deadLetterCount("call-result")).thenReturn(1L);

        AjaxResult result = controller.list();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get(AjaxResult.DATA_TAG);
        assertEquals(1, rows.size());
        assertEquals("call-result", rows.get(0).get("queue"));
        assertEquals(12L, rows.get(0).get("streamLen"));
        assertEquals(3L, rows.get(0).get("pending"));
        assertEquals(1L, rows.get(0).get("dead"));
    }

    @Test
    void 未知队列直接返回空死信列表()
    {
        when(streamQueueService.registeredQueues()).thenReturn(queues);

        TableDataInfo table = controller.deadList("unknown", 1, 10);

        assertEquals(0, table.getTotal());
        assertTrue(table.getRows().isEmpty());
        verify(streamQueueService, never()).deadLetterList("unknown", 0, 10);
    }

    @Test
    void 死信列表分页并限制每页最大条数()
    {
        when(streamQueueService.registeredQueues()).thenReturn(queues);
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", "1-0");
        rows.add(row);
        when(streamQueueService.deadLetterList("call-result", 5, 5)).thenReturn(rows);
        when(streamQueueService.deadLetterCount("call-result")).thenReturn(250L);

        TableDataInfo table = controller.deadList("call-result", 2, 5);

        assertEquals(250, table.getTotal());
        assertEquals(rows, table.getRows());
        verify(streamQueueService).deadLetterList("call-result", 5, 5);

        TableDataInfo capped = controller.deadList("call-result", 0, 500);
        assertEquals(250, capped.getTotal());
        verify(streamQueueService).deadLetterList("call-result", 0, 100);
    }

    @Test
    void 重投参数缺失或队列未知返回错误()
    {
        when(streamQueueService.registeredQueues()).thenReturn(queues);

        AjaxResult noId = controller.replay("call-result", "");
        AjaxResult unknown = controller.replay("unknown", "1-0");

        assertEquals(500, noId.get(AjaxResult.CODE_TAG));
        assertEquals("参数不完整或队列不存在", noId.get(AjaxResult.MSG_TAG));
        assertEquals(500, unknown.get(AjaxResult.CODE_TAG));
    }

    @Test
    void 重投成功与失败分别返回提示()
    {
        when(streamQueueService.registeredQueues()).thenReturn(queues);
        when(streamQueueService.replayDeadLetter("call-result", "1-0")).thenReturn(true);
        when(streamQueueService.replayDeadLetter("call-result", "2-0")).thenReturn(false);

        AjaxResult ok = controller.replay("call-result", "1-0");
        AjaxResult fail = controller.replay("call-result", "2-0");

        assertEquals("重投成功", ok.get(AjaxResult.MSG_TAG));
        assertEquals(500, fail.get(AjaxResult.CODE_TAG));
        assertEquals("重投失败：记录不存在或信封不完整", fail.get(AjaxResult.MSG_TAG));
    }

    @Test
    void 删除死信按队列白名单校验()
    {
        when(streamQueueService.registeredQueues()).thenReturn(queues);
        when(streamQueueService.deleteDeadLetter("call-result", "1-0")).thenReturn(true);

        AjaxResult unknown = controller.removeDead("unknown", "1-0");
        AjaxResult ok = controller.removeDead("call-result", "1-0");

        assertEquals("队列不存在", unknown.get(AjaxResult.MSG_TAG));
        assertEquals("删除成功", ok.get(AjaxResult.MSG_TAG));
        verify(streamQueueService).deleteDeadLetter("call-result", "1-0");
    }
}
