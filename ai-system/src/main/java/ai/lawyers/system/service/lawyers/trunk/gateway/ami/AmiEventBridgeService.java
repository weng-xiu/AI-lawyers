package ai.lawyers.system.service.lawyers.trunk.gateway.ami;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.service.lawyers.cluster.LeaderElector;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallTrunkMapper;
import ai.lawyers.system.service.lawyers.trunk.CallEventIdempotencyGuard;
import ai.lawyers.system.service.lawyers.trunk.event.CallEvent;
import ai.lawyers.system.service.lawyers.trunk.event.CallEventBus;

/**
 * P3-B1：Asterisk AMI 事件桥接服务（N9 收口：Asterisk 侧补齐事件监听）。
 *
 * <p>与 ESL 侧 {@code EslEventBridgeService} 完全同构的胶水层：</p>
 * <ol>
 *   <li>启动时连接所有 {@code vendor=ASTERISK} 的中继线路，建立常驻 AMI 事件连接
 *       （Login 后 {@code Events: all}）；命令面动作仍走 {@link PooledAmiClient}
 *       （Events: off），事件/命令双通道分离、互不影响；</li>
 *   <li>事件映射（B2 起归一为统一 {@link CallEvent} 经 {@link CallEventBus} 投递
 *       {@code stream:call-event}，消费侧与 ESL 共用 {@code CallEventProcessor}）：</li>
 *   <ul>
 *     <li>Newchannel：携带 AI_CALL_UUID 变量 = 平台外呼通道（登记 leg 关联），
 *         否则视为入站来话 → INBOUND 事件（消费侧进入电弹屏/排队/IVR）；</li>
 *     <li>VarSet(AI_CALL_UUID)：登记 Asterisk UniqueID → 业务 callUuid 关联；</li>
 *     <li>Dial(Begin)：外呼振铃 → RINGING 事件；被叫 leg 继承业务 uuid；</li>
 *     <li>Newstate(Up)：接通 → ANSWERED 事件（≈CHANNEL_ANSWER）；</li>
 *     <li>BridgeEnter：桥接 → BRIDGED 事件；</li>
 *     <li>Hangup：挂断 → HANGUP 事件（Cause 映射 + 接通时长）
 *         （≈CHANNEL_HANGUP_COMPLETE）；</li>
 *     <li>QueueMemberStatus/QueueMember：队列成员设备态 → AGENT_STATUS 事件
 *         （消费侧同步坐席状态：暂停=忙碌、NOT_INUSE=空闲、UNAVAILABLE=离线）；</li>
 *   </ul>
 *   <li>N3/C4 同款单主竞选：多实例仅 leader 建连消费（独立锁键
 *       {@code ai-law:leader:ami-bridge}），全部连接失活持续 15s 主动让位；</li>
 *   <li>幂等：B4 同款 {@link CallEventIdempotencyGuard}（source=AMI:host），
 *       重放不重复统计；</li>
 *   <li>离线模式：无 PBX/连接失败进重试循环，不阻断启动。</li>
 * </ol>
 *
 * @author ai-lawyers
 */
@Service
public class AmiEventBridgeService implements AmiEventListener
{
    private static final Logger log = LoggerFactory.getLogger(AmiEventBridgeService.class);

    /** B1：AMI 事件监听总开关（部署真实 Asterisk 时置 true） */
    @Value("${call.gateway.ami.enabled:false}")
    private boolean amiEnabled;

    @Value("${call.gateway.asterisk.amiPort:5038}")
    private int defaultAmiPort;

    @Value("${call.gateway.asterisk.amiUser:admin}")
    private String amiUser;

    @Value("${call.gateway.asterisk.amiPassword:amp111}")
    private String amiPassword;

    @Value("${call.gateway.asterisk.timeout:5000}")
    private int timeout;

    /** N3 同款：单主竞选开关（默认 true，仅 leader 消费事件） */
    @Value("${call.gateway.ami.leader-election.enabled:true}")
    private boolean leaderElectionEnabled;

    /** AMI 领导者租约锁键（与 ESL 桥独立，两种 PBX 可由不同实例分别消费） */
    @Value("${call.gateway.ami.leader-election.key:ai-law:leader:ami-bridge}")
    private String leaderLockKey;

    @Value("${call.gateway.ami.leader-election.ttl-seconds:30}")
    private long leaderTtlSeconds;

    @Value("${call.gateway.ami.leader-election.health-check-interval-seconds:5}")
    private long healthCheckIntervalSeconds;

