package ai.lawyers.system.service.lawyers.trunk.gateway.esl;

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
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.trunk.AiCallDialLog;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallTrunkMapper;
import ai.lawyers.system.service.lawyers.trunk.event.CallEvent;
import ai.lawyers.system.service.lawyers.trunk.event.CallEventBus;

/**
 * FreeSWITCH ESL 事件桥接服务
 *
 * <p>职责：</p>
 * <ol>
 *   <li>启动时连接所有 {@code vendor=FREESWITCH} 的中继线路，建立入站长连接；</li>
 *   <li>接收 CHANNEL_CREATE / CHANNEL_ANSWER / CHANNEL_HANGUP_COMPLETE /
 *       DTMF / RECORD_STOP 等事件，B2 起归一为统一 {@link CallEvent}
 *       经 {@link CallEventBus} 投递 {@code stream:call-event}；</li>
 *   <li>消费侧 {@code CallEventProcessor} 统一驱动：状态机更新、坐席 WebSocket 推送、
 *       入站处理（来电弹屏 → 排队 → 分配坐席）、录音回写与质检投递、入站自动建工单；</li>
 *   <li>生产侧保留：幂等守卫（第一道防线）、RECORD_STOP 话单关联解析、
 *       C4 单主竞选/失活让位、命令面 hangupCall/queryPbxChannelCount。</li>
 * </ol>
 *
 * <p>本类是 README 架构中 "SIP 网关 → IVR/排队机 → 坐席工作台" 事件链路的核心胶水层。</p>
 *
 * @author ai-lawyers
 */
@Service
public class EslEventBridgeService implements EslEventListener
{
    private static final Logger log = LoggerFactory.getLogger(EslEventBridgeService.class);

    @Value("${call.gateway.esl.enabled:false}")
    private boolean eslEnabled;

    @Value("${call.gateway.freeswitch.eslPort:8021}")
    private int defaultEslPort;

    @Value("${call.gateway.freeswitch.eslPassword:ClueCon}")
    private String defaultEslPassword;

    @Value("${call.gateway.freeswitch.context:default}")
    private String defaultContext;

    /**
     * N3：ESL 单主竞选开关。true（默认）时全集群仅领导者实例建立入站 ESL 长连接消费事件，
     * 领导者崩溃后租约 TTL 到期自动故障切换；false 恢复旧行为（每实例各自建连，多实例重复消费）。
     */
    @Value("${call.gateway.esl.leader-election.enabled:true}")
    private boolean leaderElectionEnabled;

    /** N3：ESL 领导者租约在 Redis 中的锁键 */
    @Value("${call.gateway.esl.leader-election.key:ai-law:leader:esl-bridge}")
    private String leaderLockKey;

    /** N3：领导者租约 TTL（秒），心跳约为 TTL/3 */
    @Value("${call.gateway.esl.leader-election.ttl-seconds:30}")
    private long leaderTtlSeconds;

    /**
     * C4：连接失活检测间隔（秒）。
     */
    @Value("${call.gateway.esl.leader-election.health-check-interval-seconds:5}")
    private long healthCheckIntervalSeconds;

    /**
     * C4：全部 ESL 连接失活（无一条 isConnected）持续该秒数后，leader 主动让位，
     * 让能连通 FreeSWITCH 的实例接管（弥补"Redis 心跳正常但 PBX 网络分区"时
     * 租约不掉、无人接管的缺陷）。默认 15s：约 3 个检测周期、小于租约 30s。
     */
    @Value("${call.gateway.esl.leader-election.unhealthy-yield-seconds:15}")
    private long unhealthyYieldSeconds;

    @Autowired
    private RedisLeaderLock leaderLock;

    /** 单主竞选器；非竞选模式（或 ESL 关闭）时为 null */
    private volatile LeaderElector elector;

    @Autowired
    private AiCallTrunkMapper trunkMapper;

    /** RECORD_STOP 话单关联解析（多候选 UUID 反查，留生产侧） */
    @Autowired
    private AiCallDialLogMapper dialLogMapper;

