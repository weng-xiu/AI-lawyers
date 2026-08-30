package ai.lawyers.system.service.lawyers.trunk.gateway.esl;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FreeSWITCH ESL 入站（inbound）长连接客户端
 *
 * <p>与 {@code FreeSwitchGatewayAdapter} 的短连接命令通道不同，本类维护一条长连接，
 * 通过 {@code event plain} 订阅呼叫事件（CHANNEL_CREATE / CHANNEL_ANSWER /
 * CHANNEL_HANGUP_COMPLETE / BACKGROUND_JOB 等），并实时转发给已注册的
 * {@link EslEventListener}。</p>
 *
 * <p>可靠性设计：</p>
 * <ul>
 *   <li>帧读取全程按字节进行（R7）：header 按字节读到空行，body 严格按
 *       Content-Length 声明的字节数读取后用 UTF-8 解码，中文多字节 body
 *       不会造成字符/字节错位粘包；</li>
 *   <li>唯一读消费者 + 帧路由（R6）：只有读取线程从输入流读取帧；
 *       api/command/reply 等命令回执帧按 FIFO 交回等待中的 {@link #sendCommand}
 *       调用方，事件帧分发给监听器，命令线程绝不自行读流；</li>
 *   <li>心跳 + 指数退避重连（R8）：soTimeout=30s，读超时空闲时发送 ESL
 *       no-op 心跳（单个 {@code \n}），半开连接在写/读异常时被快速发现；
 *       重连等待 2s 起指数退避、上限 60s；监听器回调在独立 daemon
 *       单线程执行器上执行，慢监听器不会阻塞读取线程。</li>
 * </ul>
 *
 * @author ai-lawyers
 */
public class FreeSwitchEslInboundClient
{
    private static final Logger log = LoggerFactory.getLogger(FreeSwitchEslInboundClient.class);

    /** 读超时（也是空闲心跳间隔）：30s */
    private static final int SO_TIMEOUT_MS = 30_000;
    /** sendCommand 等待回执的超时时间 */
    private static final long COMMAND_TIMEOUT_MS = 30_000L;
    /** 指数退避：初始 2s，上限 60s */
    private static final long RECONNECT_BACKOFF_BASE_MS = 2_000L;
    private static final long RECONNECT_BACKOFF_MAX_MS = 60_000L;

    private final String host;
    private final int port;
    private final String password;
    private final int connectTimeoutMs;
    private final int reconnectIntervalMs;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private Thread readerThread;
    /** 当前帧是否已读到部分字节（用于区分"空闲超时"与"半帧超时"） */
    private boolean framePartial;

    /**
     * 待配对的命令回执 FIFO：sendCommand/心跳写入命令的同时在此排队，
     * 读取线程每收到一帧命令回执就完成队首 future。TCP 保证顺序一致。
     */
    private final Queue<CompletableFuture<String>> pendingReplies = new ConcurrentLinkedQueue<>();

    /** 监听器回调投递用的独立单线程（daemon），避免慢监听阻塞读取线程 */
    private volatile ExecutorService dispatchExecutor;

    private final List<EslEventListener> listeners = new CopyOnWriteArrayList<>();

    public FreeSwitchEslInboundClient(String host, int port, String password)
    {
        this(host, port, password, 5000, (int) RECONNECT_BACKOFF_BASE_MS);
    }

    public FreeSwitchEslInboundClient(String host, int port, String password,
                                     int connectTimeoutMs, int reconnectIntervalMs)
    {
        this.host = host;
        this.port = port;
        this.password = password;
        this.connectTimeoutMs = connectTimeoutMs;
        this.reconnectIntervalMs = reconnectIntervalMs;
    }

    public void addListener(EslEventListener listener)
    {
        listeners.add(listener);
    }

    public String getHost() { return host; }

    /**
     * 启动长连接（异步、自动重连）。幂等。
     */
    public void start()
    {
        if (!running.compareAndSet(false, true))
        {
            return;
        }
        dispatchExecutor = Executors.newSingleThreadExecutor(new ThreadFactory()
        {
            @Override
            public Thread newThread(Runnable r)
            {
                Thread t = new Thread(r, "esl-event-dispatch-" + host + ":" + port);
                t.setDaemon(true);
                return t;
            }
        });
        Thread t = new Thread(this::runLoop, "esl-inbound-" + host + ":" + port);
        t.setDaemon(true);
        readerThread = t;
        t.start();
        log.info("[ESL-{}] 入站事件监听已启动", host);
    }

    /**
     * 停止长连接。
     */
    public void stop()
    {
        running.set(false);
        closeQuietly();
        ExecutorService exec = dispatchExecutor;
        if (exec != null)
        {
            exec.shutdown();
            try
            {
                if (!exec.awaitTermination(5, TimeUnit.SECONDS))
                {
                    exec.shutdownNow();
                }
            }
            catch (InterruptedException e)
            {
                exec.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        Thread t = readerThread;
        if (t != null)
        {
            t.interrupt();
        }
    }

    /**
     * 在已建立的长连接上发送一条 ESL 命令（如 api / bgapi）。
     *
     * <p>调用线程只负责写命令并等待读取线程路由回来的回执帧，自身不读取输入流，
     * 因此不会与事件读取循环抢读。</p>
     *
     * @return 命令响应块，未连接/超时/失败时返回 null
     */
    public String sendCommand(String command)
    {
        if (out == null || socket == null || socket.isClosed())
        {
            log.warn("[ESL-{}] 未连接，跳过命令: {}", host, command);
            return null;
        }
        CompletableFuture<String> reply = new CompletableFuture<>();
        synchronized (this)
        {
            try
            {
                // 写命令与回执入队必须原子，保证 FIFO 与线上报文顺序一致
                out.write((command + "\n\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                pendingReplies.offer(reply);
            }
            catch (IOException e)
            {
                log.error("[ESL-{}] 发送命令失败: {}", host, command, e);
                return null;
            }
        }
        try
        {
            return reply.get(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
        catch (Exception e)
        {
            // 超时/中断/连接关闭：把超时项移出队列，避免后续回执错位
            pendingReplies.remove(reply);
            log.warn("[ESL-{}] 等待命令回执超时或中断: {} ({})", host, command, e.toString());
            return null;
        }
    }

    // ---------------------------------------------------------------- 内部

    private void runLoop()
    {
        long backoffMs = reconnectIntervalMs > 0 ? reconnectIntervalMs : RECONNECT_BACKOFF_BASE_MS;
        while (running.get())
        {
            boolean connectedOk = false;
            try
            {
                connectedOk = connectAndListen();
            }
            catch (Exception e)
            {
                log.warn("[ESL-{}] 连接异常: {}，{}ms 后重连", host, e.getMessage(), backoffMs);
            }
            closeQuietly();
            if (!running.get())
            {
                break;
            }
            // 指数退避：成功过一次则重置；失败逐次翻倍，上限 60s
            if (connectedOk)
            {
                backoffMs = reconnectIntervalMs > 0 ? reconnectIntervalMs : RECONNECT_BACKOFF_BASE_MS;
            }
            else
            {
                long next = Math.min(backoffMs * 2, RECONNECT_BACKOFF_MAX_MS);
                backoffMs = next;
            }
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
     * 建立连接、鉴权、订阅事件并进入帧读取循环。仅由读取线程调用。
     *
     * @return true 表示鉴权与事件订阅成功后进入了事件循环（连接被对端正常关闭）
     */
    private boolean connectAndListen() throws IOException
    {
        Socket sock = new Socket();
        sock.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        sock.setSoTimeout(SO_TIMEOUT_MS);
        sock.setKeepAlive(true);
        socket = sock;
        in = new BufferedInputStream(sock.getInputStream());
        out = sock.getOutputStream();
        pendingReplies.clear();

        // 1. 接收 "Content-Type: auth/request"
        EslFrame authRequest = readFrame();
        if (authRequest == null || !authRequest.isContentType("auth/request"))
        {
            throw new IOException("ESL 握手失败: " + (authRequest == null ? "连接被关闭" : authRequest.toText()));
        }

        // 2. 鉴权
        write("auth " + password + "\n\n");
        EslFrame authResp = readFrame();
        if (authResp == null || !authResp.toText().contains("+OK"))
        {
            throw new IOException("ESL 鉴权失败: " + (authResp == null ? "连接被关闭" : authResp.toText()));
        }
        log.info("[ESL-{}] 鉴权成功，订阅呼叫事件", host);

        // 3. 事件订阅（plain 文本格式，便于无第三方库解析）
        //    关注：通道生命周期、DTMF 按键、后台任务返回、录音停止
        write("event plain CHANNEL_CREATE CHANNEL_ANSWER CHANNEL_HANGUP "
                + "CHANNEL_HANGUP_COMPLETE CHANNEL_BRIDGE CHANNEL_UNBRIDGE "
                + "DTMF BACKGROUND_JOB RECORD_STOP\n\n");
        EslFrame eventResp = readFrame();
        if (eventResp == null || !eventResp.toText().contains("+OK"))
        {
            log.warn("[ESL-{}] 事件订阅返回异常: {}", host, eventResp == null ? "连接被关闭" : eventResp.toText());
        }

        // 4. 帧循环：读取线程是输入流的唯一消费者
        while (running.get() && !sock.isClosed())
        {
            EslFrame frame;
            try
            {
                frame = readFrame();
            }
            catch (SocketTimeoutException ste)
            {
                if (framePartial)
                {
                    // 帧读到一半超时：链路已不可用，抛出触发重连
                    throw new IOException("读取 ESL 帧中途超时（疑似半开连接）", ste);
                }
                // 空闲超时：发送 ESL no-op 心跳（单个 \n），写出失败即断链
                sendHeartbeat();
                continue;
            }
            if (frame == null)
            {
                log.info("[ESL-{}] 对端关闭连接", host);
                break; // EOF，连接断开
            }
            routeFrame(frame);
        }
        return true;
    }

    /**
     * 读取线程的帧路由：命令回执按 FIFO 交回 sendCommand 调用方；事件帧分发监听器。
     */
    private void routeFrame(EslFrame frame)
    {
        String contentType = frame.contentType;
        if (contentType != null && (contentType.contains("api/response")
                || contentType.contains("command/reply")
                || contentType.contains("auth/request")))
        {
            CompletableFuture<String> reply = pendingReplies.poll();
            if (reply != null)
            {
                reply.complete(frame.toText());
            }
            else
            {
                log.debug("[ESL-{}] 收到无等待者的命令回执: {}", host, frame.toText());
            }
            return;
        }
        if (contentType != null && (contentType.contains("text/event-plain")
                || contentType.contains("text/event-json")
                || contentType.contains("text/event-xml")))
        {
            EslEvent event = parseEvent(frame);
            if (event.getEventName() != null)
            {
                dispatch(event);
            }
            return;
        }
        if (contentType != null && contentType.contains("text/disconnect-notice"))
        {
            log.info("[ESL-{}] 收到断开通知: {}", host, frame.bodyAsString());
            return;
        }
        log.debug("[ESL-{}] 未识别帧类型: {}", host, contentType);
    }

    /** 空闲心跳：ESL 规定客户端发送单个换行，mod_event_socket 回应 {@code +OK}。 */
    private void sendHeartbeat()
    {
        CompletableFuture<String> heartbeatReply = new CompletableFuture<>();
        try
        {
            synchronized (this)
            {
                out.write('\n');
                out.flush();
                pendingReplies.offer(heartbeatReply);
            }
        }
        catch (IOException e)
        {
            pendingReplies.remove(heartbeatReply);
            log.warn("[ESL-{}] 心跳发送失败，连接可能已断开: {}", host, e.getMessage());
        }
    }

    /**
     * 按字节读取一个完整 ESL 帧：头部行读到空行（{@code \n} 或 {@code \r\n}），
     * body 严格按 Content-Length 声明的字节数读取，最后按 UTF-8 解码。
     *
     * @return 帧对象；流正常结束（EOF 且无任何数据）返回 null
     */
    private EslFrame readFrame() throws IOException
    {
        framePartial = false;
        Map<String, String> headers = new LinkedHashMap<>();
        String line;
        while ((line = readLineBytes()) != null)
        {
            if (line.isEmpty())
            {
                break; // 头部结束
            }
            int idx = line.indexOf(':');
            if (idx <= 0)
            {
                continue;
            }
            headers.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
        }
        if (line == null && headers.isEmpty())
        {
            return null; // EOF 且未读到任何头部
        }

        EslFrame frame = new EslFrame(headers);
        if (frame.contentLength > 0)
        {
            byte[] body = readExact(frame.contentLength);
            frame.body = body;
        }
        return frame;
    }

    /**
     * 按字节读取一行（直到 {@code \n}），去除行尾 {@code \r}，整行按 UTF-8 解码
     * （ESL 头均为 ASCII，按字节读不会破坏多字节边界）。读到 EOF 且无数据返回 null。
     */
    private String readLineBytes() throws IOException
    {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(128);
        int c;
        while ((c = in.read()) != -1)
        {
            framePartial = true;
            if (c == '\n')
            {
                break;
            }
            buf.write(c);
        }
        if (c == -1 && buf.size() == 0)
        {
            return null;
        }
        String s = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        if (s.endsWith("\r"))
        {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /** 精确读取 len 个字节；流提前结束抛 EOFException，保证帧边界不串包。 */
    private byte[] readExact(int len) throws IOException
    {
        byte[] data = new byte[len];
        int off = 0;
        while (off < len)
        {
            int n = in.read(data, off, len - off);
            if (n < 0)
            {
                throw new EOFException("读取 ESL body 提前结束: 需要 " + len + " 字节，实际 " + off);
            }
            off += n;
        }
        return data;
    }

    /**
     * 把一帧事件解析为 {@link EslEvent}。
     * BACKGROUND_JOB 的 text/event-plain body 内还嵌套一层事件头块。
     */
    private EslEvent parseEvent(EslFrame frame)
    {
        EslEvent event = new EslEvent();
        for (Map.Entry<String, String> e : frame.headers.entrySet())
        {
            String key = e.getKey();
            if ("Content-Length".equalsIgnoreCase(key) || "Content-Type".equalsIgnoreCase(key))
            {
                continue;
            }
            event.put(key, e.getValue());
            if ("Event-Name".equalsIgnoreCase(key))
            {
                event.setEventName(e.getValue());
            }
        }

        String contentType = frame.contentType;
        if (frame.body != null && contentType != null && contentType.contains("text/event-plain"))
        {
            String body = frame.bodyAsString();
            event.setBody(body);
            // body 内部又包含一个事件头块（含空行结束），可能再跟一段 body
            int sep = body.indexOf("\n\n");
            String innerHeader = sep >= 0 ? body.substring(0, sep) : body;
            for (String hl : innerHeader.split("\n"))
            {
                int idx = hl.indexOf(':');
                if (idx <= 0) continue;
                String key = hl.substring(0, idx).trim();
                String value = hl.substring(idx + 1).trim();
                // 内层事件名优先级更高
                if ("Event-Name".equalsIgnoreCase(key))
                {
                    event.setEventName(value);
                }
                if (!event.all().containsKey(key))
                {
                    event.put(key, value);
                }
            }
        }
        else if (frame.body != null)
        {
            event.setBody(frame.bodyAsString());
        }
        return event;
    }

    private void dispatch(final EslEvent event)
    {
        ExecutorService exec = dispatchExecutor;
        if (exec == null || exec.isShutdown())
        {
            dispatchDirect(event);
            return;
        }
        try
        {
            exec.execute(new Runnable()
            {
                @Override
                public void run()
                {
                    dispatchDirect(event);
                }
            });
        }
        catch (RejectedExecutionException e)
        {
            dispatchDirect(event);
        }
    }

    private void dispatchDirect(EslEvent event)
    {
        for (EslEventListener l : listeners)
        {
            try
            {
                l.onEvent(host, event);
            }
            catch (Exception e)
            {
                log.error("[ESL-{}] 监听器处理事件异常: {}", host, event.getEventName(), e);
            }
        }
    }

    private void write(String content) throws IOException
    {
        synchronized (this)
        {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    private void closeQuietly()
    {
        // 失败所有等待回执的命令调用方
        CompletableFuture<String> pending;
        while ((pending = pendingReplies.poll()) != null)
        {
            pending.complete(null);
        }
        for (EslEventListener l : listeners)
        {
            try { l.onDisconnect(host); } catch (Exception ignored) {}
        }
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        socket = null; in = null; out = null;
    }

    /**
     * 一个已完整读取的 ESL 帧（头部 + 可选 body）。
     */
    private static final class EslFrame
    {
        final Map<String, String> headers;
        final String contentType;
        final int contentLength;
        byte[] body;

        EslFrame(Map<String, String> headers)
        {
            this.headers = headers;
            this.contentType = header("Content-Type");
            this.contentLength = parseLength(header("Content-Length"));
        }

        private String header(String name)
        {
            for (Map.Entry<String, String> e : headers.entrySet())
            {
                if (e.getKey().equalsIgnoreCase(name))
                {
                    return e.getValue();
                }
            }
            return null;
        }

        private static int parseLength(String value)
        {
            if (value == null)
            {
                return 0;
            }
            try
            {
                return Integer.parseInt(value.trim());
            }
            catch (NumberFormatException e)
            {
                return 0;
            }
        }

        boolean isContentType(String type)
        {
            return contentType != null && contentType.contains(type);
        }

        String bodyAsString()
        {
            return body == null ? null : new String(body, StandardCharsets.UTF_8);
        }

        /** 完整帧文本（头部 + 空行 + body），与历史 sendCommand 返回格式保持一致。 */
        String toText()
        {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : headers.entrySet())
            {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            }
            if (body != null)
            {
                sb.append('\n').append(bodyAsString());
            }
            return sb.toString();
        }
    }
}