    @Value("${call.gateway.ami.leader-election.unhealthy-yield-seconds:15}")
    private long unhealthyYieldSeconds;

    @Autowired
    private RedisLeaderLock leaderLock;

    private volatile LeaderElector elector;

    @Autowired
    private AiCallTrunkMapper trunkMapper;

    /** B4：事件幂等守卫（source=AMI:host）；为空时退化为不判重 */
    @Autowired(required = false)
    private CallEventIdempotencyGuard idempotencyGuard;

    /** B2：统一事件总线（归一事件投递 stream:call-event，入队失败内部同步降级） */
    @Autowired
    private CallEventBus callEventBus;

    /** host:port -> 常驻事件连接 */
    private final Map<String, PersistentAmiEventClient> clients = new ConcurrentHashMap<>();

    /** Asterisk UniqueID/通道 leg → 业务 callUuid（Newchannel/VarSet 登记，Hangup 清除） */
    private final Map<String, String> uuidByLeg = new ConcurrentHashMap<>();

    /** leg → 接通时刻（Hangup 时计算通话时长） */
    private final Map<String, Long> answeredAtMs = new ConcurrentHashMap<>();

    /** C4 同款：失活检测调度器（仅竞选模式） */
    private ScheduledExecutorService healthChecker;

    /** 本次成为 leader 的时间（启动宽限判定） */
    private volatile long grantedAtMs;

    /** 首次观测到全部连接失活的时间；null=当前健康/未判定 */
    private volatile Long unhealthySinceMs;

    /** 是否存在应消费的 Asterisk 线路 */
    private volatile boolean connectionsExpected;

    @PostConstruct
    public void init()
    {
        if (!amiEnabled)
        {
            log.info("[AMI-Bridge] call.gateway.ami.enabled=false，跳过 Asterisk 事件监听启动");
            return;
        }
        if (leaderElectionEnabled)
        {
            Duration ttl = Duration.ofSeconds(leaderTtlSeconds);
            elector = new LeaderElector(leaderLock, leaderLockKey, ttl,
                    ttl.dividedBy(3), this::handleGranted, this::handleRevoked);
            elector.start();
            startHealthChecker();
        }
        else
        {
            log.warn("[AMI-Bridge] 单主竞选已关闭（call.gateway.ami.leader-election.enabled=false），"
                    + "多实例将各自建连并重复消费事件，仅限单机/应急");
            startAsyncConnectAll();
        }
    }

    @PreDestroy
    public void destroy()
    {
        if (healthChecker != null)
        {
            healthChecker.shutdownNow();
            healthChecker = null;
        }
        if (elector != null)
        {
            elector.stop();
        }
        disconnectAll();
        connectionsExpected = false;
    }

    private void handleGranted()
    {
        grantedAtMs = System.currentTimeMillis();
        unhealthySinceMs = null;
        startAsyncConnectAll();
    }

    private void handleRevoked()
    {
        unhealthySinceMs = null;
        disconnectAll();
    }

