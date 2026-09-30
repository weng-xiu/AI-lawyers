package ai.lawyers.system.service.lawyers.trunk.gateway.ami;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
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
 * B3 命令面池化：Asterisk AMI 命令面长连接池。
 *
 * <p>按 {@code host:port} 维度维护一条长连接：Login（不订阅事件，AMI 不会推送
 * Event 块）后常驻，Originate / Hangup / Redirect / SIPshowpeer 等动作全部复用
 * 该连接，消除高话务下"每动作一短连 + Login/Logoff"的建连风暴。</p>
 *
 * <p>协议与可靠性（与 ESL 池同构）：</p>
 * <ul>
 *   <li>AMI 响应以空行结束，Action 无 ActionID 时响应按到达顺序与请求一一对应
 *       （TCP 保序），故采用 FIFO 待回执队列配对——与 ESL 池 pendingReplies 同机制；
 *       V2.53 扩展多事件收集：{@link #sendActionCollectEvents} 支持"响应块 + N 个
 *       事件块 + Complete 事件"型动作（如 PJSIPShowEndpoints），按 FIFO 头部条目
 *       持续收集直至指定 Complete 事件（或首块为 Response: Error）后返回合并文本，
 *       单响应块动作行为不变；</li>
 *   <li>唯一读消费者：仅读线程从输入流读响应块，写命令与回执入队 synchronized 原子；</li>
 *   <li>心跳 + 指数退避重连：soTimeout=30s，空闲超时发 {@code Action: Ping}，
 *       半开连接在读/写异常时发现；重连 2s 起指数退避、上限 60s；</li>
 *   <li>熔断器：连续 5s 未就绪打开（冷却期默认 60s），open 期间命令快速失败；
 *       冷却到期半开探测，鉴权成功即闭合；</li>
 *   <li>告警：连接"就绪→断开"翻转记 WARN + {@code hotline_gateway_cmdpool_down_total}；
 *       熔断打开记 ERROR + {@code hotline_gateway_cmdpool_circuit_open_total}。</li>
 * </ul>
 *
 * <p>开关 {@code call.gateway.pool.ami-enabled}（默认 true）：false 时
 * {@link #isEnabled()} 返回 false，适配器回退历史短连接实现。</p>
 *
 * @author ai-lawyers
 */
@Component
public class PooledAmiClient implements DisposableBean
{
    private static final Logger log = LoggerFactory.getLogger(PooledAmiClient.class);

    private static final String CRLF = "\r\n";
    /** 读超时（也是空闲 Ping 间隔）：30s */
    private static final int SO_TIMEOUT_MS = 30_000;
    /** 等待动作响应的超时时间 */
    private static final long ACTION_TIMEOUT_MS = 30_000L;
    /** 指数退避：初始 2s，上限 60s */
    private static final long RECONNECT_BACKOFF_BASE_MS = 2_000L;
    private static final long RECONNECT_BACKOFF_MAX_MS = 60_000L;
    /** 熔断器：连续未就绪打开阈值（1s 采样周期 × 5 = 5s） */
    private static final int CIRCUIT_FAIL_THRESHOLD = 5;

    @Value("${call.gateway.pool.ami-enabled:true}")
    private boolean poolEnabled;

    @Value("${call.gateway.asterisk.amiPort:5038}")
    private int amiPort;

    @Value("${call.gateway.asterisk.amiUser:admin}")
    private String amiUser;

    @Value("${call.gateway.asterisk.amiPassword:amp111}")
    private String amiPassword;

    @Value("${call.gateway.asterisk.timeout:5000}")
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
     * 通过池中长连接发送一个 AMI 动作（完整动作块，以空行结尾）。
     *
     * @return 响应块文本；池关闭 / 熔断打开 / 断连 / 超时均返回 null（调用方按既有
     *         "AMI 无响应"分支处理）
     */
    public String sendAction(String host, String action)
    {
        return sendInternal(host, action, null, ACTION_TIMEOUT_MS);
    }

    /**
     * V2.53 多事件收集：发送动作后持续收集响应块与后续事件块，直至出现指定
     * Complete 事件（如 {@code EndpointListComplete}）或首块为 {@code Response: Error}
     * （错误响应不伴随事件列表，立即结束），返回合并文本（块间空行分隔）。
     * 适用于 PJSIPShowEndpoints 等"Response + N × Event + Complete"型列表动作；
     * 期间本连接后续动作的响应仍按 TCP 顺序排在 Complete 之后，FIFO 配对不受影响。
     *
     * @param completeEvent Complete 事件名（不含 "Event: " 前缀）
     * @param timeoutMs     总超时（含收集期）；超时移除条目，后续残余事件块按无等待者丢弃
     * @return 合并文本；池关闭 / 熔断打开 / 断连 / 超时返回 null
     */
    public String sendActionCollectEvents(String host, String action, String completeEvent, long timeoutMs)
    {
        return sendInternal(host, action, completeEvent, timeoutMs);
    }

    private String sendInternal(String host, String action, String completeEvent, long timeoutMs)
    {
        if (!poolEnabled)
        {
            return null;
        }
        Entry entry = entries.computeIfAbsent(key(host, amiPort), k -> new Entry(host, amiPort));
        if (entry.circuitOpen())
        {
            return null;
        }
        return entry.send(action, completeEvent, timeoutMs);
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
        log.info("[AMI-POOL] 命令面连接池已关闭");
    }

    private static String key(String host, int port)
    {
        return host + ":" + port;
    }

    /** 块中是否存在指定事件行（整行精确匹配 "Event: <name>"，避免 EndpointList 误配 EndpointListComplete） */
    private static boolean containsEvent(String block, String eventName)
    {
        for (String line : block.split("\n"))
        {
            if (("Event: " + eventName).equals(line.trim()))
            {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 内部

    /** 单个 AMI 地址的池化连接条目：长连接 + 读写线程 + 熔断器 */
    private final class Entry
    {
        final String label;
        final String host;
        final int port;
        final AtomicBoolean running = new AtomicBoolean(true);
        /** 已登录就绪（可下发动作） */
        final AtomicBoolean ready = new AtomicBoolean(false);
        /** 待配对的动作回执 FIFO：写动作与入队原子，TCP 保序保证顺序一致 */
        final Queue<Pending> pendingReplies = new ConcurrentLinkedQueue<>();
        /** 连续未就绪采样计数（鉴权成功清零） */
        final AtomicInteger consecutiveFails = new AtomicInteger(0);
        final AtomicBoolean circuitOpen = new AtomicBoolean(false);
        final AtomicBoolean halfOpenProbing = new AtomicBoolean(false);
        volatile long circuitOpenedAt;
        final AtomicBoolean lastUp = new AtomicBoolean(false);

        volatile Socket socket;
        volatile OutputStream out;
        final Thread ioThread;
        final Thread monitorThread;

        Entry(String host, int port)
        {
            this.host = host;
            this.port = port;
            this.label = host + ":" + port;
            this.ioThread = new Thread(this::runLoop, "ami-pool-io-" + label);
            this.ioThread.setDaemon(true);
            this.ioThread.start();
            this.monitorThread = new Thread(this::monitorLoop, "ami-pool-monitor-" + label);
            this.monitorThread.setDaemon(true);
            this.monitorThread.start();
        }

        boolean circuitOpen()
        {
            return circuitOpen.get();
        }

        /**
         * 发送动作并等待读线程路由回来的回执：单响应块模式等待一个响应块，
         * 收集模式（completeEvent 非空）等待"响应 + 事件块 + Complete"合并文本；
         * 未连接/超时返回 null
         */
        String send(String action, String completeEvent, long timeoutMs)
        {
            OutputStream o = out;
            if (o == null || !ready.get())
            {
                return null;
            }
            Pending pending = new Pending(completeEvent);
            synchronized (this)
            {
                try
                {
                    o.write(action.getBytes(StandardCharsets.UTF_8));
                    o.flush();
                    pendingReplies.offer(pending);
                }
                catch (IOException e)
                {
                    log.warn("[AMI-POOL] 发送动作失败 {}", label, e);
                    return null;
                }
            }
            // 写入后连接已断开（onDown 已在入队前清空队列）时快速失败，避免空等超时
            if (!ready.get())
            {
                pendingReplies.remove(pending);
                return null;
            }
            try
            {
                return pending.future.get(timeoutMs, TimeUnit.MILLISECONDS);
            }
            catch (Exception e)
            {
                pendingReplies.remove(pending);
                log.warn("[AMI-POOL] 等待动作响应超时或中断 {} ({})", label, e.toString());
                return null;
            }
        }

        void shutdown()
        {
            running.set(false);
            closeQuietly();
            ioThread.interrupt();
            monitorThread.interrupt();
        }

        /** 鉴权成功：连续失败清零；半开探测成功则关闭熔断器 */
        void onReady()
        {
            ready.set(true);
            consecutiveFails.set(0);
            lastUp.set(true);
            if (circuitOpen.compareAndSet(true, false))
            {
                log.warn("[AMI-POOL] 熔断器闭合（连接恢复）{}", label);
            }
            halfOpenProbing.set(false);
        }

        /** 连接断开：失败所有等待方 + 就绪→断开翻转告警 */
        void onDown()
        {
            ready.set(false);
            Pending pending;
            while ((pending = pendingReplies.poll()) != null)
            {
                pending.future.complete(null);
            }
            if (lastUp.compareAndSet(true, false))
            {
                log.warn("[AMI-POOL] 网关命令连接断开 {}", label);
                if (metrics != null)
                {
                    metrics.incrementGatewayCmdPoolDown("ami");
                }
            }
        }

        /** 重连循环：建连 → 登录 → 读响应块；失败指数退避 */
        void runLoop()
        {
            long backoffMs = RECONNECT_BACKOFF_BASE_MS;
            while (running.get())
            {
                boolean connectedOk = false;
                try
                {
                    connectedOk = connectAndRead();
                }
                catch (Exception e)
                {
                    log.warn("[AMI-POOL] 连接异常 {}: {}，{}ms 后重连", label, e.getMessage(), backoffMs);
                }
                closeQuietly();
                onDown();
                if (!running.get())
                {
                    break;
                }
                backoffMs = connectedOk ? RECONNECT_BACKOFF_BASE_MS
                        : Math.min(backoffMs * 2, RECONNECT_BACKOFF_MAX_MS);
                try
                {
                    Thread.sleep(backoffMs);
                }
                catch (InterruptedException ie)
                {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        /**
         * 建连、登录并进入响应读取循环。仅由 IO 线程调用。
         *
         * @return true 表示登录成功后进入过读取循环（连接被对端正常关闭）
         */
        boolean connectAndRead() throws IOException
        {
            Socket sock = new Socket();
            sock.connect(new InetSocketAddress(host, port), timeout);
            sock.setSoTimeout(SO_TIMEOUT_MS);
            sock.setKeepAlive(true);
            socket = sock;
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
            out = sock.getOutputStream();
            pendingReplies.clear();

            // 1. banner: Asterisk Call Manager/x.x.x
            String banner = reader.readLine();
            if (banner == null)
            {
                throw new IOException("AMI 握手失败: 连接被关闭");
            }

            // 2. 登录（Events: off 显式关闭事件推送 → 纯命令通道，FIFO 只配对响应块）
            writeRaw("Action: Login" + CRLF
                    + "Username: " + amiUser + CRLF
                    + "Secret: " + amiPassword + CRLF
                    + "Events: off" + CRLF + CRLF);
            String loginResp = readBlock(reader);
            if (loginResp == null || !loginResp.contains("Response: Success"))
            {
                throw new IOException("AMI 登录失败: " + loginResp);
            }
            log.info("[AMI-POOL] 登录成功，命令通道就绪 {}", label);
            onReady();

            // 3. 响应循环：IO 线程是输入流唯一消费者
            while (running.get() && !sock.isClosed())
            {
                String block;
                try
                {
                    block = readBlock(reader);
                }
                catch (SocketTimeoutException ste)
                {
                    // 空闲超时：Ping 心跳，写出失败即断链
                    sendPing();
                    continue;
                }
                if (block == null)
                {
                    log.info("[AMI-POOL] 对端关闭连接 {}", label);
                    break;
                }
                routeBlock(block);
            }
            return true;
        }

        /**
         * 读线程的回执路由：单响应块模式按 FIFO 交回 send 调用方（Event 块兜底丢弃）；
         * 收集模式持续追加事件块直至 Complete 事件（V2.53）
         */
        void routeBlock(String block)
        {
            Pending head = pendingReplies.peek();
            if (head == null)
            {
                log.debug("[AMI-POOL] 收到无等待者的响应块 {}", label);
                return;
            }
            if (head.completeEvent == null)
            {
                // 单响应块模式：命令通道未订阅事件，Event 块兜底丢弃（多事件收集见 sendActionCollectEvents）
                if (block.contains("Event:"))
                {
                    log.debug("[AMI-POOL] 忽略非预期事件块 {}", label);
                    return;
                }
                pendingReplies.poll();
                head.future.complete(block);
                return;
            }
            // 多事件收集模式：追加块直至指定 Complete 事件
            if (containsEvent(block, head.completeEvent))
            {
                pendingReplies.poll();
                head.collected.append(block);
                head.future.complete(head.collected.toString());
                return;
            }
            if (!head.started && block.contains("Response: Error"))
            {
                // 错误响应不伴随事件列表，立即结束避免空等超时
                pendingReplies.poll();
                head.future.complete(block);
                return;
            }
            head.started = true;
            head.collected.append(block).append('\n');
        }

        /** 空闲心跳：Action: Ping，回执同样走 FIFO 配对 */
        void sendPing()
        {
            Pending pingPending = new Pending(null);
            try
            {
                synchronized (this)
                {
                    OutputStream o = out;
                    if (o == null)
                    {
                        return;
                    }
                    o.write(("Action: Ping" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
                    o.flush();
                    pendingReplies.offer(pingPending);
                }
            }
            catch (IOException e)
            {
                pendingReplies.remove(pingPending);
                log.warn("[AMI-POOL] Ping 发送失败，连接可能已断开 {}: {}", label, e.getMessage());
            }
        }

        /** AMI 响应/事件块以空行结束；EOF 且未读到任何内容返回 null */
        String readBlock(BufferedReader reader) throws IOException
        {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.trim().isEmpty())
                {
                    if (sb.length() > 0)
                    {
                        break;
                    }
                    continue;
                }
                sb.append(line).append('\n');
            }
            if (line == null && sb.length() == 0)
            {
                return null;
            }
            return sb.toString();
        }

        void writeRaw(String content) throws IOException
        {
            synchronized (this)
            {
                OutputStream o = out;
                if (o == null)
                {
                    throw new IOException("AMI 未连接");
                }
                o.write(content.getBytes(StandardCharsets.UTF_8));
                o.flush();
            }
        }

        void closeQuietly()
        {
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
            socket = null;
            out = null;
        }

        /**
         * 周期采样连接就绪态：持续不就绪累计连续失败，达阈值打开熔断器；
         * 熔断冷却到期后放行一次半开探测（IO 线程持续重连，探测仅读就绪态）。
         */
        void monitorLoop()
        {
            while (running.get())
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
                    if (System.currentTimeMillis() - circuitOpenedAt >= circuitOpenMs
                            && halfOpenProbing.compareAndSet(false, true))
                    {
                        if (ready.get())
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
                if (ready.get())
                {
                    consecutiveFails.set(0);
                    continue;
                }
                int fails = consecutiveFails.incrementAndGet();
                if (fails >= CIRCUIT_FAIL_THRESHOLD && circuitOpen.compareAndSet(false, true))
                {
                    circuitOpenedAt = System.currentTimeMillis();
                    log.error("[AMI-POOL] 网关命令面熔断器打开 {}（连续 {}s 未就绪，冷却 {}ms）",
                            label, fails, circuitOpenMs);
                    if (metrics != null)
                    {
                        metrics.incrementGatewayCmdPoolCircuitOpen("ami");
                    }
                }
            }
        }
    }

    /**
     * FIFO 待回执条目（V2.53）：单响应块模式（completeEvent=null）一个块即完成；
     * 收集模式持续累积响应+事件块文本直至 Complete 事件，由 IO 线程独占写入
     */
    private static final class Pending
    {
        final CompletableFuture<String> future = new CompletableFuture<>();
        /** Complete 事件名（如 EndpointListComplete）；null=单响应块模式 */
        final String completeEvent;
        /** 收集模式：已收集的响应+事件块文本（块间以空行分隔） */
        final StringBuilder collected = new StringBuilder();
        /** 收集模式：是否已收到首块（用于识别首块 Response: Error 立即结束） */
        volatile boolean started;

        Pending(String completeEvent)
        {
            this.completeEvent = completeEvent;
        }
    }
}
