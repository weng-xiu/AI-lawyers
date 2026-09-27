package ai.lawyers.system.service.lawyers.trunk.gateway.esl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;

/**
 * B3 命令面池化：FreeSWITCH ESL 命令面长连接池。
 *
 * <p>按 {@code host:port} 维度维护一条<b>纯命令</b>长连接（复用
 * {@link FreeSwitchEslInboundClient} 的 R6 回执分流 / R7 字节帧 / R8 心跳退避机制，
 * {@code subscribeEvents=false} 不订阅事件），originate / uuid_kill / uuid_transfer /
 * sofia status 等命令全部复用该连接，消除高话务下"每命令一短连"的建连风暴。</p>
 *
 * <p>可靠性：</p>
 * <ul>
 *   <li>后台读线程自动重连（指数退避 2s→60s），断连期间命令快速失败返回 null；</li>
 *   <li>熔断器：连续建连失败达到阈值后进入 open 态（冷却期默认 60s），open 期间
 *       命令快速失败并抑制告警风暴；冷却到期半开探测一次连接就绪状态，成功即关闭；</li>
 *   <li>告警：连接"就绪→断开"翻转记 WARN + {@code hotline_gateway_cmdpool_down_total}；
 *       熔断打开记 ERROR + {@code hotline_gateway_cmdpool_circuit_open_total}。</li>
 * </ul>
 *
 * <p>开关 {@code call.gateway.pool.esl-enabled}（默认 true）：false 时
 * {@link #isEnabled()} 返回 false，适配器回退历史短连接实现，单机/排障可应急回退。</p>
 *
 * @author ai-lawyers
 */
@Component
public class PooledEslClient implements DisposableBean
{
    private static final Logger log = LoggerFactory.getLogger(PooledEslClient.class);

    /** 熔断器：连续建连失败打开阈值 */
    private static final int CIRCUIT_FAIL_THRESHOLD = 5;

    @Value("${call.gateway.pool.esl-enabled:true}")
    private boolean poolEnabled;

    /** ESL 端口（适配器同口径，FreeSWITCH 默认 8021） */
    @Value("${call.gateway.freeswitch.eslPort:8021}")
    private int eslPort;

    @Value("${call.gateway.freeswitch.eslPassword:ClueCon}")
    private String eslPassword;

    /** Socket 连接超时(ms)，与适配器 timeout 同配置 */
    @Value("${call.gateway.freeswitch.timeout:5000}")
    private int timeout;

    /** 熔断打开后的冷却时长(ms)，默认 60s，期间命令快速失败 */
    @Value("${call.gateway.pool.circuit-open-ms:60000}")
    private long circuitOpenMs;

    @Autowired(required = false)
    private HotlineMetrics metrics;

    /** host:port → 池化连接条目 */
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public boolean isEnabled()
    {
        return poolEnabled;
    }

    /**
     * 通过池中长连接发送一条 ESL 命令（含前缀，如 "api " / "bgapi "）。
     *
     * @return 命令响应块；池关闭 / 熔断打开 / 断连 / 超时均返回 null（调用方按既有
     *         "网关无响应"分支处理）
     */
    public String sendCommand(String host, String command)
    {
        if (!poolEnabled)
        {
            return null;
        }
        Entry entry = entries.computeIfAbsent(key(host, eslPort), k -> new Entry(host, eslPort));
        if (entry.circuitOpen())
        {
            return null;
        }
        return entry.client.sendCommand(command);
    }

    /** 可观测：当前池内连接条目数（不同 host:port 数） */
    public int poolSize()
    {
        return entries.size();
    }

    @Override
    public void destroy()
    {
        for (Entry e : entries.values())
        {
            e.shutdown();
        }
        entries.clear();
        log.info("[ESL-POOL] 命令面连接池已关闭");
    }

    private static String key(String host, int port)
    {
        return host + ":" + port;
    }

    // ---------------------------------------------------------------- 内部

