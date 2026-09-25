package ai.lawyers.web.controller.lawyers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.service.lawyers.queue.QueueNames;
import ai.lawyers.system.service.lawyers.queue.StreamQueueService;

/**
 * Stream 异步队列监控 Controller（P3-H1）。
 *
 * <p>提供队列水位概览（Stream 长度 / pending 积压 / 死信数）、死信列表、
 * 死信重投与删除能力。队列范围限定为 {@link StreamQueueService#registeredQueues()}
 * （已注册消费处理器的 5 个内置队列），防止任意 Redis key 探测。</p>
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/queueMonitor")
public class AiQueueMonitorController extends BaseController
{
    @Autowired
    private StreamQueueService streamQueueService;

    /** 队列水位概览：每队列一行（queue/streamLen/pending/dead） */
    @PreAuthorize("@ss.hasPermi('lawyers:queueMonitor:list')")
    @GetMapping("/list")
    public AjaxResult list()
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String queue : streamQueueService.registeredQueues())
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("queue", queue);
            row.put("streamKey", QueueNames.streamKey(queue));
            row.put("streamLen", streamQueueService.streamLength(queue));
            row.put("pending", streamQueueService.pendingCount(queue));
            row.put("dead", streamQueueService.deadLetterCount(queue));
            rows.add(row);
        }
        return success(rows);
    }

    /** 死信列表（倒序分页） */
    @PreAuthorize("@ss.hasPermi('lawyers:queueMonitor:list')")
    @GetMapping("/dead/list")
    public TableDataInfo deadList(String queue, Integer pageNum, Integer pageSize)
    {
        if (!isKnownQueue(queue))
        {
            return getDataTable(new ArrayList<>());
        }
        int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        List<Map<String, Object>> rows = streamQueueService.deadLetterList(queue, (page - 1) * size, size);
        TableDataInfo table = getDataTable(rows);
        long total = streamQueueService.deadLetterCount(queue);
        table.setTotal(total < 0 ? 0 : total);
        return table;
    }

    /** 死信重投：回投原队列 Stream 后删除死信记录（消费端幂等保证不重复生效） */
    @PreAuthorize("@ss.hasPermi('lawyers:queueMonitor:replay')")
    @PostMapping("/dead/replay")
    public AjaxResult replay(String queue, String id)
    {
        if (!isKnownQueue(queue) || StringUtils.isEmpty(id))
        {
            return error("参数不完整或队列不存在");
        }
        return streamQueueService.replayDeadLetter(queue, id) ? success("重投成功") : error("重投失败：记录不存在或信封不完整");
    }

    /** 死信删除（人工确认无需重投） */
    @PreAuthorize("@ss.hasPermi('lawyers:queueMonitor:replay')")
    @DeleteMapping("/dead/{queue}/{id}")
    public AjaxResult removeDead(@PathVariable("queue") String queue, @PathVariable("id") String id)
    {
        if (!isKnownQueue(queue))
        {
            return error("队列不存在");
        }
        return streamQueueService.deleteDeadLetter(queue, id) ? success("删除成功") : error("删除失败：记录不存在");
    }

    /** 队列白名单校验：仅允许已注册消费队列，防止任意 Stream key 探测/写入 */
    private boolean isKnownQueue(String queue)
    {
        return StringUtils.isNotEmpty(queue) && streamQueueService.registeredQueues().contains(queue);
    }
}