    @Autowired
    private AiCallRecordMapper callRecordMapper;

    /** B2：统一事件总线（归一事件投递 stream:call-event，入队失败内部同步降级） */
    @Autowired
    private CallEventBus callEventBus;

    /** B4：PBX 事件幂等守卫（第一道防线）；为空时退化为不判重（兼容旧行为） */
    @Autowired(required = false)
    private ai.lawyers.system.service.lawyers.trunk.CallEventIdempotencyGuard idempotencyGuard;

    /** host:port -> client */
    private final Map<String, FreeSwitchEslInboundClient> clients = new ConcurrentHashMap<>();

    /** C4：失活检测调度器（仅竞选模式） */
    private ScheduledExecutorService healthChecker;

    /** C4：本次成为 leader 的时间（启动宽限判定） */
    private volatile long grantedAtMs;

    /** C4：首次观测到全部连接失活的时间；null=当前健康/未判定 */
    private volatile Long unhealthySinceMs;

    /** C4：是否存在应消费的 FreeSWITCH 线路（无线路时空 clients 不应触发让位） */
    private volatile boolean connectionsExpected;

    @PostConstruct
    public void init()
    {
        if (!eslEnabled)
        {
            log.info("[ESL-Bridge] call.gateway.esl.enabled=false，跳过 FreeSWITCH 事件监听启动");
            return;
        }
        if (leaderElectionEnabled)
        {
            // N3：先竞选再建连，仅领导者持有入站 ESL 长连接；崩溃后租约到期自动故障切换
            Duration ttl = Duration.ofSeconds(leaderTtlSeconds);
            elector = new LeaderElector(leaderLock, leaderLockKey, ttl,
                    ttl.dividedBy(3), this::handleGranted, this::handleRevoked);
            elector.start();
            startHealthChecker();
        }
        else
        {
            log.warn("[ESL-Bridge] 单主竞选已关闭（call.gateway.esl.leader-election.enabled=false），"
                    + "多实例将各自建连并重复消费事件，仅限单机/应急");
            startAsyncConnectAll();
        }
    }

    /** C4：成为 leader——记录时间并异步建连 */
    private void handleGranted()
    {
        grantedAtMs = System.currentTimeMillis();
        unhealthySinceMs = null;
        startAsyncConnectAll();
    }

    /** C4：丢失领导权——重置失活状态并断连防双主 */
    private void handleRevoked()
    {
        unhealthySinceMs = null;
        disconnectAll();
    }

