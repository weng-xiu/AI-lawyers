package ai.lawyers.system.service.lawyers.trunk.gateway.ami;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * B3 命令面池化：PooledAmiClient 单元测试（内嵌假 AMI 服务端）。
 *
 * <p>覆盖：Login 显式 Events: off（纯命令通道）、长连接复用、事件块忽略后
 * 响应仍正确配对、断连自动重建、熔断器打开快速失败、池关闭快速失败。</p>
 */
class PooledAmiClientTest
{
    private PooledAmiClient pool;
    private FakeAmiServer server;

    @AfterEach
    void tearDown() throws Exception
    {
        if (pool != null)
        {
            pool.destroy();
        }
        if (server != null)
        {
            server.close();
        }
    }

    private PooledAmiClient newPool(int port, boolean enabled) throws Exception
    {
        PooledAmiClient p = new PooledAmiClient();
        setField(p, "poolEnabled", enabled);
        setField(p, "amiPort", port);
        setField(p, "amiUser", "admin");
        setField(p, "amiPassword", "amp111");
        setField(p, "timeout", 3000);
        setField(p, "circuitOpenMs", 60000L);
        setField(p, "metrics", new HotlineMetrics());
        return p;
    }

    /** 轮询发送动作直到拿到非空响应（等待异步建连+登录就绪），超时 8s */
    private String waitAction(String host, String action, long timeoutMs)
    {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String resp = null;
        while (System.currentTimeMillis() < deadline)
        {
            resp = pool.sendAction(host, action);
            if (resp != null)
            {
                return resp;
            }
            try { Thread.sleep(100L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
        }
        return resp;
    }

    @Test
    void sendAction_success_loginDisablesEvents() throws Exception
    {
        server = new FakeAmiServer();
        pool = newPool(server.port(), true);

        String resp = waitAction("127.0.0.1", "Action: SIPshowpeer\r\nPeer: TRUNK_A\r\n\r\n", 8000);
        assertNotNull(resp, "池化长连接应返回动作响应");
        assertTrue(resp.contains("Response: Success"), "响应块应透传: " + resp);
        assertNotNull(server.loginBlock, "服务端应收到 Login");
        assertTrue(server.loginBlock.contains("Events: off"),
                "Login 必须显式 Events: off 保证纯命令通道: " + server.loginBlock);
        assertEquals(1, pool.poolSize());
    }

    @Test
    void sendAction_multipleActions_reuseSingleConnection() throws Exception
    {
        server = new FakeAmiServer();
        pool = newPool(server.port(), true);

        assertNotNull(waitAction("127.0.0.1", "Action: Originate\r\nChannel: SIP/T/100\r\n\r\n", 8000));
        assertNotNull(pool.sendAction("127.0.0.1", "Action: Hangup\r\nChannel: SIP/T-1\r\n\r\n"));
        assertNotNull(pool.sendAction("127.0.0.1", "Action: Redirect\r\nChannel: SIP/T-1\r\n\r\n"));

        assertEquals(3, server.actionCount.get(), "三个动作均应到达服务端");
        assertEquals(1, server.acceptCount.get(), "多个动作必须复用同一条长连接");
        assertTrue(server.received.get(0).contains("Action: Originate"));
        assertTrue(server.received.get(1).contains("Action: Hangup"));
        assertTrue(server.received.get(2).contains("Action: Redirect"));
    }

    @Test
    void sendAction_unsolicitedEventBlock_responseStillPaired() throws Exception
    {
        server = new FakeAmiServer();
        server.sendEventBeforeResponse = true;
        pool = newPool(server.port(), true);

        String resp = waitAction("127.0.0.1", "Action: Hangup\r\nChannel: SIP/T-9\r\n\r\n", 8000);
        assertNotNull(resp, "夹杂事件块时响应仍应正确配对");
        assertTrue(resp.contains("Response: Success"));
        assertFalse(resp.contains("Event:"), "事件块不应串入动作响应: " + resp);
    }

    @Test
    void sendAction_connectionDrops_autoReconnects() throws Exception
    {
        server = new FakeAmiServer();
        pool = newPool(server.port(), true);

        assertNotNull(waitAction("127.0.0.1", "Action: Ping\r\n\r\n", 8000));
        assertEquals(1, server.acceptCount.get());

        server.dropCurrent();

        long deadline = System.currentTimeMillis() + 10000;
        while (server.acceptCount.get() < 2 && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(100L);
        }
        assertEquals(2, server.acceptCount.get(), "断连后应自动重建长连接");
        assertNotNull(waitAction("127.0.0.1", "Action: Ping\r\n\r\n", 8000), "重连后动作应恢复可用");
    }

    @Test
    void sendAction_gatewayDown_circuitOpensAndFailsFast() throws Exception
    {
        int deadPort;
        try (ServerSocket probe = new ServerSocket(0))
        {
            deadPort = probe.getLocalPort();
        }
        pool = newPool(deadPort, true);

        assertNull(pool.sendAction("127.0.0.1", "Action: Ping\r\n\r\n"), "未连接时应快速失败");

        AtomicBoolean circuit = waitCircuitOpen("127.0.0.1", deadPort, 12000);
        assertNotNull(circuit, "应能观测到熔断器条目");
        assertTrue(circuit.get(), "持续不可达应打开熔断器");

        long start = System.currentTimeMillis();
        assertNull(pool.sendAction("127.0.0.1", "Action: Ping\r\n\r\n"), "熔断打开期间动作快速失败");
        assertTrue(System.currentTimeMillis() - start < 1000L, "熔断期间应快速失败而非等待超时");
    }

    @Test
    void sendAction_poolDisabled_returnsNull() throws Exception
    {
        server = new FakeAmiServer();
        pool = newPool(server.port(), false);
        assertFalse(pool.isEnabled());
        assertNull(pool.sendAction("127.0.0.1", "Action: Ping\r\n\r\n"));
        assertEquals(0, pool.poolSize(), "池关闭时不应创建任何连接");
        assertEquals(0, server.acceptCount.get());
    }

    @Test
    void sendActionCollectEvents_collectsEventsUntilComplete() throws Exception
    {
        server = new FakeAmiServer();
        // 模拟 PJSIPShowEndpoints 真实响应形态：Response 块 + N×EndpointList 事件块 + Complete 事件块
        server.pjsipShowScript = "Response: Success\r\nEventList: start\r\nMessage: Following Endpoints\r\n\r\n"
                + "Event: EndpointList\r\nObjectType: endpoint\r\nObjectName: 6001\r\nDeviceState: Not in use\r\n\r\n"
                + "Event: EndpointList\r\nObjectType: endpoint\r\nObjectName: 6002\r\nDeviceState: Unavailable\r\n\r\n"
                + "Event: EndpointListComplete\r\nEventList: Complete\r\n\r\n";
        pool = newPool(server.port(), true);

        String resp = waitCollect("127.0.0.1", "Action: PJSIPShowEndpoints\r\n\r\n", 8000);
        assertNotNull(resp, "多事件收集应返回合并文本");
        assertTrue(resp.contains("EventList: start"), "首个 Response 块应包含在收集文本中: " + resp);
        assertEquals(2, resp.split("Event: EndpointList\n", -1).length - 1,
                "应收集到两个 EndpointList 事件块（Complete 事件不误配）: " + resp);
        assertTrue(resp.contains("Event: EndpointListComplete"), "应以 Complete 事件收尾");
        assertTrue(resp.indexOf("ObjectName: 6001") < resp.indexOf("ObjectName: 6002"), "事件到达顺序应保持");

        // 收集完成后，同连接后续单响应块动作仍正常 FIFO 配对
        assertNotNull(pool.sendAction("127.0.0.1", "Action: Ping\r\n\r\n"), "收集结束后动作应恢复正常");
        assertEquals(2, server.actionCount.get());
    }

    @Test
    void sendActionCollectEvents_errorResponse_completesImmediately() throws Exception
    {
        server = new FakeAmiServer();
        // 错误响应不伴随事件列表与 Complete 事件——应立即返回而非等满超时
        server.pjsipShowScript = "Response: Error\r\nMessage: Permission denied\r\n\r\n";
        pool = newPool(server.port(), true);

        long start = System.currentTimeMillis();
        String resp = waitCollect("127.0.0.1", "Action: PJSIPShowEndpoints\r\n\r\n", 8000);
        long elapsed = System.currentTimeMillis() - start;
        assertNotNull(resp, "错误响应也应立即返回而非等待超时");
        assertTrue(resp.contains("Response: Error"), "应透传错误响应块: " + resp);
        assertTrue(elapsed < 3000L, "应立即结束而非超时，实际 " + elapsed + "ms");
    }

    /** 轮询发送收集动作直到拿到非空响应（等待异步建连+登录就绪），超时 timeoutMs */
    private String waitCollect(String host, String action, long timeoutMs)
    {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String resp = null;
        while (System.currentTimeMillis() < deadline)
        {
            resp = pool.sendActionCollectEvents(host, action, "EndpointListComplete", timeoutMs);
            if (resp != null)
            {
                return resp;
            }
            try { Thread.sleep(100L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
        }
        return resp;
    }

    private AtomicBoolean waitCircuitOpen(String host, int port, long timeoutMs) throws Exception
    {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline)
        {
            Object entries = getField(pool, "entries");
            Object entry = ((Map<?, ?>) entries).get(host + ":" + port);
            if (entry != null)
            {
                AtomicBoolean open = (AtomicBoolean) getField(entry, "circuitOpen");
                if (open.get())
                {
                    return open;
                }
            }
            Thread.sleep(200L);
        }
        Object entries = getField(pool, "entries");
        Object entry = ((Map<?, ?>) entries).get(host + ":" + port);
        return entry == null ? null : (AtomicBoolean) getField(entry, "circuitOpen");
    }

    private static void setField(Object target, String name, Object value) throws Exception
    {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception
    {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    // ---------------------------------------------------------------- 假 AMI 服务端

    /**
     * 最小 AMI 服务端：banner → Login(校验并回 Success) → 按块回 Response。
     * {@code sendEventBeforeResponse=true} 时在每个动作响应前先推一个 Event 块，
     * 模拟事件串包，验证客户端忽略后仍正确 FIFO 配对。
     */
    static class FakeAmiServer implements AutoCloseable
    {
        final ServerSocket serverSocket;
        final AtomicInteger acceptCount = new AtomicInteger();
        final AtomicInteger actionCount = new AtomicInteger();
        final List<String> received = Collections.synchronizedList(new ArrayList<>());
        volatile String loginBlock;
        volatile boolean running = true;
        volatile boolean sendEventBeforeResponse = false;
        /** 非空时：收到 PJSIPShowEndpoints 动作按该脚本原文回写（V2.53 模拟多事件/错误响应） */
        volatile String pjsipShowScript;
        volatile Socket current;
        final Thread acceptThread;

        FakeAmiServer() throws IOException
        {
            serverSocket = new ServerSocket(0);
            acceptThread = new Thread(this::acceptLoop, "fake-ami-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }

        int port()
        {
            return serverSocket.getLocalPort();
        }

        void acceptLoop()
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

        void handle(Socket s)
        {
            try
            {
                s.setSoTimeout(20000);
                OutputStream out = s.getOutputStream();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                out.write("Asterisk Call Manager/2.10.6\r\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                loginBlock = readBlock(reader);
                if (loginBlock == null || !loginBlock.contains("Action: Login"))
                {
                    return;
                }
                out.write("Response: Success\r\nMessage: Authentication accepted\r\n\r\n"
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();
                while (running)
                {
                    String action = readBlock(reader);
                    if (action == null)
                    {
                        break;
                    }
                    received.add(action);
                    actionCount.incrementAndGet();
                    if (action.contains("PJSIPShowEndpoints") && pjsipShowScript != null)
                    {
                        out.write(pjsipShowScript.getBytes(StandardCharsets.UTF_8));
                        out.flush();
                        continue;
                    }
                    if (sendEventBeforeResponse && !action.contains("Action: Ping"))
                    {
                        out.write("Event: Newchannel\r\nChannel: SIP/x-0001\r\n\r\n"
                                .getBytes(StandardCharsets.UTF_8));
                    }
                    if (action.contains("Action: Ping"))
                    {
                        out.write("Response: Success\r\nPing: Pong\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                    }
                    else
                    {
                        out.write("Response: Success\r\nMessage: Action processed\r\n\r\n"
                                .getBytes(StandardCharsets.UTF_8));
                    }
                    out.flush();
                }
            }
            catch (IOException ignored)
            {
                // 连接中断，回到 accept 等待客户端重连
            }
        }

        void dropCurrent()
        {
            Socket c = current;
            if (c != null)
            {
                try { c.close(); } catch (IOException ignored) {}
            }
        }

        @Override
        public void close() throws IOException
        {
            running = false;
            dropCurrent();
            serverSocket.close();
        }

        /** AMI 块以空行结束；EOF 且无内容返回 null */
        static String readBlock(BufferedReader reader) throws IOException
        {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.isEmpty())
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
    }
}
