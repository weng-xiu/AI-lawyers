package ai.lawyers.system.service.lawyers.trunk.gateway.esl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ai.lawyers.system.service.lawyers.cluster.LeaderElector;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * P3-C4：{@link EslEventBridgeService} 单主失活检测与主动让位测试。
 *
 * <p>用真实 {@link LeaderElector}（mock 锁底座）驱动领导状态，反射注入私有字段，
 * 按时间戳驱动 {@code checkUnhealthyAndMaybeYield}。</p>
 */
class EslEventBridgeServiceTest
{
    private static final String LOCK_KEY = "leader:esl-bridge";

    private EslEventBridgeService bridge;
    private RedisLeaderLock lock;
    private LeaderElector elector;
    private Map<String, FreeSwitchEslInboundClient> clients;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception
    {
        bridge = new EslEventBridgeService();
        lock = mock(RedisLeaderLock.class);
        when(lock.isEnabled()).thenReturn(true);
        when(lock.acquireLeader(eq(LOCK_KEY), any(Duration.class))).thenReturn(true);
        elector = new LeaderElector(lock, LOCK_KEY,
                Duration.ofSeconds(30), Duration.ofSeconds(10),
                () -> {}, () -> {});
        setField("elector", elector);
        setField("unhealthyYieldSeconds", 15L);
        clients = (Map<String, FreeSwitchEslInboundClient>) getField("clients");
    }

    private void setField(String name, Object value) throws Exception
    {
        Field f = EslEventBridgeService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(bridge, value);
    }

    private Object getField(String name) throws Exception
    {
        Field f = EslEventBridgeService.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(base());
    }

    private EslEventBridgeService base()
    {
        return bridge;
    }

    private void becomeLeader() throws Exception
    {
        Method tick = LeaderElector.class.getDeclaredMethod("tick");
        tick.setAccessible(true);
        tick.invoke(elector);
        assertThat(elector.isLeader()).isTrue();
    }

    private FreeSwitchEslInboundClient mockClient(boolean connected)
    {
        FreeSwitchEslInboundClient c = mock(FreeSwitchEslInboundClient.class);
        when(c.isConnected()).thenReturn(connected);
        return c;
    }

    @Test
    void healthy_oneConnected_noYield() throws Exception
    {
        becomeLeader();
        setField("connectionsExpected", true);
        clients.put("h1:8021", mockClient(false));
        clients.put("h2:8021", mockClient(true));

        bridge.checkUnhealthyAndMaybeYield(System.currentTimeMillis());

        assertThat(elector.isLeader()).isTrue();
        assertThat(getField("unhealthySinceMs")).isNull();
    }

    @Test
    void allDisconnected_withinGrace_clockStartsNoYield() throws Exception
    {
        becomeLeader();
        setField("connectionsExpected", true);
        clients.put("h1:8021", mockClient(false));
        long now = System.currentTimeMillis();
        setField("grantedAtMs", now - 5_000L); // 成为 leader 仅 5s（<15s 窗口）

        bridge.checkUnhealthyAndMaybeYield(now);

        assertThat(elector.isLeader()).isTrue();
        assertThat(getField("unhealthySinceMs")).isNull(); // 宽限内不计时
    }

    @Test
    void allDisconnected_firstObservation_marksSince() throws Exception
    {
        becomeLeader();
        setField("connectionsExpected", true);
        clients.put("h1:8021", mockClient(false));
        long now = System.currentTimeMillis();
        setField("grantedAtMs", now - 20_000L);

        bridge.checkUnhealthyAndMaybeYield(now);

        assertThat(elector.isLeader()).isTrue();
        assertThat(getField("unhealthySinceMs")).isEqualTo(now);
    }

    @Test
    void allDisconnected_beyondWindow_yieldsLeadership() throws Exception
    {
        becomeLeader();
        setField("connectionsExpected", true);
        clients.put("h1:8021", mockClient(false));
        long t0 = 1_000_000L;
        setField("grantedAtMs", t0 - 20_000L);

        bridge.checkUnhealthyAndMaybeYield(t0);          // 首次失活
        bridge.checkUnhealthyAndMaybeYield(t0 + 16_000L); // 持续超窗 → 让位

        assertThat(elector.isLeader()).isFalse();
        verify(lock).resign(LOCK_KEY);
        assertThat(getField("unhealthySinceMs")).isNull();
    }

    @Test
    void connectionRecovers_resetsClock() throws Exception
    {
        becomeLeader();
        setField("connectionsExpected", true);
        FreeSwitchEslInboundClient c = mockClient(false);
        clients.put("h1:8021", c);
        long t0 = 2_000_000L;
        setField("grantedAtMs", t0 - 20_000L);

        bridge.checkUnhealthyAndMaybeYield(t0);
        assertThat(getField("unhealthySinceMs")).isEqualTo(t0);

        when(c.isConnected()).thenReturn(true);
        bridge.checkUnhealthyAndMaybeYield(t0 + 10_000L);
        assertThat(getField("unhealthySinceMs")).isNull();
        assertThat(elector.isLeader()).isTrue();
    }

    @Test
    void followerState_noYieldAndResets() throws Exception
    {
        // 不调用 becomeLeader：elector 处于跟随态
        setField("connectionsExpected", true);
        clients.put("h1:8021", mockClient(false));

        bridge.checkUnhealthyAndMaybeYield(System.currentTimeMillis());

        assertThat(elector.isLeader()).isFalse();
        assertThat(getField("unhealthySinceMs")).isNull();
    }

    @Test
    void noExpectedConnections_noYield() throws Exception
    {
        becomeLeader();
        setField("connectionsExpected", false);

        bridge.checkUnhealthyAndMaybeYield(System.currentTimeMillis());

        assertThat(elector.isLeader()).isTrue();
        assertThat(getField("unhealthySinceMs")).isNull();
    }

    @Test
    void electionDisabled_electorNull_noYield() throws Exception
    {
        setField("elector", null);
        setField("connectionsExpected", true);

        bridge.checkUnhealthyAndMaybeYield(System.currentTimeMillis()); // 不应 NPE

        assertThat(bridge.isEslLeader()).isTrue();
    }
}
