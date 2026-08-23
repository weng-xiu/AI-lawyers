package ai.lawyers.system.service.lawyers.trunk.gateway.esl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
 * <p>支持自动重连，可由 Spring {@code @PostConstruct} 或定时任务启动。</p>
 *
 * @author ai-lawyers
 */
public class FreeSwitchEslInboundClient
{
    private static final Logger log = LoggerFactory.getLogger(FreeSwitchEslInboundClient.class);

    private final String host;
    private final int port;
    private final String password;
    private final int connectTimeoutMs;
    private final int reconnectIntervalMs;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Socket socket;
    private BufferedReader reader;
    private OutputStream out;
    private Thread readerThread;

    private final List<EslEventListener> listeners = new CopyOnWriteArrayList<>();

    public FreeSwitchEslInboundClient(String host, int port, String password)
    {
        this(host, port, password, 5000, 5000);
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
        Thread t = new Thread(this::runLoop, "esl-inbound-" + host + ":" + port);
        t.setDaemon(true);
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
    }

    /**
     * 在已建立的长连接上发送一条 ESL 命令（如 api / bgapi）。
     *
     * @return 命令响应块，未连接时返回 null
     */
    public synchronized String sendCommand(String command)
    {
        if (out == null || socket == null || socket.isClosed())
        {
            log.warn("[ESL-{}] 未连接，跳过命令: {}", host, command);
            return null;
        }
        try
        {
            write(command + "\n\n");
            // 命令响应紧跟一个 block，在事件流中需要读取；
            // 这里简单读取一次（事件循环也会并发读取，需要加锁保护）
            return readBlock();
        }
        catch (IOException e)
        {
            log.error("[ESL-{}] 发送命令失败: {}", host, command, e);
            return null;
        }
    }

    // ---------------------------------------------------------------- 内部

    private void runLoop()
    {
        while (running.get())
        {
            try
            {
                connectAndListen();
            }
            catch (Exception e)
            {
                log.warn("[ESL-{}] 连接异常: {}，{}ms 后重连", host, e.getMessage(), reconnectIntervalMs);
            }
            closeQuietly();
            if (!running.get())
            {
                break;
            }
            try
            {
                Thread.sleep(reconnectIntervalMs);
            }
            catch (InterruptedException ie)
            {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void connectAndListen() throws IOException
    {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        socket.setSoTimeout(0); // 读事件，永久阻塞
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = socket.getOutputStream();

        // 1. 接收 "Content-Type: auth/request"
        String authRequest = readBlock();
        if (authRequest == null || !authRequest.contains("auth/request"))
        {
            throw new IOException("ESL 握手失败: " + authRequest);
        }

        // 2. 鉴权
        write("auth " + password + "\n\n");
        String authResp = readBlock();
        if (authResp == null || !authResp.contains("+OK"))
        {
            throw new IOException("ESL 鉴权失败: " + authResp);
        }
        log.info("[ESL-{}] 鉴权成功，订阅呼叫事件", host);

        // 3. 事件订阅（plain 文本格式，便于无第三方库解析）
        //    关注：通道生命周期、DTMF 按键、后台任务返回、录音停止
        write("event plain CHANNEL_CREATE CHANNEL_ANSWER CHANNEL_HANGUP "
                + "CHANNEL_HANGUP_COMPLETE CHANNEL_BRIDGE CHANNEL_UNBRIDGE "
                + "DTMF BACKGROUND_JOB RECORD_STOP\n\n");
        String eventResp = readBlock();
        if (eventResp == null || !eventResp.contains("+OK"))
        {
            log.warn("[ESL-{}] 事件订阅返回异常: {}", host, eventResp);
        }

        // 4. 事件循环：持续读取 ESL block
        while (running.get() && !socket.isClosed())
        {
            EslEvent event = readEvent();
            if (event == null)
            {
                break; // 连接断开
            }
            if (event.getEventName() != null)
            {
                dispatch(event);
            }
        }
    }

    /**
     * 读取一个完整的 ESL block，解析为事件。
     */
    private EslEvent readEvent() throws IOException
    {
        String headerBlock = readBlock();
        if (headerBlock == null)
        {
            return null;
        }
        EslEvent event = new EslEvent();
        String contentLength = null;
        String contentType = null;

        for (String line : headerBlock.split("\n"))
        {
            int idx = line.indexOf(':');
            if (idx <= 0) continue;
            String key = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            if ("Content-Length".equalsIgnoreCase(key))
            {
                contentLength = value;
            }
            else if ("Content-Type".equalsIgnoreCase(key))
            {
                contentType = value;
            }
            else
            {
                event.put(key, value);
                if ("Event-Name".equalsIgnoreCase(key))
                {
                    event.setEventName(value);
                }
            }
        }

        // BACKGROUND_JOB 的响应会带 text/event-plain body（其中包含真正的事件头）
        if (contentLength != null && contentType != null && contentType.contains("text/event-plain"))
        {
            int len = Integer.parseInt(contentLength);
            String body = readBody(len);
            event.setBody(body);
            // body 内部又包含一个事件头块（含空行结束），可能再跟一段 body
            int sep = body.indexOf("\n\n");
            String innerHeader = sep >= 0 ? body.substring(0, sep) : body;
            for (String line : innerHeader.split("\n"))
            {
                int idx = line.indexOf(':');
                if (idx <= 0) continue;
                String key = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();
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
        else if (contentLength != null)
        {
            int len = Integer.parseInt(contentLength);
            event.setBody(readBody(len));
        }

        return event;
    }

    private void dispatch(EslEvent event)
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

    private synchronized void write(String content) throws IOException
    {
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private synchronized String readBlock() throws IOException
    {
        StringBuilder header = new StringBuilder();
        int contentLength = 0;
        String line;
        while ((line = reader.readLine()) != null)
        {
            if (line.isEmpty())
            {
                break;
            }
            header.append(line).append('\n');
            if (line.toLowerCase().startsWith("content-length:"))
            {
                try
                {
                    contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                }
                catch (NumberFormatException ignored) {}
            }
        }
        if (header.length() == 0 && (line == null))
        {
            return null;
        }
        if (contentLength > 0)
        {
            return header + readBody(contentLength);
        }
        return header.toString();
    }

    private String readBody(int len) throws IOException
    {
        char[] body = new char[len];
        int read = 0;
        while (read < len)
        {
            int n = reader.read(body, read, len - read);
            if (n < 0) break;
            read += n;
        }
        return new String(body, 0, Math.max(read, 0));
    }

    private void closeQuietly()
    {
        for (EslEventListener l : listeners)
        {
            try { l.onDisconnect(host); } catch (Exception ignored) {}
        }
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        socket = null; reader = null; out = null;
    }
}
