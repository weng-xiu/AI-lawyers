package ai.lawyers.system.service.lawyers.trunk.gateway.esl;

import java.io.IOException;
import java.io.InputStream;
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
 * B3 命令面池化：PooledEslClient 单元测试（内嵌假 ESL 服务端）。
 *
 * <p>覆盖：长连接复用（多命令单连接）、命令回执配对、断连自动重建、
 * 熔断器打开与快速失败、池关闭时快速失败。</p>
 */
class PooledEslClientTest
{
    private PooledEslClient pool;
    private FakeEslServer server;

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

    private PooledEslClient newPool(int port, boolean enabled) throws Exception
    {
        PooledEslClient p = new PooledEslClient();
        setField(p, "poolEnabled", enabled);
        setField(p, "eslPort", port);
        setField(p, "eslPassword", "ClueCon");
        setField(p, "timeout", 3000);
        setField(p, "circuitOpenMs", 60000L);
        setField(p, "metrics", new HotlineMetrics());
        return p;
    }

    /** 轮询发送命令直到拿到非空响应（等待异步建连+鉴权就绪），超时 8s */
    private String waitCommand(String host, String command, long timeoutMs)
    {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String resp = null;
        while (System.currentTimeMillis() < deadline)
        {
            resp = pool.sendCommand(host, command);
            if (resp != null)
            {
                return resp;
            }
            try { Thread.sleep(100L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
        }
        return resp;
    }

    @Test
    void sendCommand_success_overPooledConnection() throws Exception
    {
        server = new FakeEslServer();
        pool = newPool(server.port(), true);

        String resp = waitCommand("127.0.0.1", "api sofia status gateway TRUNK_A", 8000);
        assertNotNull(resp, "池化长连接应返回 api/response");
        assertTrue(resp.contains("REGED"), "api 响应体应透传: " + resp);
        assertEquals(1, pool.poolSize());
    }

    @Test
    void sendCommand_multipleCommands_reuseSingleConnection() throws Exception
    {
        server = new FakeEslServer();
        pool = newPool(server.port(), true);

        assertNotNull(waitCommand("127.0.0.1", "bgapi originate {a=1}sofia/gateway/T/100 &park()", 8000));
        assertNotNull(pool.sendCommand("127.0.0.1", "api uuid_kill u1"));
        assertNotNull(pool.sendCommand("127.0.0.1", "api uuid_transfer u1 -both user/1001 XML default"));

        assertEquals(3, server.commandCount.get(), "三条命令均应到达服务端");
        assertEquals(1, server.acceptCount.get(), "多条命令必须复用同一条长连接");
        assertTrue(server.received.get(0).contains("originate"));
        assertTrue(server.received.get(1).contains("uuid_kill"));
        assertTrue(server.received.get(2).contains("uuid_transfer"));
    }

    @Test
    void sendCommand_connectionDrops_autoReconnects() throws Exception
    {
        server = new FakeEslServer();
        pool = newPool(server.port(), true);

        assertNotNull(waitCommand("127.0.0.1", "api status", 8000));
        assertEquals(1, server.acceptCount.get());

        // 服务端断开当前连接 → 客户端读线程感知后指数退避重建（基数 2s）
        server.dropCurrent();

        long deadline = System.currentTimeMillis() + 10000;
        while (server.acceptCount.get() < 2 && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(100L);
        }
        assertEquals(2, server.acceptCount.get(), "断连后应自动重建长连接");
        assertNotNull(waitCommand("127.0.0.1", "api status", 8000), "重连后命令应恢复可用");
    }

    @Test
    void sendCommand_gatewayDown_circuitOpensAndFailsFast() throws Exception
    {
        // 选一个未被监听的端口模拟网关宕机
        int deadPort;
        try (ServerSocket probe = new ServerSocket(0))
        {
            deadPort = probe.getLocalPort();
        }
        pool = newPool(deadPort, true);

        assertNull(pool.sendCommand("127.0.0.1", "api status"), "未连接时应快速失败");

        // 监测线程 1s 采样，连续 5s 未就绪打开熔断
        AtomicBoolean circuit = waitCircuitOpen("127.0.0.1", deadPort, 12000);
        assertNotNull(circuit, "应能观测到熔断器条目");
        assertTrue(circuit.get(), "持续不可达应打开熔断器");

        long start = System.currentTimeMillis();
        assertNull(pool.sendCommand("127.0.0.1", "api status"), "熔断打开期间命令快速失败");
        assertTrue(System.currentTimeMillis() - start < 1000L, "熔断期间应快速失败而非等待超时");
    }

    @Test
    void sendCommand_poolDisabled_returnsNull() throws Exception
    {
        server = new FakeEslServer();
        pool = newPool(server.port(), false);
        assertFalse(pool.isEnabled());
        assertNull(pool.sendCommand("127.0.0.1", "api status"));
        assertEquals(0, pool.poolSize(), "池关闭时不应创建任何连接");
        assertEquals(0, server.acceptCount.get());
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

    // ---------------------------------------------------------------- 假 ESL 服务端

    /**
     * 最小 ESL 服务端：auth/request → +OK →（命令模式无 event 订阅）按帧回复。
     * "api " 前缀命令回 api/response（带 Content-Length body），其余回 command/reply。
     */
    static class FakeEslServer implements AutoCloseable
    {
        final ServerSocket serverSocket;
        final AtomicInteger acceptCount = new AtomicInteger();
        final AtomicInteger commandCount = new AtomicInteger();
        final List<String> received = Collections.synchronizedList(new ArrayList<>());
        volatile boolean running = true;
        volatile Socket current;
        final Thread acceptThread;

        FakeEslServer() throws IOException
        {
            serverSocket = new ServerSocket(0);
            acceptThread = new Thread(this::acceptLoop, "fake-esl-accept");
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
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                out.write("Content-Type: auth/request\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                String auth = readBlock(reader);
                if (auth == null || !auth.startsWith("auth "))
                {
                    return;
                }
                out.write("Content-Type: command/reply\nReply-Text: +OK accepted\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                while (running)
                {
                    String req = readBlock(reader);
                    if (req == null)
                    {
                        break; // EOF：客户端断开
                    }
                    if (req.isEmpty())
                    {
                        continue; // 客户端空闲心跳（单个换行），不回包
                    }
                    received.add(req);
                    commandCount.incrementAndGet();
                    if (req.startsWith("api "))
                    {
                        byte[] body = "State REGED".getBytes(StandardCharsets.UTF_8);
                        String frame = "Content-Type: api/response\nContent-Length: " + body.length + "\n\n";
                        out.write(frame.getBytes(StandardCharsets.UTF_8));
                        out.write(body);
                    }
                    else
                    {
                        out.write("Content-Type: command/reply\nReply-Text: +OK\n\n".getBytes(StandardCharsets.UTF_8));
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

        /** 读一个请求块：按行读到空行（客户端命令均无 body）；EOF 且无内容返回 null */
        static String readBlock(java.io.BufferedReader reader) throws IOException
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
    }
}
