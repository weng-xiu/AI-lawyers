package ai.lawyers.framework.manager;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.service.lawyers.cluster.InstanceDrainState;
import ai.lawyers.system.service.lawyers.trunk.gateway.ami.AmiEventBridgeService;
import ai.lawyers.system.service.lawyers.trunk.gateway.esl.EslEventBridgeService;
import ai.lawyers.framework.websocket.CallWebSocketServer;
import ai.lawyers.framework.websocket.ChatWebSocketServer;

/**
 * P3-C6：优雅停机排空协调器（无损发布代码侧收口）。
 *
 * <p>Spring 关闭顺序：ContextClosedEvent（本协调器执行）→ SmartLifecycle stop
 * （Tomcat 优雅等待在途 HTTP）→ 单例 Bean 销毁（@PreDestroy 释放 ESL/线程池等）。
 * 本协调器在最早阶段完成：
 * <ol>
 *   <li>置排空态：readiness 探针 OUT_OF_SERVICE，nginx/负载均衡摘除流量，外呼扫描停领新任务；</li>
 *   <li>通知本机 WS 坐席/对话客户端 SERVER_DRAINING，前端立即重连到存活实例；</li>
 *   <li>主动让出 ESL 消费者角色，存活实例快速接收入站事件；</li>
 *   <li>轮询在途通话（拨号中/振铃/已接通）至归零或超时；</li>
 *   <li>超时仍有在途通话则强制退出并告警，后续由对账批次补偿事件。</li>
 * </ol>
 * 参数：app.shutdown.drain.*；maxWaitSeconds 须小于 spring.lifecycle.timeout-per-shutdown-phase。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GracefulDrainCoordinator implements ApplicationListener<ContextClosedEvent>
{
    private static final Logger log = LoggerFactory.getLogger("sys-user");

    private final AtomicBoolean executed = new AtomicBoolean(false);

    @Autowired
    private InstanceDrainState drainState;

    @Autowired
    private AiCallDialLogMapper dialLogMapper;

    @Autowired(required = false)
    private EslEventBridgeService eslBridge;

    /** P3-B1：AMI 事件桥（启用 Asterisk 时同款让位） */
    @Autowired(required = false)
    private AmiEventBridgeService amiBridge;

    @Value("${app.shutdown.drain.max-wait-seconds:30}")
    private int maxWaitSeconds;

    @Value("${app.shutdown.drain.poll-interval-seconds:1}")
    private int pollIntervalSeconds;

    @Value("${app.shutdown.drain.notify-websocket:true}")
    private boolean notifyWebsocket;

    @Value("${app.shutdown.drain.settle-ms:1000}")
    private int settleMs;

    @Override
    public void onApplicationEvent(ContextClosedEvent event)
    {
        if (!executed.compareAndSet(false, true))
        {
            return;
        }
        long start = System.currentTimeMillis();
        log.info("====C6 优雅停机排空开始 maxWait={}s poll={}s====", maxWaitSeconds, pollIntervalSeconds);
        try
        {
            // 1) 排空态：探针翻转（摘流量）+ 外呼 gate
            drainState.beginDrain();

            // 2) 通知本机 WS 客户端重连
            if (notifyWebsocket)
            {
                int callConns = CallWebSocketServer.notifyDrainingLocal();
                int chatConns = ChatWebSocketServer.notifyDrainingLocal();
                log.info("排空 WS 通知已投递: 坐席连接={} 对话连接={}", callConns, chatConns);
            }

            // 3) 让出 ESL/AMI 消费者，存活实例接收入站呼叫事件
            if (eslBridge != null)
            {
                try
                {
                    eslBridge.yieldForDrain();
                }
                catch (Exception e)
                {
                    log.warn("排空时让出 ESL 角色异常（不阻断停机）: {}", e.getMessage());
                }
            }
            if (amiBridge != null)
            {
                try
                {
                    amiBridge.yieldForDrain();
                }
                catch (Exception e)
                {
                    log.warn("排空时让出 AMI 角色异常（不阻断停机）: {}", e.getMessage());
                }
            }

            // 4) 短暂 settle：等待探针摘流量传播、WS 重连、ESL 接管
            sleepQuietly(settleMs);

            // 5) 等待在途通话归零
            waitActiveCallsDrained(start);
        }
        catch (Exception e)
        {
            log.error("优雅停机排空异常，继续退出流程", e);
        }
        log.info("====C6 优雅停机排空结束，耗时={}ms====", System.currentTimeMillis() - start);
    }

    /** 轮询在途通话数，归零或超过 maxWaitSeconds 退出 */
    private void waitActiveCallsDrained(long start)
    {
        long maxWaitMs = Math.max(1, maxWaitSeconds) * 1000L;
        long pollMs = Math.max(1, pollIntervalSeconds) * 1000L;
        Integer active = countActive();
        if (active == null)
        {
            return;
        }
        while (active > 0 && System.currentTimeMillis() - start < maxWaitMs)
        {
            log.info("排空等待在途通话结束: active={}, 已等待={}ms", active,
                    System.currentTimeMillis() - start);
            sleepQuietly(pollMs);
            active = countActive();
            if (active == null)
            {
                return;
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        if (active > 0)
        {
            log.warn("排空超时（{}ms），仍有 {} 通在途通话，强制退出：在途通话被拆线，"
                    + "丢失的事件由后续对账批次补偿（PBX 自身主备/浮动 IP 属部署侧 C6）",
                    elapsed, active);
        }
        else
        {
            log.info("在途通话已全部结束（耗时 {}ms），可安全退出", elapsed);
        }
    }

    /** 统计在途通话；异常时（如关闭期数据源不可用）返回 null，调用方放弃等待 */
    private Integer countActive()
    {
        try
        {
            return dialLogMapper.countActiveDials();
        }
        catch (Exception e)
        {
            log.warn("统计在途通话失败，放弃排空等待: {}", e.getMessage());
            return null;
        }
    }

    private void sleepQuietly(long ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }
}
