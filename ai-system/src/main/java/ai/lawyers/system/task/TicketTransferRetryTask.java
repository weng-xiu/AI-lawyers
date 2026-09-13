package ai.lawyers.system.task;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ai.lawyers.system.service.lawyers.IAiTicketTransferService;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * F3 转办推送重试定时任务
 *
 * <p>每 2 分钟扫描到达重试时间的失败/处理中转办流水（指数退避，超最大次数转人工）。
 * 多实例通过 {@link RedisLeaderLock} 单主锁保证全组仅一个实例执行。</p>
 *
 * @author ai-lawyers
 */
@Component
public class TicketTransferRetryTask
{
    private static final Logger log = LoggerFactory.getLogger(TicketTransferRetryTask.class);

    private static final String LOCK_NAME = "job:ticket-transfer-retry";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    @Autowired
    private IAiTicketTransferService ticketTransferService;

    @Autowired
    private RedisLeaderLock leaderLock;

    @Scheduled(fixedDelayString = "${call.collab.retryIntervalMs:120000}", initialDelay = 90000)
    public void retryPendingTransfers()
    {
        leaderLock.tryRun(LOCK_NAME, LOCK_TTL, () ->
        {
            try
            {
                int count = ticketTransferService.processRetryQueue();
                if (count > 0)
                {
                    log.info("[F3] 转办重试队列本轮处理 {} 条", count);
                }
            }
            catch (Exception e)
            {
                log.error("[F3] 转办重试任务异常", e);
            }
        });
    }
}