    /** C4：启动连接失活检测（daemon 单线程，固定速率） */
    private void startHealthChecker()
    {
        long intervalMs = Math.max(1L, healthCheckIntervalSeconds) * 1000L;
        healthChecker = Executors.newSingleThreadScheduledExecutor(r ->
        {
            Thread t = new Thread(r, "esl-bridge-health");
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
                log.warn("[ESL-Bridge] 失活检测异常 err={}", t.getMessage());
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    /** 建连放到独立 daemon 线程，避免阻塞竞选心跳线程/应用启动 */
    private void startAsyncConnectAll()
    {
        Thread starter = new Thread(this::connectAll, "esl-bridge-starter");
        starter.setDaemon(true);
        starter.start();
    }

    @PreDestroy
    public void destroy()
    {
        // 先停止失活检测与心跳并主动放弃领导权（加速对端接管），再断连
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

    /** 断开并清空全部 ESL 入站连接（丢失领导权/停机时调用） */
    public synchronized void disconnectAll()
    {
        for (FreeSwitchEslInboundClient c : clients.values())
        {
            try { c.stop(); } catch (Exception ignored) {}
        }
        clients.clear();
        log.info("[ESL-Bridge] 已断开全部 FreeSWITCH 入站连接（非领导者/停机）");
    }

    /**
     * 连接/重连所有 FreeSWITCH 类型的中继。供管理界面在新增/修改线路后手动刷新调用。
     * N3：竞选模式下非领导者直接跳过，避免跟随者建立重复事件连接。
     */
    public synchronized void connectAll()
    {
        if (elector != null && !elector.isLeader())
        {
            log.info("[ESL-Bridge] 本实例非领导者，跳过建连（由当前领导者消费 ESL 事件）");
            return;
        }
        AiCallTrunk query = new AiCallTrunk();
        query.setVendor("FREESWITCH");
        List<AiCallTrunk> trunks = trunkMapper.selectAiCallTrunkList(query);
        if (trunks == null || trunks.isEmpty())
        {
            connectionsExpected = false;
            log.info("[ESL-Bridge] 未发现 FREESWITCH 类型的中继线路");
            return;
        }
        boolean expected = false;
        for (AiCallTrunk t : trunks)
        {
            if (StringUtils.isEmpty(t.getGatewayHost())) continue;
            expected = true;
            String key = t.getGatewayHost() + ":" + defaultEslPort;
            if (clients.containsKey(key)) continue; // 已连接
            try
            {
                FreeSwitchEslInboundClient client = new FreeSwitchEslInboundClient(
                        t.getGatewayHost(), defaultEslPort, defaultEslPassword);
                client.addListener(this);
                client.start();
                clients.put(key, client);
                log.info("[ESL-Bridge] 已连接 FreeSWITCH: {} (trunk={})", key, t.getTrunkCode());
            }
            catch (Exception e)
            {
                log.error("[ESL-Bridge] 连接 FreeSWITCH 失败: {}", key, e);
            }
        }
        connectionsExpected = expected;
    }

    /**
     * C4：单主失活检测——仅 leader、存在应消费线路时判定；全部连接失活持续
     * {@code unhealthyYieldSeconds} 后主动让位。包级可见供单测按时间驱动。
     *
     * @param nowMs 当前时间戳
     */
    void checkUnhealthyAndMaybeYield(long nowMs)
    {
        if (elector == null || !elector.isLeader() || !connectionsExpected)
        {
            unhealthySinceMs = null;
            return;
        }
        for (FreeSwitchEslInboundClient c : clients.values())
        {
            if (c.isConnected())
            {
                unhealthySinceMs = null;
                return;
            }
        }
        // 启动宽限：成为 leader 不足一个判定窗口不计时（建连/鉴权进行中）
        if (nowMs - grantedAtMs < unhealthyYieldSeconds * 1000L)
        {
            return;
        }
        if (unhealthySinceMs == null)
        {
            unhealthySinceMs = nowMs;
            log.warn("[ESL-Bridge] 全部 ESL 连接失活，{}s 内不恢复将主动让位", unhealthyYieldSeconds);
            return;
        }
        if (nowMs - unhealthySinceMs >= unhealthyYieldSeconds * 1000L)
        {
            unhealthySinceMs = null;
            elector.yieldLeadership("all ESL connections unhealthy beyond "
                    + unhealthyYieldSeconds + "s");
        }
    }

    /** C4：本实例是否为 ESL 事件消费者（非竞选模式下即自身消费） */
    public boolean isEslLeader()
    {
        return elector == null || elector.isLeader();
    }

    /**
     * C6：停机排空时主动让出 ESL 消费者角色（仅竞选模式且当前为 leader 时生效）。
     * 让位后存活实例 ≤10s 接管，后续入站呼叫事件由存活实例处理；
     * 让位时 onRevoked 回调断开本实例全部 ESL 连接，不会双主。
     */
    public void yieldForDrain()
    {
        if (elector != null && elector.isLeader())
        {
            elector.yieldLeadership("instance draining for shutdown");
            log.info("[ESL-Bridge] 排空开始，已主动让出 ESL 消费者角色");
        }
    }

    /** C4：当前在线（已鉴权+已订阅）的 ESL 连接数 */
    public int connectedCount()
    {
        int n = 0;
        for (FreeSwitchEslInboundClient c : clients.values())
        {
            if (c.isConnected())
            {
                n++;
            }
        }
        return n;
    }

    /**
     * 通过 ESL 在指定 FreeSWITCH 节点上挂断指定通道。
     *
     * @param host 网关主机；为空时使用任一已连接节点
     * @param uuid 通道 UUID
     * @return true 表示命令已下发（不代表对端已挂断）
     */
    public boolean hangupCall(String host, String uuid)
    {
        if (StringUtils.isEmpty(uuid))
        {
            return false;
        }
        FreeSwitchEslInboundClient client = pickClient(host);
        if (client == null)
        {
            log.warn("[ESL-Bridge] 无可用 ESL 连接，挂断失败 uuid={} host={}", uuid, host);
            return false;
        }
        String cmd = "api uuid_kill " + uuid;
        String resp = client.sendCommand(cmd);
        log.info("[ESL-Bridge] 挂断命令已下发 host={} uuid={} resp={}", client.getHost(), uuid, resp);
        return true;
    }

    /**
     * B4：汇总所有已连接 FreeSWITCH 节点的在途通道数（{@code api show channels count}），
     * 供 PBX/DB 对账。无可用连接或全部节点查询失败时返回 -1（调用方跳过本轮对账）。
     */
    public int queryPbxChannelCount()
    {
        int total = 0;
        boolean any = false;
        for (FreeSwitchEslInboundClient c : clients.values())
        {
            if (!c.isConnected())
            {
                continue;
            }
            try
            {
                String resp = c.sendCommand("api show channels count");
                int n = parseChannelCount(resp);
                if (n >= 0)
                {
                    total += n;
                    any = true;
                }
            }
            catch (Exception e)
            {
                log.warn("[ESL-Bridge] 查询通道数失败 host={} err={}", c.getHost(), e.getMessage());
            }
        }
        return any ? total : -1;
    }

    /**
     * 解析 {@code api show channels count} 响应中的通道总数（响应形如 "\n3 total.\n"）。
     * 包级静态便于单测；解析失败返回 -1。
     */
    static int parseChannelCount(String resp)
    {
        if (resp == null)
        {
            return -1;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s+total").matcher(resp);
        if (!m.find())
        {
            return -1;
        }
        try
        {
            return Integer.parseInt(m.group(1));
        }
        catch (NumberFormatException e)
        {
            return -1;
        }
    }

    private FreeSwitchEslInboundClient pickClient(String host)
    {
        if (clients.isEmpty())
        {
            return null;
        }
        if (StringUtils.isNotEmpty(host))
        {
            // 优先精确匹配 host:port
            FreeSwitchEslInboundClient c = clients.get(host + ":" + defaultEslPort);
            if (c != null) return c;
            // 退化到 host 前缀匹配
            for (Map.Entry<String, FreeSwitchEslInboundClient> e : clients.entrySet())
            {
                if (e.getKey().startsWith(host + ":")) return e.getValue();
            }
        }
        return clients.values().iterator().next();
    }

    // ---------------------------------------------------------------- 事件处理

    @Override
    public void onEvent(String host, EslEvent event)
    {
        String name = event.getEventName();
        String uuid = event.getCallUuid();
        if (name == null) return;

        // B4：事件幂等（第一道防线）。CHANNEL_HANGUP_COMPLETE 归一为 CHANNEL_HANGUP——
        // 一次挂断两个事件都会到达，双发会造成 finishCall 双释放并发；
        // DTMF 同一通话可合法重复（多次按键），不参与判重
        if (!"DTMF".equals(name) && idempotencyGuard != null)
        {
            String normalized = "CHANNEL_HANGUP_COMPLETE".equals(name) ? "CHANNEL_HANGUP" : name;
            if (!idempotencyGuard.firstSeen("ESL:" + host, uuid, normalized))
            {
                log.info("[ESL-Bridge] 重复事件已忽略: host={} event={} uuid={}", host, normalized, uuid);
                return;
            }
        }

        try
        {
            switch (name)
            {
                case "CHANNEL_CREATE":
                    onChannelCreate(host, event, uuid);
                    break;
                case "CHANNEL_ANSWER":
                    onChannelAnswer(host, event, uuid);
                    break;
                case "CHANNEL_BRIDGE":
                    onChannelBridge(host, event, uuid);
                    break;
                case "CHANNEL_HANGUP_COMPLETE":
                case "CHANNEL_HANGUP":
                    onChannelHangup(host, event, uuid, name);
                    break;
                case "DTMF":
                    onDtmf(host, event, uuid);
                    break;
                case "RECORD_STOP":
                    onRecordStop(host, event, uuid);
                    break;
                default:
                    break;
            }
        }
        catch (Exception e)
        {
            log.error("[ESL-Bridge] 处理事件异常: host={} event={} uuid={}", host, name, uuid, e);
        }
    }

    /** CHANNEL_CREATE：入站来话归一为 INBOUND 事件入总线；出站通道仅日志 */
    private void onChannelCreate(String host, EslEvent event, String uuid)
    {
        String direction = event.get("Call-Direction");
        String caller = event.get("Caller-Caller-ID-Number");
        String callee = event.get("Caller-Destination-Number");

        if ("inbound".equalsIgnoreCase(direction))
        {
            log.info("[ESL] 入站来话: uuid={} {} -> {}", uuid, caller, callee);
            CallEvent ce = CallEvent.of("ESL:" + host, CallEvent.INBOUND, uuid);
            ce.setChannel(uuid);
            ce.setCaller(caller);
            ce.setCallee(callee);
            Map<String, Object> payload = new HashMap<>();
            payload.put("rawEventName", "CHANNEL_CREATE");
            payload.put("direction", direction);
            ce.setPayload(payload);
            callEventBus.dispatch(ce);
        }
        else
        {
            log.debug("[ESL] 出站通道创建: uuid={} {} -> {}", uuid, caller, callee);
        }
    }

    /** CHANNEL_ANSWER：归一 ANSWERED 事件（指标/坐席推送/状态机统一在消费侧） */
    private void onChannelAnswer(String host, EslEvent event, String uuid)
    {
        log.info("[ESL] 通道接通: uuid={}", uuid);
        CallEvent ce = CallEvent.of("ESL:" + host, CallEvent.ANSWERED, uuid);
        ce.setChannel(uuid);
        ce.setCaller(event.get("Caller-Caller-ID-Number"));
        ce.setCallee(event.get("Caller-Destination-Number"));
        Map<String, Object> payload = new HashMap<>();
        payload.put("rawEventName", "CHANNEL_ANSWER");
        putIfNotEmpty(payload, "varRecordId", event.get("variable_ai_record_id"));
        ce.setPayload(payload);
        callEventBus.dispatch(ce);
    }

    /** CHANNEL_BRIDGE：归一 BRIDGED 事件（坐席推送） */
    private void onChannelBridge(String host, EslEvent event, String uuid)
    {
        log.info("[ESL] 通道桥接: uuid={}", uuid);
        CallEvent ce = CallEvent.of("ESL:" + host, CallEvent.BRIDGED, uuid);
        ce.setChannel(uuid);
        Map<String, Object> payload = new HashMap<>();
        payload.put("rawEventName", "CHANNEL_BRIDGE");
        putIfNotEmpty(payload, "varRecordId", event.get("variable_ai_record_id"));
        ce.setPayload(payload);
        callEventBus.dispatch(ce);
    }

    /**
     * CHANNEL_HANGUP[_COMPLETE]：归一 HANGUP 事件（挂断原因 + 通话时长）。
     * 入站正常挂断（NORMAL_CLEARING 且有通话时长）置 payload.autoTicket 旗标，
     * 由消费侧执行自动建工单——该旗标仅 ESL 侧产生，保持 AMI 侧无自动建工单的边界。
     */
    private void onChannelHangup(String host, EslEvent event, String uuid, String rawName)
    {
        String cause = event.get("Hangup-Cause");
        String billSec = event.get("variable_billsec");
        String duration = event.get("variable_duration");
        String direction = event.get("Call-Direction");
        log.info("[ESL] 通道挂断: uuid={} cause={} billsec={} direction={}", uuid, cause, billSec, direction);

        int talkDuration = 0;
        if (billSec != null)
        {
            try { talkDuration = Integer.parseInt(billSec); } catch (Exception ignored) {}
        }
        else if (duration != null)
        {
            try { talkDuration = Integer.parseInt(duration); } catch (Exception ignored) {}
        }

        CallEvent ce = CallEvent.of("ESL:" + host, CallEvent.HANGUP, uuid);
        ce.setChannel(uuid);
        ce.setCaller(event.get("Caller-Caller-ID-Number"));
        ce.setCallee(event.get("Caller-Destination-Number"));
        Map<String, Object> payload = new HashMap<>();
        payload.put("rawEventName", rawName);
        payload.put("hangupCause", cause);
        payload.put("talkDuration", talkDuration);
        payload.put("direction", direction);
        putIfNotEmpty(payload, "varRecordId", event.get("variable_ai_record_id"));
        // 入站通话正常结束且有通话时长时，自动创建工单（外呼已有 maybeCreateTicket 逻辑，不重复）
        if ("inbound".equalsIgnoreCase(direction)
                && "NORMAL_CLEARING".equalsIgnoreCase(cause)
                && talkDuration > 0)
        {
            payload.put("autoTicket", true);
        }
        ce.setPayload(payload);
        callEventBus.dispatch(ce);
    }

    /** DTMF：归一 DTMF 事件（payload.digit 透传按键） */
    private void onDtmf(String host, EslEvent event, String uuid)
    {
        String digit = event.get("DTMF-Digit");
        if (digit == null) digit = event.get("DTMF-Source");
        log.debug("[ESL] DTMF: uuid={} digit={}", uuid, digit);
        CallEvent ce = CallEvent.of("ESL:" + host, CallEvent.DTMF, uuid);
        ce.setChannel(uuid);
        Map<String, Object> payload = new HashMap<>();
        payload.put("rawEventName", "DTMF");
        putIfNotEmpty(payload, "digit", digit);
        putIfNotEmpty(payload, "varRecordId", event.get("variable_ai_record_id"));
        ce.setPayload(payload);
        callEventBus.dispatch(ce);
    }

    /**
     * RECORD_STOP：生产侧解析录音路径/时长与关联话单 recordId（多候选 UUID 反查），
     * 归一 RECORD_STOP 事件入总线；回写/上传/质检统一在消费侧执行。
     *
     * <p>事件字段兼容：</p>
     * <ul>
     *   <li>录音文件路径：{@code Record-File-Path}（标准字段），回退到
     *       {@code variable_record_path} / {@code record_path}；</li>
     *   <li>录音时长（秒）：{@code variable_record_seconds}，回退到
     *       {@code variable_recording_duration} / {@code record_seconds}；</li>
     *   <li>关联键：优先按事件 uuid（一般是 A-leg Unique-ID）查
     *       {@code call_uuid}，未命中时再尝试通过拨号日志的 recordId 关联。</li>
     * </ul>
     */
    private void onRecordStop(String host, EslEvent event, String uuid)
    {
        String recordPath = event.get("Record-File-Path");
        if (StringUtils.isEmpty(recordPath))
        {
            recordPath = event.get("variable_record_path");
        }
        if (StringUtils.isEmpty(recordPath))
        {
            recordPath = event.get("record_path");
        }
        if (StringUtils.isEmpty(recordPath))
        {
            recordPath = event.get("Record-File");
        }

        Integer recordSeconds = parseInteger(
                firstNonEmpty(event, "variable_record_seconds",
                        "variable_recording_duration", "record_seconds", "Record-Duration"));

        // RECORD_STOP 事件的 uuid 可能是录音腿 uuid，与话单建立时写入的 A-leg uuid 不一定相同，
        // 这里同时尝试 Other-Leg-Unique-ID / Channel-Call-UUID 作为候选
        String otherLeg = event.get("Other-Leg-Unique-ID");
        String callUuidHeader = event.get("Channel-Call-UUID");

        log.info("[ESL] 录音停止: uuid={} file={} seconds={} otherLeg={} callUuid={}",
                uuid, recordPath, recordSeconds, otherLeg, callUuidHeader);

        Long recordId = resolveRecordIdForCall(uuid, otherLeg, callUuidHeader, event);
        if (recordId == null)
        {
            log.warn("[ESL-Bridge] RECORD_STOP 未找到关联话单: uuid={} file={}", uuid, recordPath);
            return;
        }

        CallEvent ce = CallEvent.of("ESL:" + host, CallEvent.RECORD_STOP, uuid);
        ce.setChannel(uuid);
        ce.setLinkedid(otherLeg);
        ce.setRecordId(recordId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("rawEventName", "RECORD_STOP");
        putIfNotEmpty(payload, "recordPath", recordPath);
        if (recordSeconds != null)
        {
            payload.put("recordSeconds", recordSeconds);
        }
        ce.setPayload(payload);
        callEventBus.dispatch(ce);
    }

    /**
     * 在 RECORD_STOP 等事件中按多个候选 UUID 找到对应的话单主键。
     * 查找顺序：
     * <ol>
     *   <li>事件 uuid / Other-Leg-Unique-ID / Channel-Call-UUID 直接查 ai_call_record.call_uuid；</li>
     *   <li>通过 ai_call_dial_log.call_uuid 反查 recordId（外呼场景）；</li>
     *   <li>事件携带的 variable_ai_record_id 透传变量。</li>
     * </ol>
     */
    private Long resolveRecordIdForCall(String uuid, String otherLeg, String callUuidHeader, EslEvent event)
    {
        Long recordId = tryFindRecordIdByCallUuid(uuid);
        if (recordId == null && StringUtils.isNotEmpty(otherLeg))
        {
            recordId = tryFindRecordIdByCallUuid(otherLeg);
        }
        if (recordId == null && StringUtils.isNotEmpty(callUuidHeader))
        {
            recordId = tryFindRecordIdByCallUuid(callUuidHeader);
        }
        if (recordId == null && StringUtils.isNotEmpty(uuid))
        {
            try
            {
                AiCallDialLog dialLog = dialLogMapper.selectByCallUuid(uuid);
                if (dialLog != null && dialLog.getRecordId() != null)
                {
                    recordId = dialLog.getRecordId();
                }
            }
            catch (Exception ex)
            {
                log.debug("[ESL-Bridge] 按拨号日志反查 recordId 失败: uuid={} err={}", uuid, ex.getMessage());
            }
        }
        if (recordId == null)
        {
            String varRecordId = event.get("variable_ai_record_id");
            if (varRecordId != null && varRecordId.matches("\\d+"))
            {
                recordId = Long.parseLong(varRecordId);
            }
        }
        return recordId;
    }

    private Long tryFindRecordIdByCallUuid(String callUuid)
    {
        if (StringUtils.isEmpty(callUuid)) return null;
        try
        {
            AiCallRecord record = callRecordMapper.selectAiCallRecordByCallUuid(callUuid);
            return record == null ? null : record.getRecordId();
        }
        catch (Exception e)
        {
            log.debug("[ESL-Bridge] 按 callUuid={} 查话单失败: {}", callUuid, e.getMessage());
            return null;
        }
    }

    private String firstNonEmpty(EslEvent event, String... keys)
    {
        if (keys == null) return null;
        for (String k : keys)
        {
            String v = event.get(k);
            if (StringUtils.isNotEmpty(v)) return v;
        }
        return null;
    }

    private Integer parseInteger(String s)
    {
        if (StringUtils.isEmpty(s)) return null;
        try
        {
            // FreeSWITCH 有时返回 "12.34" 秒，截断小数
            int dot = s.indexOf('.');
            if (dot > 0) s = s.substring(0, dot);
            return Integer.parseInt(s.trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /** 值非空才放入 payload（保持事件载荷精简） */
    private static void putIfNotEmpty(Map<String, Object> payload, String key, String value)
    {
        if (StringUtils.isNotEmpty(value))
        {
            payload.put(key, value);
        }
    }
}