    /** 单个网关地址的池化连接条目：长连接 + 熔断器状态 */
    private final class Entry implements FreeSwitchEslInboundClient.ReadyListener
    {
        final FreeSwitchEslInboundClient client;
        final String label;
        /** 连续建连失败计数（鉴权/订阅成功即由 onReady 清零） */
        final AtomicInteger consecutiveFails = new AtomicInteger(0);
        /** 熔断器是否打开（打开期间快速失败，冷却到期半开） */
        final AtomicBoolean circuitOpen = new AtomicBoolean(false);
        /** 半开探测是否进行中（保证只放行一个探测） */
        final AtomicBoolean halfOpenProbing = new AtomicBoolean(false);
        volatile long circuitOpenedAt;
        /** 上一次可观测连接状态（用于就绪→断开翻转告警只记一次） */
        final AtomicBoolean lastUp = new AtomicBoolean(false);
        final Thread monitorThread;

        Entry(String host, int port)
        {
            this.label = host + ":" + port;
            this.client = new FreeSwitchEslInboundClient(host, port, eslPassword, timeout, 2000, false);
            this.client.setReadyListener(this);
            this.client.addListener(new EslEventListener()
            {
                @Override
                public void onEvent(String h, EslEvent event) { /* 纯命令通道无事件 */ }

                @Override
                public void onDisconnect(String h)
                {
                    onDown();
                }
            });
            this.client.start();
            // 失败计数与熔断监测：监听读线程连接尝试结果（attemptOk 由读线程维护语义：
            // 周期性检查 client 就绪态；这里用轻量监测线程按周期采样，避免侵入读线程）
            this.monitorThread = new Thread(this::monitorLoop, "esl-pool-monitor-" + label);
            this.monitorThread.setDaemon(true);
            this.monitorThread.start();
        }

        boolean circuitOpen()
        {
            return circuitOpen.get();
        }

        void shutdown()
        {
            try { client.stop(); } catch (Exception ignored) {}
            if (monitorThread != null)
            {
                monitorThread.interrupt();
            }
        }

        /** 鉴权成功回调：连续失败清零；半开探测成功则关闭熔断器 */
        @Override
        public void onReady()
        {
            consecutiveFails.set(0);
            lastUp.set(true);
            if (circuitOpen.compareAndSet(true, false))
            {
                log.warn("[ESL-POOL] 熔断器闭合（连接恢复）{}", label);
            }
            halfOpenProbing.set(false);
        }

        /** 连接断开回调：就绪→断开翻转告警 */
        void onDown()
        {
            if (lastUp.compareAndSet(true, false))
            {
                log.warn("[ESL-POOL] 网关命令连接断开 {}", label);
                if (metrics != null)
                {
                    metrics.incrementGatewayCmdPoolDown("esl");
                }
            }
        }

        /**
         * 周期采样连接就绪态：持续不就绪累计连续失败，达阈值打开熔断器；
         * 熔断冷却到期后放行一次半开探测（直接以当前就绪态判定，后台读线程
         * 持续重连，探测成本为零）。
         */
        void monitorLoop()
        {
            while (!Thread.currentThread().isInterrupted())
            {
                try
                {
                    Thread.sleep(1000L);
                }
                catch (InterruptedException ie)
                {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (circuitOpen.get())
                {
                    // 冷却到期 → 半开探测：连接已就绪则关闭熔断，否则继续冷却
                    if (System.currentTimeMillis() - circuitOpenedAt >= circuitOpenMs
                            && halfOpenProbing.compareAndSet(false, true))
                    {
                        if (client.isReady())
                        {
                            onReady();
                        }
                        else
                        {
                            circuitOpenedAt = System.currentTimeMillis();
                            halfOpenProbing.set(false);
                        }
                    }
                    continue;
                }
                if (client.isReady())
                {
                    consecutiveFails.set(0);
                    continue;
                }
                int fails = consecutiveFails.incrementAndGet();
                if (fails >= CIRCUIT_FAIL_THRESHOLD && circuitOpen.compareAndSet(false, true))
                {
                    circuitOpenedAt = System.currentTimeMillis();
                    log.error("[ESL-POOL] 网关命令面熔断器打开 {}（连续 {}s 未就绪，冷却 {}ms）",
                            label, fails, circuitOpenMs);
                    if (metrics != null)
                    {
                        metrics.incrementGatewayCmdPoolCircuitOpen("esl");
                    }
                }
            }
        }
    }
}
