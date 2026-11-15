package ai.lawyers.system.service.lawyers.trunk.gateway.esl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * ESL 入站长连接客户端测试（内嵌假 ESL 服务端）。
 *
 * <p>覆盖：鉴权/事件订阅握手、事件解析与异步分发、中文 body 按 Content-Length
 * 字节解码不粘包、命令回执 FIFO 配对、未就绪快速失败、纯命令通道（不订阅事件）、
 * 监听器异常隔离、停止时断连通知、断线自动重连。</p>
 *
 * @author ai-lawyers
 */
class FreeSwitchEslInboundClientTest
{
    private static final String HOST = "127.0.0.1";

    private FreeSwitchEslInboundClient client;

    private FakeEslServer server;

    @AfterEach
    void tearDown() throws Exception
    {
        if (client != null)
        {
            client.stop();
        }
        if (server != null)
        {
            server.close();
        }
    }

    private FreeSwitchEslInboundClient newClient(int port, boolean subscribeEvents)
    {
        // 连接超时 2s，重连基数 200ms（测试内可快速观测重连）
        return new FreeSwitchEslInboundClient(HOST, port, "ClueCon", 2000, 200, subscribeEvents);
    }

    @Test
    void 订阅成功后接收事件并解析通道变量() throws Exception
    {
        server = new FakeEslServer(true);
        client = newClient(server.port(), true);
        List<EslEvent> events = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(1);
        client.addListener(new EslEventListener()
        {
            @Override
            public void onEvent(String host, EslEvent event)
            {
                events.add(event);
                latch.countDown();
            }
        });
        client.start();

        await(client::isConnected, 8000);
        assertTrue(client.isConnected(), "订阅成功后应处于在线态");
        assertTrue(server.eventSubscribed.get(), "默认应订阅呼叫事件");

        server.pushEvent("Event-Name: CHANNEL_ANSWER\nUnique-ID: uuid-1\n"
                + "Caller-Caller-ID-Number: 13800001234\n\n");

        assertTrue(latch.await(5, TimeUnit.SECONDS), "监听器应收到事件");
        EslEvent event = events.get(0);
        assertThat(event.getEventName()).isEqualTo("CHANNEL_ANSWER");
        assertThat(event.getCallUuid()).isEqualTo("uuid-1");
        assertThat(event.get("Caller-Caller-ID-Number")).isEqualTo("13800001234");
    }

