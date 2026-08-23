package ai.lawyers.system.task;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.service.lawyers.CallEventPublisher;

/**
 * 工单 SLA 超时提醒定时任务
 *
 * <p>每 5 分钟扫描一次 status=0(待处理) 且 due_time 已过、
 * 尚未标记超时(overtime_flag=0)的工单：</p>
 * <ol>
 *   <li>更新 overtime_flag=1，记录 remind_time；</li>
 *   <li>通过 {@link CallEventPublisher} 向管理员广播 TICKET_OVERTIME 事件；</li>
 *   <li>无事件发布器时至少记录 warn 日志。</li>
 * </ol>
 *
 * <p>主程序已开启 @EnableScheduling，多实例部署时建议只在一个实例执行，
 * 或改造为分布式锁调度。</p>
 *
 * @author ai-lawyers
 */
@Component
public class TicketSlaScheduleTask
{
    private static final Logger log = LoggerFactory.getLogger(TicketSlaScheduleTask.class);

    @Autowired
    private AiCallTicketMapper aiCallTicketMapper;

    @Autowired(required = false)
    private CallEventPublisher callEventPublisher;

    /**
     * 每 5 分钟扫描一次超时工单。
     */
    @Scheduled(fixedDelayString = "${call.ticket.slaScanIntervalMs:300000}", initialDelay = 60000)
    public void scanOvertimeTickets()
    {
        try
        {
            List<AiCallTicket> overdue = aiCallTicketMapper.selectOvertimePendingTickets();
            if (overdue == null || overdue.isEmpty())
            {
                return;
            }
            Date now = new Date();
            for (AiCallTicket ticket : overdue)
            {
                try
                {
                    int rc = aiCallTicketMapper.markOvertime(ticket.getTicketId(), now);
                    if (rc > 0)
                    {
                        log.warn("[SLA] 工单超时: ticketId={} ticketNo={} title={} dueTime={}",
                                ticket.getTicketId(), ticket.getTicketNo(),
                                ticket.getTitle(), ticket.getDueTime());
                        publishOvertimeEvent(ticket);
                    }
                }
                catch (Exception ex)
                {
                    log.error("[SLA] 标记工单超时失败 ticketId={}", ticket.getTicketId(), ex);
                }
            }
        }
        catch (Exception e)
        {
            log.error("[SLA] 工单超时扫描任务异常", e);
        }
    }

    private void publishOvertimeEvent(AiCallTicket ticket)
    {
        Map<String, Object> data = new HashMap<>();
        data.put("ticketId", ticket.getTicketId());
        data.put("ticketNo", ticket.getTicketNo());
        data.put("title", ticket.getTitle());
        data.put("priority", ticket.getPriority());
        data.put("dueTime", ticket.getDueTime());
        data.put("callerNumber", ticket.getCallerNumber());
        data.put("ts", System.currentTimeMillis());
        if (callEventPublisher != null)
        {
            callEventPublisher.broadcast("TICKET_OVERTIME", data);
        }
    }
}
