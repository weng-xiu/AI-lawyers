package ai.lawyers.system.service.lawyers.trunk.gateway.ami;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** B1：常驻 AMI 事件客户端单测（内嵌假 Asterisk AMI 服务端） */
class PersistentAmiEventClientTest
{
    private FakeAmiServer server;
    private PersistentAmiEventClient client;

    @AfterEach
    void tearDown()
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

    private void startPair() throws IOException
    {
        server = new FakeAmiServer();
        server.start();
        client = new PersistentAmiEventClient(
                "127.0.0.1", server.getPort(), "admin", "amp111", 2000);
        client.start();
    }

    /** 轮询等待条件成立（避免引入 awaitility 依赖） */
    private static boolean await(Supplier supplier, long timeoutMs) throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline)
        {
            Object v = supplier.get();
            if (v instanceof Boolean && (Boolean) v)
            {
                return true;
            }
            if (v instanceof Integer && (Integer) v > 0)
            {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    private interface Supplier
    {
        Object get();
    }

    @Test
    void loginWithEventsAllAndReceiveEvent() throws Exception
    {
        startPair();
        List<AmiEvent> received = new CopyOnWriteArrayList<>();
        client.addListener((host, event) -> received.add(event));
        server.startEvents();

        assertThat(await(() -> received.size(), 5000)).isTrue();
        assertThat(received.get(0).getName()).isEqualTo("Newchannel");
        assertThat(received.get(0).get("Uniqueid")).isEqualTo("1");
        assertThat(received.get(0).get("AI_CALL_UUID")).isEqualTo("uuid-abc");
        // 登录块必须显式 Events: all（事件通道），并校验账号
        assertThat(server.lastLoginBlock().get()).contains("Events: all");
        assertThat(server.lastLoginBlock().get()).contains("Username: admin");
        assertThat(client.isConnected()).isTrue();
    }

    @Test
    void responseBlocksAreIgnored() throws Exception
    {
        startPair();
        List<AmiEvent> received = new CopyOnWriteArrayList<>();
        client.addListener((host, event) -> received.add(event));
        server.writeBlock("Response: Pong\r\n\r\n");
        server.startEvents();

        assertThat(await(() -> received.size(), 5000)).isTrue();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).getName()).isEqualTo("Newchannel");
    }

    @Test
    void reconnectsAfterDropAndResumesEvents() throws Exception
    {
        startPair();
        List<AmiEvent> received = new CopyOnWriteArrayList<>();
        client.addListener((host, event) -> received.add(event));
        server.startEvents();
        assertThat(await(() -> received.size(), 5000)).isTrue();

        server.dropCurrentConnection();
        assertThat(await(() -> server.acceptCount().get() >= 2 ? 1 : 0, 10000)).isTrue();
        assertThat(await(() -> received.size() >= 2, 5000)).isTrue();
        assertThat(received.get(1).getName()).isEqualTo("Newchannel");
        assertThat(client.isConnected()).isTrue();
    }

    @Test
    void stopPreventsReconnect() throws Exception
    {
        startPair();
        assertThat(await(() -> server.acceptCount().get(), 5000)).isTrue();
        client.stop();
        server.dropCurrentConnection();
        Thread.sleep(3000);
        assertThat(server.acceptCount().get()).isEqualTo(1);
        assertThat(client.isConnected()).isFalse();
    }

    @Test
    void listenerExceptionDoesNotKillEventLoop() throws Exception
    {
        startPair();
        List<AmiEvent> received = new CopyOnWriteArrayList<>();
        client.addListener((host, event) -> { throw new RuntimeException("boom"); });
        client.addListener((host, event) -> received.add(event));
        server.startEvents();

        assertThat(await(() -> received.size(), 5000)).isTrue();
        assertThat(received).hasSize(1);
        assertThat(client.isConnected()).isTrue();
    }

    /** 内嵌假 Asterisk AMI 服务端：banner → Login 校验（Events: all）→ 可持续推送事件块 */
    private static final class FakeAmiServer implements Runnable
    {
        private final ServerSocket serverSocket;
        private final AtomicInteger acceptCounter = new AtomicInteger(0);
        private final AtomicReference<String> loginBlock = new AtomicReference<>();
        private volatile Socket current;
        private volatile OutputStream currentOut;
        private volatile boolean closed = false;
        private volatile boolean eventsStarted = false;

        FakeAmiServer() throws IOException
        {
            serverSocket = new ServerSocket(0);
        }

        int getPort() { return serverSocket.getLocalPort(); }

        AtomicInteger acceptCount() { return acceptCounter; }

        AtomicReference<String> lastLoginBlock() { return loginBlock; }

        void start()
        {
            Thread thread = new Thread(this, "fake-ami-server");
            thread.setDaemon(true);
            thread.start();
        }

        void startEvents()
        {
            eventsStarted = true;
        }

        void writeBlock(String block) throws IOException
        {
            OutputStream out = currentOut;
            if (out != null)
            {
                out.write(block.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        }

        void dropCurrentConnection() throws IOException
        {
            Socket s = current;
            if (s != null && !s.isClosed())
            {
                s.close();
            }
        }

        void close()
        {
            closed = true;
            try { serverSocket.close(); } catch (IOException ignored) {}
            try { dropCurrentConnection(); } catch (IOException ignored) {}
        }

        @Override
        public void run()
        {
            while (!closed)
            {
                try (Socket sock = serverSocket.accept())
                {
                    acceptCounter.incrementAndGet();
                    current = sock;
                    currentOut = sock.getOutputStream();
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
                    OutputStream out = sock.getOutputStream();
                    out.write("Asterisk Call Manager/7.0.2\r\n".getBytes(StandardCharsets.UTF_8));
                    out.flush();

                    String login = readBlock(reader);
                    loginBlock.set(login);
                    if (login == null || !login.contains("Action: Login") || !login.contains("Events: all"))
                    {
                        continue;
                    }
                    writeBlock("Response: Success\r\nMessage: Authentication accepted\r\n\r\n");
                    // 持续连接：事件开关打开后周期推送；连接被客户端关闭则退出本轮 accept
                    while (!closed && !sock.isClosed())
                    {
                        if (eventsStarted)
                        {
                            writeBlock("Event: Newchannel\r\nUniqueid: 1\r\nAI_CALL_UUID: uuid-abc\r\nCallerIDNum: 13800138000\r\n\r\n");
                        }
                        Thread.sleep(200);
                    }
                }
                catch (Exception e)
                {
                    if (closed) return;
                    // 连接断开/中断：回环重新 accept（模拟重连）
                }
                finally
                {
                    current = null;
                    currentOut = null;
                    // eventsStarted 保持 sticky：重连后无需测试端再次打开
                }
            }
        }

        private String readBlock(BufferedReader reader) throws IOException
        {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.trim().isEmpty())
                {
                    if (sb.length() > 0) break;
                    continue;
                }
                sb.append(line).append('\n');
            }
            return sb.length() == 0 ? null : sb.toString();
        }
    }
}
