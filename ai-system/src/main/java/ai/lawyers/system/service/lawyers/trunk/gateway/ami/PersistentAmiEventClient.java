package ai.lawyers.system.service.lawyers.trunk.gateway.ami;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * B1：Asterisk AMI 常驻事件连接（单 host:port 一条，登录后 {@code Events: all}）。
 *
 * <p>与命令面 {@link PooledAmiClient} 职责分离：本连接只订阅事件不下发业务动作，
 * 读线程收到的 {@code Response} 块（Login/Ping 应答）直接忽略，{@code Event} 块
 * 经 {@link AmiEventParser} 解析后分发给注册的 {@link AmiEventListener}。</p>
 *
 * <p>可靠性（与 ESL 入站客户端同构）：</p>
 * <ul>
 *   <li>心跳：soTimeout=30s 空闲超时发 {@code Action: Ping}，半开连接由读写异常暴露；</li>
 *   <li>重连：2s 起指数退避、上限 60s，{@code start()} 后由 IO 线程自持；</li>
 *   <li>离线模式：PBX 不可达仅循环重试，不阻断应用启动（复用 Simulator 兜底思路）；</li>
 *   <li>唯一读消费者：仅 IO 线程读输入流；Ping 写入 synchronized 原子。</li>
 * </ul>
 *
 * @author ai-lawyers
 */
public class PersistentAmiEventClient
{
    private static final Logger log = LoggerFactory.getLogger(PersistentAmiEventClient.class);

    private static final String CRLF = "\r\n";
    /** 读超时（也是空闲 Ping 间隔）：30s */
    private static final int SO_TIMEOUT_MS = 30_000;
    /** 指数退避：初始 2s，上限 60s */
    private static final long RECONNECT_BACKOFF_BASE_MS = 2_000L;
    private static final long RECONNECT_BACKOFF_MAX_MS = 60_000L;

    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final int connectTimeoutMs;
    private final List<AmiEventListener> listeners = new CopyOnWriteArrayList<>();

    private final AtomicBoolean running = new AtomicBoolean(false);
    /** 已登录就绪（可收事件） */
    private final AtomicBoolean ready = new AtomicBoolean(false);

    private volatile Socket socket;
    private volatile OutputStream out;
    private Thread ioThread;

    public PersistentAmiEventClient(String host, int port, String user, String password, int connectTimeoutMs)
    {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public void addListener(AmiEventListener l)
    {
        if (l != null)
        {
            listeners.add(l);
        }
    }

    public String getHost()
    {
        return host;
    }

    /** 已登录就绪（可收事件） */
    public boolean isConnected()
    {
        return ready.get() && socket != null && !socket.isClosed();
    }

    /** 启动 IO 线程（幂等；连接失败进入离线重试循环，不抛异常不阻断启动） */
    public synchronized void start()
    {
        if (running.get())
        {
            return;
        }
        running.set(true);
        ioThread = new Thread(this::runLoop, "ami-event-io-" + host + ":" + port);
        ioThread.setDaemon(true);
        ioThread.start();
    }

    /** 停止并断开（退出重连循环） */
    public synchronized void stop()
    {
        running.set(false);
        ready.set(false);
        closeQuietly();
        if (ioThread != null)
        {
            ioThread.interrupt();
            ioThread = null;
        }
    }

    /** 重连循环：建连 → 登录（Events: all）→ 事件读取循环；失败指数退避 */
    private void runLoop()
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
                log.warn("[AMI-EVENT] 连接异常 {}: {}，{}ms 后重连", label(), e.getMessage(), backoffMs);
            }
            closeQuietly();
            boolean wasReady = ready.getAndSet(false);
            if (wasReady)
            {
                log.warn("[AMI-EVENT] 事件连接断开 {}", label());
                for (AmiEventListener l : listeners)
                {
                    try { l.onDisconnect(host); } catch (Exception ignored) {}
                }
            }
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
     * 建连、登录并进入事件读取循环。仅由 IO 线程调用。
     *
     * @return true 表示登录成功后进入过读取循环（连接被对端正常关闭）
     */
    private boolean connectAndRead() throws IOException
    {
        Socket sock = new Socket();
        sock.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        sock.setSoTimeout(SO_TIMEOUT_MS);
        sock.setKeepAlive(true);
        socket = sock;
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
        out = sock.getOutputStream();

        // 1. banner: Asterisk Call Manager/x.x.x
        String banner = reader.readLine();
        if (banner == null)
        {
            throw new IOException("AMI 握手失败: 连接被关闭");
        }

        // 2. 登录并订阅全部事件（B1 事件通道；命令面在 PooledAmiClient，Events: off）
        writeRaw("Action: Login" + CRLF
                + "Username: " + user + CRLF
                + "Secret: " + password + CRLF
                + "Events: all" + CRLF + CRLF);
        String loginResp = readBlock(reader);
        if (loginResp == null || !loginResp.contains("Response: Success"))
        {
            throw new IOException("AMI 事件通道登录失败: " + loginResp);
        }
        ready.set(true);
        log.info("[AMI-EVENT] 登录成功，事件通道就绪（Events: all）{}", label());

        // 3. 事件循环：IO 线程是输入流唯一消费者
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
                log.info("[AMI-EVENT] 对端关闭连接 {}", label());
                break;
            }
            routeBlock(block);
        }
        return true;
    }

    /** 读线程的事件路由：解析 → 分发；非事件块（Login/Ping 响应）忽略 */
    private void routeBlock(String block)
    {
        AmiEvent event = AmiEventParser.parse(block);
        if (event == null)
        {
            log.debug("[AMI-EVENT] 忽略非事件块 {}", label());
            return;
        }
        for (AmiEventListener l : listeners)
        {
            try
            {
                l.onEvent(host, event);
            }
            catch (Exception e)
            {
                log.error("[AMI-EVENT] 监听器处理事件异常: event={} err={}", event.getName(), e.getMessage(), e);
            }
        }
    }

    /** 空闲心跳：Action: Ping（响应块在事件循环中被忽略） */
    private void sendPing()
    {
        try
        {
            writeRaw("Action: Ping" + CRLF + CRLF);
        }
        catch (IOException e)
        {
            log.warn("[AMI-EVENT] Ping 发送失败，连接可能已断开 {}: {}", label(), e.getMessage());
        }
    }

    /** AMI 块以空行结束；EOF 且未读到任何内容返回 null */
    private String readBlock(BufferedReader reader) throws IOException
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

    private void writeRaw(String content) throws IOException
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

    private void closeQuietly()
    {
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        socket = null;
        out = null;
    }

    private String label()
    {
        return host + ":" + port;
    }
}