    private void startHealthChecker()
    {
        long intervalMs = Math.max(1L, healthCheckIntervalSeconds) * 1000L;
        healthChecker = Executors.newSingleThreadScheduledExecutor(r ->
        {
            Thread t = new Thread(r, "ami-bridge-health");
            t.setDaemon(true);
            return t;
        });
        healthChecker.scheduleAtFixedRate(() ->
        {
            try
            {
                checkUnhealthyAndMaybeYield(System.currentTimeMillis());
            }
            catch (Throwable t)
            {
                log.warn("[AMI-Bridge] 失活检测异常 err={}", t.getMessage());
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    /** 建连放独立 daemon 线程，避免阻塞竞选心跳线程/应用启动 */
    private void startAsyncConnectAll()
    {
        Thread starter = new Thread(this::connectAll, "ami-bridge-starter");
        starter.setDaemon(true);
        starter.start();
    }

    /** 断开并清空全部 AMI 事件连接（丢失领导权/停机时调用） */
    public synchronized void disconnectAll()
    {
        for (PersistentAmiEventClient c : clients.values())
        {
            try { c.stop(); } catch (Exception ignored) {}
        }
        clients.clear();
        log.info("[AMI-Bridge] 已断开全部 Asterisk 事件连接（非领导者/停机）");
    }

    /**
     * 连接/重连所有 Asterisk 类型的中继。新增/修改线路后可手动刷新调用。
     * 竞选模式下非领导者跳过，避免重复消费。
     */
    public synchronized void connectAll()
    {
        if (elector != null && !elector.isLeader())
        {
            log.info("[AMI-Bridge] 本实例非领导者，跳过建连（由当前领导者消费 AMI 事件）");
            return;
        }
        AiCallTrunk query = new AiCallTrunk();
        query.setVendor("ASTERISK");
        List<AiCallTrunk> trunks = trunkMapper.selectAiCallTrunkList(query);
        if (trunks == null || trunks.isEmpty())
        {
            connectionsExpected = false;
            log.info("[AMI-Bridge] 未发现 ASTERISK 类型的中继线路");
            return;
        }
        boolean expected = false;
        for (AiCallTrunk t : trunks)
        {
            if (StringUtils.isEmpty(t.getGatewayHost())) continue;
            expected = true;
            String key = t.getGatewayHost() + ":" + defaultAmiPort;
            if (clients.containsKey(key)) continue;
            try
            {
                PersistentAmiEventClient client = new PersistentAmiEventClient(
                        t.getGatewayHost(), defaultAmiPort, amiUser, amiPassword, timeout);
                client.addListener(this);
                client.start();
                clients.put(key, client);
                log.info("[AMI-Bridge] 已连接 Asterisk: {} (trunk={})", key, t.getTrunkCode());
            }
            catch (Exception e)
            {
                log.error("[AMI-Bridge] 连接 Asterisk 失败: {}", key, e);
            }
        }
        connectionsExpected = expected;
    }

    /** C4 同款：全部连接失活持续阈值秒数 → 主动让位（包级可见供单测） */
    void checkUnhealthyAndMaybeYield(long nowMs)
    {
        if (elector == null || !elector.isLeader() || !connectionsExpected)
        {
            unhealthySinceMs = null;
            return;
        }
        for (PersistentAmiEventClient c : clients.values())
        {
            if (c.isConnected())
            {
                unhealthySinceMs = null;
                return;
            }
        }
        if (nowMs - grantedAtMs < unhealthyYieldSeconds * 1000L)
        {
            return;
        }
        if (unhealthySinceMs == null)
        {
            unhealthySinceMs = nowMs;
            log.warn("[AMI-Bridge] 全部 AMI 事件连接失活，{}s 内不恢复将主动让位", unhealthyYieldSeconds);
            return;
        }
        if (nowMs - unhealthySinceMs >= unhealthyYieldSeconds * 1000L)
        {
            unhealthySinceMs = null;
            elector.yieldLeadership("all AMI event connections unhealthy beyond "
                    + unhealthyYieldSeconds + "s");
        }
    }

    /** C4 同款：本实例是否为 AMI 事件消费者 */
    public boolean isAmiLeader()
    {
        return elector == null || elector.isLeader();
    }

    /** C6：停机排空时主动让出 AMI 消费者角色 */
    public void yieldForDrain()
    {
        if (elector != null && elector.isLeader())
        {
            elector.yieldLeadership("instance draining for shutdown");
            log.info("[AMI-Bridge] 排空开始，已主动让出 AMI 消费者角色");
        }
    }

    /** 当前在线（已登录）的 AMI 事件连接数 */
    public int connectedCount()
    {
        int n = 0;
        for (PersistentAmiEventClient c : clients.values())
        {
            if (c.isConnected())
            {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 事件处理

    @Override
    public void onEvent(String host, AmiEvent event)
    {
        String name = event.getName();
        if (name == null) return;

        // B4：呼叫流事件幂等（第一道防线，与 ESL 侧同款）。判重键用 leg/linkedid——
        // PBX 重发、多实例重复消费、重连补发都不会重复进状态机；
        // VarSet/QueueMember* 为状态快照类事件可合法重复，不参与判重
        if (idempotencyGuard != null && isCallFlowEvent(name))
        {
            String key = firstNonEmpty(event, "Uniqueid", "Uniqueid1", "DestUniqueid", "Uniqueid2", "Linkedid");
            if (StringUtils.isNotEmpty(key) && !idempotencyGuard.firstSeen("AMI:" + host, key, name))
            {
                log.info("[AMI-Bridge] 重复事件已忽略: host={} event={} key={}", host, name, key);
                return;
            }
        }
        try
        {
            switch (name)
            {
                case "Newchannel":
                    onNewchannel(host, event);
                    break;
                case "VarSet":
                    onVarSet(event);
                    break;
                case "Dial":
                    onDial(host, event);
                    break;
                case "Newstate":
                    onNewstate(host, event);
                    break;
                case "BridgeEnter":
                    onBridgeEnter(host, event);
                    break;
                case "Hangup":
                    onHangup(host, event);
                    break;
                case "QueueMemberStatus":
                case "QueueMember":
                    onQueueMember(host, event);
                    break;
                default:
                    break;
            }
        }
        catch (Exception e)
        {
            log.error("[AMI-Bridge] 处理事件异常: host={} event={}", host, name, e);
        }
    }

    /** Newchannel：外呼通道（携带 AI_CALL_UUID）登记 leg 关联；入站来话归一为 INBOUND 事件入总线 */
    private void onNewchannel(String host, AmiEvent event)
    {
        String uniqueId = event.get("Uniqueid");
        if (StringUtils.isEmpty(uniqueId))
        {
            return;
        }
        String callUuidVar = event.get("AI_CALL_UUID");
        boolean outbound = StringUtils.isNotEmpty(callUuidVar);
        if (outbound)
        {
            uuidByLeg.put(uniqueId, callUuidVar);
            log.debug("[AMI] 外呼通道创建: uniqueId={} uuid={}", uniqueId, callUuidVar);
            return;
        }
        // 非 originate 通道（Context 非外呼 context）→ 入站来话
        String context = event.get("Context");
        if ("from-internal".equalsIgnoreCase(context))
        {
            return;
        }
        String caller = event.get("CallerIDNum");
        String callee = event.get("Exten");
        log.info("[AMI] 入站来话: uniqueId={} {} -> {} ctx={}", uniqueId, caller, callee, context);
        CallEvent ce = CallEvent.of("AMI:" + host, CallEvent.INBOUND, uniqueId);
        ce.setChannel(uniqueId);
        ce.setLinkedid(event.get("Linkedid"));
        ce.setCaller(caller);
        ce.setCallee(callee);
        Map<String, Object> payload = new HashMap<>();
        payload.put("rawEventName", "Newchannel");
        putIfNotEmpty(payload, "context", context);
        ce.setPayload(payload);
        callEventBus.dispatch(ce);
    }

    /** VarSet(AI_CALL_UUID)：登记 Asterisk UniqueID → 业务 callUuid 关联 */
    private void onVarSet(AmiEvent event)
    {
        if (!"AI_CALL_UUID".equalsIgnoreCase(event.get("Variable")))
        {
            return;
        }
        String uniqueId = event.get("Uniqueid");
        String value = event.get("Value");
        if (StringUtils.isNotEmpty(uniqueId) && StringUtils.isNotEmpty(value))
        {
            uuidByLeg.put(uniqueId, value);
        }
    }

    /** Dial(Begin)：外呼振铃 → 归一 RINGING 事件；被叫 leg 继承业务 uuid（后续事件按其路由） */
    private void onDial(String host, AmiEvent event)
    {
        String subEvent = event.get("SubEvent");
        if (StringUtils.isNotEmpty(subEvent) && !"Begin".equalsIgnoreCase(subEvent))
        {
            return;
        }
        String callerLeg = event.get("Uniqueid");
        String destLeg = event.get("DestUniqueid");
        String uuid = resolveUuid(callerLeg, event);
        if (StringUtils.isEmpty(uuid))
        {
            return;
        }
        if (StringUtils.isNotEmpty(destLeg))
        {
            uuidByLeg.putIfAbsent(destLeg, uuid);
        }
        log.info("[AMI] 外呼振铃: uuid={}", uuid);
        CallEvent ce = CallEvent.of("AMI:" + host, CallEvent.RINGING, uuid);
        ce.setChannel(callerLeg);
        ce.setLinkedid(destLeg);
        Map<String, Object> payload = new HashMap<>();
        payload.put("rawEventName", "Dial");
        ce.setPayload(payload);
        callEventBus.dispatch(ce);
    }

    /** Newstate(ChannelStateDesc=Up)：接通 → 归一 ANSWERED 事件（≈ESL CHANNEL_ANSWER） */
    private void onNewstate(String host, AmiEvent event)
    {
        if (!"Up".equalsIgnoreCase(event.get("ChannelStateDesc")))
        {
            return;
        }
        String uniqueId = event.get("Uniqueid");
        String uuid = resolveUuid(uniqueId, event);
        if (StringUtils.isEmpty(uuid))
        {
            return;
        }
        if (StringUtils.isNotEmpty(uniqueId))
        {
            answeredAtMs.putIfAbsent(uniqueId, System.currentTimeMillis());
        }
        log.info("[AMI] 通道接通: uuid={}", uuid);
        CallEvent ce = CallEvent.of("AMI:" + host, CallEvent.ANSWERED, uuid);
        ce.setChannel(uniqueId);
        ce.setLinkedid(event.get("Linkedid"));
        ce.setCaller(event.get("CallerIDNum"));
        ce.setCallee(event.get("Exten"));
        Map<String, Object> payload = new HashMap<>();
        payload.put("rawEventName", "Newstate");
        ce.setPayload(payload);
        callEventBus.dispatch(ce);
    }

    /** BridgeEnter：桥接 → 归一 BRIDGED 事件（坐席推送） */
    private void onBridgeEnter(String host, AmiEvent event)
    {
        String leg = firstNonEmpty(event, "Uniqueid1", "Uniqueid2", "Uniqueid");
        String uuid = resolveUuid(leg, event);
        if (StringUtils.isEmpty(uuid))
        {
            return;
        }
        log.info("[AMI] 通道桥接: uuid={}", uuid);
        CallEvent ce = CallEvent.of("AMI:" + host, CallEvent.BRIDGED, uuid);
        ce.setChannel(leg);
        ce.setLinkedid(event.get("Linkedid"));
        Map<String, Object> payload = new HashMap<>();
        payload.put("rawEventName", "BridgeEnter");
        ce.setPayload(payload);
        callEventBus.dispatch(ce);
    }

    /**
     * Hangup：挂断 → 归一 HANGUP 事件（Cause 映射 + 接通时长），清除 leg 关联。
     * 不置 autoTicket 旗标——保持 V2.50 边界：入站自动建工单仅 ESL 侧。
     */
    private void onHangup(String host, AmiEvent event)
    {
        String uniqueId = event.get("Uniqueid");
        String uuid = resolveUuid(uniqueId, event);
        String cause = mapHangupCause(event.get("Cause"), event.get("Cause-txt"));
        long talkDuration = 0;
        Long answeredAt = uniqueId == null ? null : answeredAtMs.get(uniqueId);
        if (answeredAt != null)
        {
            talkDuration = Math.max(0L, (System.currentTimeMillis() - answeredAt) / 1000L);
        }
        // 与 ESL 侧对齐：全部挂断事件进状态机（未知 uuid 由 dispatch 按拨号日志对账兜底，
        // 查不到 dial_log 仅 WARN，不影响其它呼叫）
        log.info("[AMI] 通道挂断: uuid={} cause={} billsec={}", uuid, cause, talkDuration);

        CallEvent ce = CallEvent.of("AMI:" + host, CallEvent.HANGUP, uuid);
        ce.setChannel(uniqueId);
        ce.setLinkedid(event.get("Linkedid"));
        ce.setCaller(event.get("CallerIDNum"));
        Map<String, Object> payload = new HashMap<>();
        payload.put("rawEventName", "Hangup");
        payload.put("hangupCause", cause);
        payload.put("talkDuration", (int) talkDuration);
        ce.setPayload(payload);
        callEventBus.dispatch(ce);
        cleanupLeg(uniqueId);
    }

    /** QueueMemberStatus/QueueMember：队列成员设备态 → 归一 AGENT_STATUS 事件（坐席状态同步在消费侧） */
    private void onQueueMember(String host, AmiEvent event)
    {
        String ext = extractExtension(firstNonEmpty(event, "Interface", "MemberName"));
        if (StringUtils.isEmpty(ext))
        {
            return;
        }
        String paused = event.get("Paused");
        String memberStatus = firstNonEmpty(event, "MemberStatus", "Status");
        String target = mapAgentStatus(paused, memberStatus);
        if (target == null)
        {
            return;
        }
        CallEvent ce = CallEvent.of("AMI:" + host, CallEvent.AGENT_STATUS, null);
        Map<String, Object> payload = new HashMap<>();
        payload.put("rawEventName", event.getName());
        payload.put("ext", ext);
        payload.put("targetStatus", target);
        putIfNotEmpty(payload, "memberStatus", memberStatus);
        ce.setPayload(payload);
        callEventBus.dispatch(ce);
    }

    private void cleanupLeg(String uniqueId)
    {
        if (StringUtils.isEmpty(uniqueId))
        {
            return;
        }
        // 各 leg 挂断事件各自到达时清理自身关联，避免误清仍在途的其它 leg
        answeredAtMs.remove(uniqueId);
        uuidByLeg.remove(uniqueId);
    }

    /** 解析业务 callUuid：leg 关联表优先，其次事件自带 AI_CALL_UUID，最后 Asterisk UniqueID 兜底 */
    private String resolveUuid(String uniqueId, AmiEvent event)
    {
        if (StringUtils.isEmpty(uniqueId))
        {
            return event.get("AI_CALL_UUID");
        }
        String uuid = uuidByLeg.get(uniqueId);
        if (StringUtils.isNotEmpty(uuid))
        {
            return uuid;
        }
        String varUuid = event.get("AI_CALL_UUID");
        if (StringUtils.isNotEmpty(varUuid))
        {
            uuidByLeg.putIfAbsent(uniqueId, varUuid);
            return varUuid;
        }
        return uniqueId;
    }

    /** 值非空才放入 payload（保持事件载荷精简） */
    private static void putIfNotEmpty(Map<String, Object> payload, String key, String value)
    {
        if (StringUtils.isNotEmpty(value))
        {
            payload.put(key, value);
        }
    }

    // ---------------------------------------------------------------- 静态映射（包级可见供单测）

    /**
     * Asterisk Hangup Cause → ESL 风格挂断原因。
     * 常见映射：16=NORMAL_CLEARING、17=USER_BUSY、18/19=NO_ANSWER、
     * 21=CALL_REJECTED、34=NORMAL_CIRCUIT_CONGESTION；其余用 Cause-txt 下划线归一，
     * 无法解析时 CAUSE_&lt;code&gt;。
     */
    static String mapHangupCause(String code, String causeTxt)
    {
        if (code != null)
        {
            switch (code.trim())
            {
                case "1": return "UNALLOCATED_NUMBER";
                case "16": return "NORMAL_CLEARING";
                case "17": return "USER_BUSY";
                case "18": return "NO_ANSWER";
                case "19": return "NO_ANSWER";
                case "21": return "CALL_REJECTED";
                case "27": return "DESTINATION_OUT_OF_ORDER";
                case "28": return "INVALID_NUMBER_FORMAT";
                case "34": return "NORMAL_CIRCUIT_CONGESTION";
                case "487": return "ORIGINATOR_CANCEL";
                case "503": return "SERVICE_UNAVAILABLE";
                default: break;
            }
        }
        if (StringUtils.isNotEmpty(causeTxt))
        {
            return causeTxt.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_");
        }
        return code == null || code.trim().isEmpty() ? "UNKNOWN" : "CAUSE_" + code.trim();
    }

    /** 从队列成员 Interface/MemberName 提取分机号：SIP/1001、PJSIP/200@from-internal、Local/300@x → 1001/200/300 */
    static String extractExtension(String iface)
    {
        if (StringUtils.isEmpty(iface))
        {
            return null;
        }
        String s = iface.trim();
        int slash = s.indexOf('/');
        if (slash >= 0)
        {
            s = s.substring(slash + 1);
        }
        int at = s.indexOf('@');
        if (at >= 0)
        {
            s = s.substring(0, at);
        }
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * 队列成员设备态 → 坐席状态码（0=离线/1=空闲/2=忙碌）。
     * Paused=yes → 忙碌；INUSE/BUSY/RINGING → 忙碌；NOT_INUSE → 空闲；
     * UNAVAILABLE/INVALID → 离线；其余（未知/数值态）返回 null 不动坐席状态。
     */
    static String mapAgentStatus(String paused, String memberStatus)
    {
        if ("yes".equalsIgnoreCase(paused))
        {
            return "2";
        }
        if ("no".equalsIgnoreCase(paused))
        {
            return "1";
        }
        if (StringUtils.isEmpty(memberStatus))
        {
            return null;
        }
        switch (memberStatus.trim().toUpperCase())
        {
            case "INUSE":
            case "BUSY":
            case "RINGING":
            case "ONHOLD":
                return "2";
            case "NOT_INUSE":
            case "WAITING":
                return "1";
            case "UNAVAILABLE":
            case "INVALID":
            case "UNKNOWN":
                return "0";
            default:
                return null;
        }
    }

    private String firstNonEmpty(AmiEvent event, String... keys)
    {
        if (keys == null) return null;
        for (String k : keys)
        {
            String v = event.get(k);
            if (StringUtils.isNotEmpty(v)) return v;
        }
        return null;
    }

    /** 是否参与幂等判重的呼叫流事件（包级静态便于单测） */
    static boolean isCallFlowEvent(String eventName)
    {
        switch (eventName == null ? "" : eventName)
        {
            case "Newchannel":
            case "Newstate":
            case "Dial":
            case "BridgeEnter":
            case "Hangup":
                return true;
            default:
                return false;
        }
    }
}