    @Test
    void 中文body按字节长度解码且不粘包() throws Exception
    {
        server = new FakeEslServer(true);
        client = newClient(server.port(), true);
        List<EslEvent> events = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(2);
        client.addListener(new EslEventListener()
        {
            @Override
            public void onEvent(String host, EslEvent event)
            {
                events.add(event);
                latch.countDown();
            }
        });
        client.start();
        await(client::isConnected, 8000);

        server.pushEvent("Event-Name: BACKGROUND_JOB\nJob-UUID: job-1\n\n中文结果：已生成工单");
        server.pushEvent("Event-Name: CHANNEL_HANGUP_COMPLETE\nUnique-ID: uuid-2\n\n");

        assertTrue(latch.await(5, TimeUnit.SECONDS), "两帧事件都应被分发");
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getBody()).contains("中文结果：已生成工单");
        assertThat(events.get(1).getEventName()).isEqualTo("CHANNEL_HANGUP_COMPLETE");
        assertThat(events.get(1).getCallUuid()).isEqualTo("uuid-2");
    }

    @Test
    void 命令回执按FIFO配对() throws Exception
    {
        server = new FakeEslServer(true);
        client = newClient(server.port(), true);
        client.start();
        await(client::isReady, 8000);

        String first = client.sendCommand("api status");
        String second = client.sendCommand("api uuid_kill uuid-1");

        assertNotNull(first);
        assertNotNull(second);
        assertThat(first).contains("echo:api status");
        assertThat(second).contains("echo:api uuid_kill uuid-1");
        assertThat(server.received).contains("api status", "api uuid_kill uuid-1");
    }

    @Test
    void 未就绪时命令快速失败返回null() throws Exception
    {
        int deadPort;
        try (ServerSocket probe = new ServerSocket(0))
        {
            deadPort = probe.getLocalPort();
        }
        // 未启动：ready/socket 均未就绪
        client = newClient(deadPort, true);

        long start = System.currentTimeMillis();
        assertNull(client.sendCommand("api status"));
        assertThat(System.currentTimeMillis() - start).isLessThan(1000L);
        assertFalse(client.isConnected());
        assertFalse(client.isReady());
    }

    @Test
    void 纯命令通道不订阅事件且触发就绪回调() throws Exception
    {
        server = new FakeEslServer(false);
        client = newClient(server.port(), false);
        CountDownLatch ready = new CountDownLatch(1);
        client.setReadyListener(ready::countDown);
        client.start();

        assertTrue(ready.await(8, TimeUnit.SECONDS), "鉴权成功后应触发就绪回调");
        await(client::isReady, 3000);
        assertTrue(client.isReady());
        assertFalse(server.eventSubscribed.get(), "纯命令通道不应发送 event 订阅");
        assertNotNull(client.sendCommand("api status"));
    }

    @Test
    void 单个监听器异常不影响其他监听器() throws Exception
    {
        server = new FakeEslServer(true);
        client = newClient(server.port(), true);
        CountDownLatch latch = new CountDownLatch(1);
        client.addListener(new EslEventListener()
        {
            @Override
            public void onEvent(String host, EslEvent event)
            {
                throw new IllegalStateException("监听器故障");
            }
        });
        client.addListener(new EslEventListener()
        {
            @Override
            public void onEvent(String host, EslEvent event)
            {
                latch.countDown();
            }
        });
        client.start();
        await(client::isConnected, 8000);

        server.pushEvent("Event-Name: CHANNEL_CREATE\nUnique-ID: uuid-3\n\n");

        assertTrue(latch.await(5, TimeUnit.SECONDS), "异常监听器不应阻断后续监听器");
    }

    @Test
    void 停止时通知断连并回落在线态() throws Exception
    {
        server = new FakeEslServer(true);
        client = newClient(server.port(), true);
        CountDownLatch disconnected = new CountDownLatch(1);
        client.addListener(new EslEventListener()
        {
            @Override
            public void onEvent(String host, EslEvent event)
            {
            }

            @Override
            public void onDisconnect(String host)
            {
                disconnected.countDown();
            }
        });
        client.start();
        await(client::isConnected, 8000);
        assertTrue(client.isConnected());

        client.stop();

        assertTrue(disconnected.await(5, TimeUnit.SECONDS), "停止后应回调 onDisconnect");
        await(() -> !client.isConnected(), 3000);
        assertFalse(client.isConnected());
        assertFalse(client.isReady());
    }

    @Test
    void 对端断开后自动重连() throws Exception
    {
        server = new FakeEslServer(true);
        client = newClient(server.port(), true);
        client.start();
        await(client::isConnected, 8000);
        assertThat(server.acceptCount.get()).isEqualTo(1);

        server.dropCurrent();
        await(() -> server.acceptCount.get() >= 2, 10000);
        assertThat(server.acceptCount.get()).isGreaterThanOrEqualTo(2);

        await(client::isConnected, 10000);
        assertTrue(client.isConnected(), "重连鉴权/订阅成功后应恢复在线态");
        assertNotNull(client.sendCommand("api status"), "重连后命令应恢复可用");
    }

    /** 轮询等待条件满足（超时后由调用方断言） */
    private static void await(BooleanSupplier condition, long timeoutMs) throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline)
        {
            if (condition.getAsBoolean())
            {
                return;
            }
            Thread.sleep(20L);
        }
    }

    // ---------------------------------------------------------------- 假 ESL 服务端

    /**
     * 最小 ESL 服务端：auth/request → 校验 auth →（事件模式）校验 event plain 订阅
     * → 按帧回复命令；支持主动推送事件帧与断连模拟。
     */
    static class FakeEslServer implements AutoCloseable
    {
        private final ServerSocket serverSocket;

        private final boolean eventMode;

        final AtomicInteger acceptCount = new AtomicInteger();

        final List<String> received = Collections.synchronizedList(new ArrayList<>());

        final AtomicBoolean eventSubscribed = new AtomicBoolean();

        private volatile boolean running = true;

        private volatile Socket current;

        private volatile OutputStream out;

        FakeEslServer(boolean eventMode) throws IOException
        {
            this.eventMode = eventMode;
            this.serverSocket = new ServerSocket(0);
            Thread t = new Thread(this::acceptLoop, "fake-esl-accept");
            t.setDaemon(true);
            t.start();
        }

        int port()
        {
            return serverSocket.getLocalPort();
        }

        private void acceptLoop()
        {
            while (running)
            {
                try
                {
                    Socket s = serverSocket.accept();
                    acceptCount.incrementAndGet();
                    current = s;
                    handle(s);
                }
                catch (IOException e)
                {
                    if (!running)
                    {
                        return;
                    }
                }
            }
        }

        private void handle(Socket s)
        {
            try
            {
                s.setSoTimeout(20000);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                OutputStream o = s.getOutputStream();
                out = o;
                writeRaw(o, "Content-Type: auth/request\n\n");
                String auth = readBlock(reader);
                if (auth == null || !auth.startsWith("auth "))
                {
                    return;
                }
                writeRaw(o, "Content-Type: command/reply\nReply-Text: +OK accepted\n\n");
                if (eventMode)
                {
                    String subscription = readBlock(reader);
                    if (subscription == null || !subscription.startsWith("event plain"))
                    {
                        return;
                    }
                    eventSubscribed.set(true);
                    writeRaw(o, "Content-Type: command/reply\nReply-Text: +OK event listener enabled plain\n\n");
                }
                while (running)
                {
                    String req = readBlock(reader);
                    if (req == null)
                    {
                        break; // EOF：客户端断开
                    }
                    if (req.isEmpty())
                    {
                        continue; // 客户端空闲心跳（单个换行）
                    }
                    received.add(req);
                    synchronized (this)
                    {
                        o.write(replyFor(req));
                        o.flush();
                    }
                }
            }
            catch (IOException ignored)
            {
                // 连接中断，回到 accept 等待客户端重连
            }
            finally
            {
                out = null;
            }
        }

        /** api 命令回 api/response（body 回显命令），其余回 command/reply。 */
        private static byte[] replyFor(String req) throws IOException
        {
            if (req.startsWith("api "))
            {
                byte[] body = ("echo:" + req + ";State REGED").getBytes(StandardCharsets.UTF_8);
                byte[] head = ("Content-Type: api/response\nContent-Length: " + body.length + "\n\n")
                        .getBytes(StandardCharsets.UTF_8);
                byte[] frame = new byte[head.length + body.length];
                System.arraycopy(head, 0, frame, 0, head.length);
                System.arraycopy(body, 0, frame, head.length, body.length);
                return frame;
            }
            return "Content-Type: command/reply\nReply-Text: +OK\n\n".getBytes(StandardCharsets.UTF_8);
        }

        /** 主动推送一帧 text/event-plain 事件（Content-Length 按 UTF-8 字节数）。 */
        void pushEvent(String plainBody) throws IOException
        {
            byte[] body = plainBody.getBytes(StandardCharsets.UTF_8);
            byte[] head = ("Content-Type: text/event-plain\nContent-Length: " + body.length + "\n\n")
                    .getBytes(StandardCharsets.UTF_8);
            synchronized (this)
            {
                OutputStream o = out;
                if (o == null)
                {
                    throw new IOException("客户端尚未连接");
                }
                o.write(head);
                o.write(body);
                o.flush();
            }
        }

        void dropCurrent()
        {
            Socket s = current;
            if (s != null)
            {
                try { s.close(); } catch (IOException ignored) {}
            }
        }

        private static void writeRaw(OutputStream o, String text) throws IOException
        {
            o.write(text.getBytes(StandardCharsets.UTF_8));
            o.flush();
        }

        /** 读一个请求块：按行读到空行（ESL 命令均无 body）；EOF 且无内容返回 null */
        private static String readBlock(BufferedReader reader) throws IOException
        {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.isEmpty())
                {
                    break;
                }
                sb.append(line).append('\n');
            }
            if (line == null && sb.length() == 0)
            {
                return null;
            }
            return sb.toString().trim();
        }

        @Override
        public void close() throws IOException
        {
            running = false;
            dropCurrent();
            serverSocket.close();
        }
    }
}
