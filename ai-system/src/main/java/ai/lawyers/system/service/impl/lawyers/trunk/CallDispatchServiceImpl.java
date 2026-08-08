package ai.lawyers.system.service.impl.lawyers.trunk;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.trunk.AiCallDialLog;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.domain.lawyers.trunk.AiNumberSegment;
import ai.lawyers.system.domain.lawyers.trunk.DialRequest;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.enums.CarrierEnum;
import ai.lawyers.system.enums.DialStatusEnum;
import ai.lawyers.system.enums.TrunkHealthEnum;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallTrunkMapper;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;
import ai.lawyers.system.service.lawyers.trunk.ICarrierRouteService;
import ai.lawyers.system.service.lawyers.trunk.ITrunkMonitorService;
import ai.lawyers.system.service.lawyers.trunk.gateway.CallGatewayFactory;
import ai.lawyers.system.service.lawyers.trunk.gateway.ICallGatewayAdapter;
import ai.lawyers.system.utils.trunk.NumberTransformUtils;

/**
 * 呼叫调度服务实现
 *
 * 并发控制分三层：
 *   1) 全局并发：AtomicInteger 上限 call.dispatch.maxGlobalConcurrent；
 *   2) 线路并发：数据库 UPDATE ... WHERE current_concurrent &lt; max_concurrent 原子占位，
 *      天然支持多实例部署；
 *   3) CPS 限速：线路级令牌桶，防止瞬时冲击导致运营商侧封堵。
 *
 * 故障切换：下发失败或线路探测失败时，把该线路加入本次呼叫的排除列表，
 * 重新选路重试，最多 maxFailover 次；连续失败达阈值触发线路熔断。
 */
@Service
public class CallDispatchServiceImpl implements ICallDispatchService
{
    private static final Logger log = LoggerFactory.getLogger(CallDispatchServiceImpl.class);

    /** 路由策略标识 */
    private static final String STRATEGY_CARRIER_MATCH = "CARRIER_MATCH";

    private static final String STRATEGY_ASSIGN = "ASSIGN_TRUNK";

    private static final String STRATEGY_FAILOVER = "FAILOVER";

    @Autowired
    private ICarrierRouteService carrierRouteService;

    @Autowired
    private CallGatewayFactory gatewayFactory;

    @Autowired
    private AiCallTrunkMapper trunkMapper;

    @Autowired
    private AiCallDialLogMapper dialLogMapper;

    @Autowired
    private ITrunkMonitorService trunkMonitorService;

    /** 全局最大并发呼叫数 */
    @Value("${call.dispatch.maxGlobalConcurrent:200}")
    private int maxGlobalConcurrent;

    /** 排队队列容量 */
    @Value("${call.dispatch.queueCapacity:2000}")
    private int queueCapacity;

    /** 排队最长等待时间(ms)，超时判定为排队失败 */
    @Value("${call.dispatch.queueTimeoutMs:60000}")
    private long queueTimeoutMs;

    /** 调度线程轮询间隔(ms) */
    @Value("${call.dispatch.scanIntervalMs:200}")
    private long scanIntervalMs;

    /** 是否启用排队调度线程 */
    @Value("${call.dispatch.queueEnabled:true}")
    private boolean queueEnabled;

    /** 全局并发计数 */
    private final AtomicInteger globalConcurrent = new AtomicInteger(0);

    /** 排队队列：按 priority 升序、入队时间升序 */
    private PriorityBlockingQueue<QueuedCall> callQueue;

    /** callUuid -> 已占用的线路ID，用于事件回调时释放并发 */
    private final Map<String, Long> callTrunkHolder = new ConcurrentHashMap<>();

    /** 线路CPS令牌桶：trunkId -> 桶 */
    private final Map<Long, CpsBucket> cpsBuckets = new ConcurrentHashMap<>();

    private volatile boolean running = true;

    private Thread dispatchThread;

    @PostConstruct
    public void init()
    {
        callQueue = new PriorityBlockingQueue<>(Math.max(16, Math.min(queueCapacity, 1024)));
        // 应用启动时重置线路并发计数，避免上次非正常退出留下的脏数据
        try
        {
            trunkMapper.resetAllConcurrent();
        }
        catch (Exception e)
        {
            log.warn("重置线路并发计数失败: {}", e.getMessage());
        }
        if (queueEnabled)
        {
            dispatchThread = new Thread(this::dispatchLoop, "call-dispatch-worker");
            dispatchThread.setDaemon(true);
            dispatchThread.start();
            log.info("呼叫排队调度线程已启动，队列容量={} 全局并发上限={}", queueCapacity, maxGlobalConcurrent);
        }
    }

    @PreDestroy
    public void destroy()
    {
        running = false;
        if (dispatchThread != null)
        {
            dispatchThread.interrupt();
        }
    }

    // ------------------------------------------------------------------ 对外接口

    @Override
    public DialResult dial(DialRequest request)
    {
        return doDial(request, 0L);
    }

    @Override
    public DialResult dialWithQueue(DialRequest request)
    {
        if (!queueEnabled)
        {
            return dial(request);
        }
        // 先尝试直接拨打，资源充足时不必排队
        if (globalConcurrent.get() < maxGlobalConcurrent)
        {
            DialResult direct = doDial(request, 0L);
            if (direct.isSuccess() || !"NO_AVAILABLE_TRUNK".equals(direct.getErrorCode()))
            {
                return direct;
            }
            // 仅当"无可用线路"（通常是并发满）时才排队
        }
        if (callQueue.size() >= queueCapacity)
        {
            log.warn("呼叫队列已满({}), 拒绝新请求 callee={}", queueCapacity,
                    NumberTransformUtils.mask(request.getCalleeNumber()));
            trunkMonitorService.raiseQueueOverflowAlarm(callQueue.size(), queueCapacity);
            return DialResult.fail("QUEUE_FULL", "呼叫队列已满，请稍后重试");
        }
        callQueue.offer(new QueuedCall(request));
        DialResult queued = DialResult.ok(null);
        queued.setDialStatus(DialStatusEnum.QUEUING.getCode());
        queued.setMessage("已进入呼叫队列，当前排队 " + callQueue.size() + " 个");
        return queued;
    }

    @Override
    public boolean hangup(String callUuid)
    {
        Long trunkId = callTrunkHolder.get(callUuid);
        if (trunkId == null)
        {
            AiCallDialLog dialLog = dialLogMapper.selectByCallUuid(callUuid);
            trunkId = dialLog == null ? null : dialLog.getTrunkId();
        }
        if (trunkId == null)
        {
            log.warn("挂断失败，未找到呼叫 uuid={}", callUuid);
            return false;
        }
        AiCallTrunk trunk = trunkMapper.selectAiCallTrunkByTrunkId(trunkId);
        if (trunk == null)
        {
            return false;
        }
        ICallGatewayAdapter adapter = gatewayFactory.get(trunk);
        boolean ok = adapter != null && adapter.hangup(trunk, callUuid);
        // 无论网关是否成功，都要释放本地资源，避免并发泄漏
        releaseCall(callUuid, trunkId);
        return ok;
    }

    @Override
    public void onCallEvent(String callUuid, String eventType, Map<String, Object> params)
    {
        if (callUuid == null || callUuid.isEmpty())
        {
            return;
        }
        AiCallDialLog dialLog = dialLogMapper.selectByCallUuid(callUuid);
        if (dialLog == null)
        {
            log.warn("收到未知呼叫事件 uuid={} event={}", callUuid, eventType);
            return;
        }

        AiCallDialLog update = new AiCallDialLog();
        update.setLogId(dialLog.getLogId());
        Date now = new Date();

        switch (eventType == null ? "" : eventType.toUpperCase())
        {
            case "RINGING":
                update.setDialStatus(DialStatusEnum.RINGING.getCode());
                update.setRingTime(now);
                dialLogMapper.updateAiCallDialLog(update);
                break;

            case "ANSWERED":
                update.setDialStatus(DialStatusEnum.ANSWERED.getCode());
                update.setAnswerTime(now);
                update.setIsConnected("1");
                if (dialLog.getRingTime() != null)
                {
                    update.setRingDuration(diffSeconds(dialLog.getRingTime(), now));
                }
                dialLogMapper.updateAiCallDialLog(update);
                break;

            case "HANGUP":
            case "FAILED":
                fillHangupInfo(update, dialLog, params, now, eventType);
                dialLogMapper.updateAiCallDialLog(update);
                finishCall(callUuid, dialLog, update);
                break;

            default:
                log.debug("忽略未处理的呼叫事件 uuid={} event={}", callUuid, eventType);
        }
    }

    @Override
    public int getQueueSize()
    {
        return callQueue == null ? 0 : callQueue.size();
    }

    @Override
    public int getGlobalConcurrent()
    {
        return globalConcurrent.get();
    }

    // ------------------------------------------------------------------ 核心拨号

    /**
     * 执行一次外呼，内部完成选路、占位、下发与故障切换。
     *
     * @param queueWaitMs 排队等待时长，直呼为 0
     */
    private DialResult doDial(DialRequest request, long queueWaitMs)
    {
        // 1. 号码校验
        if (!NumberTransformUtils.isValid(request.getCalleeNumber()))
        {
            return DialResult.fail("INVALID_NUMBER", "被叫号码不合法: " + request.getCalleeNumber());
        }

        // 2. 全局并发控制
        if (globalConcurrent.get() >= maxGlobalConcurrent)
        {
            return DialResult.fail("NO_AVAILABLE_TRUNK",
                    "已达全局并发上限 " + maxGlobalConcurrent);
        }

        // 3. 运营商识别
        AiNumberSegment segment = carrierRouteService.recognizeSegment(request.getCalleeNumber());
        String calleeCarrier = segment != null && segment.getCarrier() != null
                ? segment.getCarrier() : CarrierEnum.UNKNOWN.getCode();
        String targetCarrier = request.getAssignCarrier() != null && !request.getAssignCarrier().isEmpty()
                ? request.getAssignCarrier() : calleeCarrier;

        // 4. 构建候选线路
        List<AiCallTrunk> candidates = buildCandidates(request, targetCarrier);
        if (candidates.isEmpty())
        {
            log.warn("无可用线路 callee={} carrier={}",
                    NumberTransformUtils.mask(request.getCalleeNumber()), targetCarrier);
            trunkMonitorService.raiseNoTrunkAlarm(targetCarrier);
            return DialResult.fail("NO_AVAILABLE_TRUNK",
                    "运营商 " + CarrierEnum.infoOf(targetCarrier) + " 无可用线路");
        }

        // 5. 依次尝试候选线路（故障自动切换）
        int maxFailover = request.getMaxFailover() == null ? 2 : request.getMaxFailover();
        int maxAttempts = request.isAllowFailover() ? Math.min(candidates.size(), maxFailover + 1) : 1;

        List<String> triedTrunks = new ArrayList<>();
        DialResult lastFail = null;

        for (int attempt = 0; attempt < maxAttempts; attempt++)
        {
            AiCallTrunk trunk = candidates.get(attempt);
            triedTrunks.add(trunk.getTrunkCode());

            // 5.1 CPS 限速
            if (!acquireCps(trunk))
            {
                log.debug("线路 {} 触发 CPS 限速，尝试下一条", trunk.getTrunkCode());
                lastFail = DialResult.fail("CPS_LIMIT", "线路 " + trunk.getTrunkCode() + " 达到CPS上限");
                continue;
            }

            // 5.2 线路并发原子占位
            if (trunkMapper.tryAcquireConcurrent(trunk.getTrunkId()) <= 0)
            {
                log.debug("线路 {} 并发已满，尝试下一条", trunk.getTrunkCode());
                lastFail = DialResult.fail("TRUNK_BUSY", "线路 " + trunk.getTrunkCode() + " 并发已满");
                continue;
            }
            globalConcurrent.incrementAndGet();

            // 5.3 落库拨号日志（先记录，保证任何结果都有痕迹）
            AiCallDialLog dialLog = buildDialLog(request, trunk, segment, calleeCarrier,
                    attempt, triedTrunks, queueWaitMs);
            dialLogMapper.insertAiCallDialLog(dialLog);

            // 5.4 下发网关
            ICallGatewayAdapter adapter = gatewayFactory.get(trunk);
            if (adapter == null)
            {
                releaseConcurrent(trunk.getTrunkId());
                markLogFailed(dialLog, "NO_ADAPTER", "未找到网关适配器 vendor=" + trunk.getVendor());
                lastFail = DialResult.fail("NO_ADAPTER", "未找到网关适配器");
                continue;
            }

            DialResult result;
            try
            {
                result = adapter.originate(trunk, request);
            }
            catch (Exception e)
            {
                log.error("线路 {} 下发异常", trunk.getTrunkCode(), e);
                result = DialResult.fail("GATEWAY_ERROR", e.getMessage());
                result.setDialStatus(DialStatusEnum.FAILED.getCode());
            }

            if (result.isSuccess())
            {
                // 成功：把网关返回的真实 callUuid 回写到日志
                dialLog.setCallUuid(result.getCallUuid());
                dialLogMapper.updateCallUuid(dialLog.getLogId(), result.getCallUuid());

                callTrunkHolder.put(result.getCallUuid(), trunk.getTrunkId());

                result.setLogId(dialLog.getLogId());
                result.setTrunkId(trunk.getTrunkId());
                result.setTrunkCode(trunk.getTrunkCode());
                result.setTrunkCarrier(trunk.getCarrier());
                result.setCalleeCarrier(calleeCarrier);
                result.setFailoverCount(attempt);
                result.setFailoverTrunks(String.join(">", triedTrunks));
                result.setQueueWaitMs(queueWaitMs);
                result.setRouteStrategy(dialLog.getRouteStrategy());

                log.info("外呼下发成功 callee={} carrier={} trunk={} 切换{}次 排队{}ms uuid={}",
                        NumberTransformUtils.mask(request.getCalleeNumber()),
                        CarrierEnum.infoOf(calleeCarrier), trunk.getTrunkCode(),
                        attempt, queueWaitMs, result.getCallUuid());
                return result;
            }

            // 失败：释放资源、标记日志、判定是否熔断，然后切换下一条线路
            releaseConcurrent(trunk.getTrunkId());
            markLogFailed(dialLog, result.getErrorCode(), result.getMessage());
            trunkMapper.markCallFail(trunk.getTrunkId());
            trunkMonitorService.onCallFailed(trunk);

            lastFail = result;
            lastFail.setTrunkId(trunk.getTrunkId());
            lastFail.setTrunkCode(trunk.getTrunkCode());

            log.warn("线路 {} 外呼失败({}), 准备故障切换 [{}/{}]",
                    trunk.getTrunkCode(), result.getMessage(), attempt + 1, maxAttempts);
        }

        // 全部尝试失败
        if (lastFail == null)
        {
            lastFail = DialResult.fail("DIAL_FAILED", "所有候选线路均不可用");
        }
        lastFail.setCalleeCarrier(calleeCarrier);
        lastFail.setFailoverCount(Math.max(0, triedTrunks.size() - 1));
        lastFail.setFailoverTrunks(String.join(">", triedTrunks));
        lastFail.setQueueWaitMs(queueWaitMs);
        return lastFail;
    }

    /**
     * 构建候选线路：优先使用强制指定线路，否则按运营商选路。
     */
    private List<AiCallTrunk> buildCandidates(DialRequest request, String targetCarrier)
    {
        List<AiCallTrunk> candidates = new ArrayList<>();

        if (request.getAssignTrunkCode() != null && !request.getAssignTrunkCode().isEmpty())
        {
            AiCallTrunk assigned = carrierRouteService.getTrunkByCode(request.getAssignTrunkCode());
            if (assigned != null && "1".equals(assigned.getEnableFlag())
                    && TrunkHealthEnum.isSelectable(assigned.getHealthStatus()))
            {
                candidates.add(assigned);
            }
            else
            {
                log.warn("指定线路 {} 不可用，回退自动选路", request.getAssignTrunkCode());
            }
        }

        List<AiCallTrunk> auto = carrierRouteService.selectCandidateTrunks(targetCarrier, null);
        for (AiCallTrunk trunk : auto)
        {
            boolean duplicated = false;
            for (AiCallTrunk exist : candidates)
            {
                if (exist.getTrunkId().equals(trunk.getTrunkId()))
                {
                    duplicated = true;
                    break;
                }
            }
            if (!duplicated)
            {
                candidates.add(trunk);
            }
        }
        return candidates;
    }

    private AiCallDialLog buildDialLog(DialRequest request, AiCallTrunk trunk, AiNumberSegment segment,
                                       String calleeCarrier, int attempt, List<String> triedTrunks,
                                       long queueWaitMs)
    {
        AiCallDialLog dialLog = new AiCallDialLog();
        // 下发成功前先用临时占位，成功后回写真实 callUuid
        dialLog.setCallUuid("PENDING-" + System.nanoTime() + "-" + trunk.getTrunkId());
        dialLog.setRecordId(request.getRecordId());
        dialLog.setTaskId(request.getTaskId());
        dialLog.setAgentId(request.getAgentId());
        dialLog.setCallerNumber(NumberTransformUtils.resolveCaller(request.getCallerNumber(), trunk));
        dialLog.setCalleeNumber(NumberTransformUtils.normalize(request.getCalleeNumber()));
        dialLog.setCalleeCarrier(calleeCarrier);
        if (segment != null)
        {
            dialLog.setCalleeProvince(segment.getProvince());
            dialLog.setCalleeCity(segment.getCity());
        }
        dialLog.setTrunkId(trunk.getTrunkId());
        dialLog.setTrunkCode(trunk.getTrunkCode());
        dialLog.setTrunkCarrier(trunk.getCarrier());
        dialLog.setLineType(trunk.getLineType());
        dialLog.setRouteStrategy(resolveStrategy(request, attempt));
        dialLog.setFailoverCount(attempt);
        dialLog.setFailoverTrunks(String.join(">", triedTrunks));
        dialLog.setQueueWaitMs(queueWaitMs);
        dialLog.setDialStatus(DialStatusEnum.DIALING.getCode());
        dialLog.setIsConnected("0");
        dialLog.setDialTime(new Date());
        dialLog.setCreateBy(request.getCreateBy());
        dialLog.setRemark(request.getRemark());
        return dialLog;
    }

    private String resolveStrategy(DialRequest request, int attempt)
    {
        if (attempt > 0)
        {
            return STRATEGY_FAILOVER;
        }
        if (request.getAssignTrunkCode() != null && !request.getAssignTrunkCode().isEmpty())
        {
            return STRATEGY_ASSIGN;
        }
        return STRATEGY_CARRIER_MATCH;
    }

    private void markLogFailed(AiCallDialLog dialLog, String errorCode, String message)
    {
        AiCallDialLog upd = new AiCallDialLog();
        upd.setLogId(dialLog.getLogId());
        upd.setDialStatus(DialStatusEnum.FAILED.getCode());
        upd.setIsConnected("0");
        upd.setFailReason(truncate(errorCode + ":" + message, 250));
        upd.setHangupTime(new Date());
        dialLogMapper.updateAiCallDialLog(upd);
    }

    private void fillHangupInfo(AiCallDialLog update, AiCallDialLog dialLog,
                                Map<String, Object> params, Date now, String eventType)
    {
        update.setHangupTime(now);

        boolean connected = dialLog.getAnswerTime() != null || "1".equals(dialLog.getIsConnected());
        if ("FAILED".equalsIgnoreCase(eventType) || !connected)
        {
            String status = getString(params, "dialStatus");
            update.setDialStatus(status != null ? status : DialStatusEnum.FAILED.getCode());
            update.setIsConnected("0");
        }
        else
        {
            update.setDialStatus(DialStatusEnum.HANGUP.getCode());
            update.setIsConnected("1");
        }

        update.setHangupCause(getString(params, "hangupCause"));
        update.setFailReason(truncate(getString(params, "failReason"), 250));
        Integer sipCode = getInt(params, "sipCode");
        if (sipCode != null)
        {
            update.setSipCode(sipCode);
        }

        Integer talk = getInt(params, "talkDuration");
        if (talk == null && dialLog.getAnswerTime() != null)
        {
            talk = diffSeconds(dialLog.getAnswerTime(), now);
        }
        update.setTalkDuration(talk == null ? 0 : talk);
        update.setBillDuration(update.getTalkDuration());

        if (dialLog.getDialTime() != null)
        {
            update.setTotalDuration(diffSeconds(dialLog.getDialTime(), now));
        }
        if (dialLog.getRingTime() != null && dialLog.getAnswerTime() != null)
        {
            update.setRingDuration(diffSeconds(dialLog.getRingTime(), dialLog.getAnswerTime()));
        }

        Object mos = params == null ? null : params.get("mos");
        if (mos != null)
        {
            update.setMos(new BigDecimal(mos.toString()));
        }
        Object loss = params == null ? null : params.get("packetLoss");
        if (loss != null)
        {
            update.setPacketLoss(new BigDecimal(loss.toString()));
        }
        Integer jitter = getInt(params, "jitter");
        if (jitter != null)
        {
            update.setJitter(jitter);
        }
        Integer rtt = getInt(params, "rtt");
        if (rtt != null)
        {
            update.setRtt(rtt);
        }
        String recordFile = getString(params, "recordFile");
        if (recordFile != null)
        {
            update.setRecordFile(recordFile);
        }
    }

    /**
     * 呼叫结束：释放并发、更新线路累计指标、驱动健康状态。
     */
    private void finishCall(String callUuid, AiCallDialLog dialLog, AiCallDialLog update)
    {
        Long trunkId = dialLog.getTrunkId();
        releaseCall(callUuid, trunkId);
        if (trunkId == null)
        {
            return;
        }
        AiCallTrunk trunk = trunkMapper.selectAiCallTrunkByTrunkId(trunkId);
        if ("1".equals(update.getIsConnected()))
        {
            trunkMapper.markCallSuccess(trunkId, update.getTalkDuration());
            if (trunk != null)
            {
                trunkMonitorService.onCallSuccess(trunk);
            }
        }
        else
        {
            trunkMapper.markCallFail(trunkId);
            if (trunk != null)
            {
                trunkMonitorService.onCallFailed(trunk);
            }
        }
    }

    private void releaseCall(String callUuid, Long trunkId)
    {
        if (callUuid != null)
        {
            callTrunkHolder.remove(callUuid);
        }
        if (trunkId != null)
        {
            releaseConcurrent(trunkId);
        }
    }

    private void releaseConcurrent(Long trunkId)
    {
        try
        {
            trunkMapper.releaseConcurrent(trunkId);
        }
        finally
        {
            if (globalConcurrent.get() > 0)
            {
                globalConcurrent.decrementAndGet();
            }
        }
    }

    // ------------------------------------------------------------------ 排队调度

    private void dispatchLoop()
    {
        while (running)
        {
            try
            {
                QueuedCall queued = callQueue.poll(scanIntervalMs, TimeUnit.MILLISECONDS);
                if (queued == null)
                {
                    continue;
                }
                long waited = System.currentTimeMillis() - queued.enqueueTime;
                if (waited > queueTimeoutMs)
                {
                    log.warn("呼叫排队超时被丢弃 callee={} 等待{}ms",
                            NumberTransformUtils.mask(queued.request.getCalleeNumber()), waited);
                    continue;
                }
                if (globalConcurrent.get() >= maxGlobalConcurrent)
                {
                    // 资源仍紧张，放回队列稍后再试
                    callQueue.offer(queued);
                    Thread.sleep(scanIntervalMs);
                    continue;
                }
                DialResult result = doDial(queued.request, waited);
                if (!result.isSuccess() && "NO_AVAILABLE_TRUNK".equals(result.getErrorCode()))
                {
                    callQueue.offer(queued);
                    Thread.sleep(scanIntervalMs);
                }
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                break;
            }
            catch (Exception e)
            {
                log.error("呼叫调度线程异常", e);
            }
        }
        log.info("呼叫排队调度线程已退出");
    }

    // ------------------------------------------------------------------ CPS 限速

    private boolean acquireCps(AiCallTrunk trunk)
    {
        int limit = trunk.getCpsLimit() == null ? 0 : trunk.getCpsLimit();
        if (limit <= 0)
        {
            return true;
        }
        CpsBucket bucket = cpsBuckets.computeIfAbsent(trunk.getTrunkId(), k -> new CpsBucket());
        return bucket.tryAcquire(limit);
    }

    /** 秒级滑动令牌桶 */
    private static class CpsBucket
    {
        private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis() / 1000);

        private final AtomicInteger counter = new AtomicInteger(0);

        boolean tryAcquire(int limit)
        {
            long nowSec = System.currentTimeMillis() / 1000;
            long start = windowStart.get();
            if (nowSec != start && windowStart.compareAndSet(start, nowSec))
            {
                counter.set(0);
            }
            return counter.incrementAndGet() <= limit;
        }
    }

    /** 排队中的呼叫 */
    private static class QueuedCall implements Comparable<QueuedCall>
    {
        final DialRequest request;

        final long enqueueTime;

        QueuedCall(DialRequest request)
        {
            this.request = request;
            this.enqueueTime = System.currentTimeMillis();
        }

        @Override
        public int compareTo(QueuedCall other)
        {
            int p1 = request.getPriority() == null ? 100 : request.getPriority();
            int p2 = other.request.getPriority() == null ? 100 : other.request.getPriority();
            if (p1 != p2)
            {
                return Integer.compare(p1, p2);
            }
            return Long.compare(this.enqueueTime, other.enqueueTime);
        }
    }

    // ------------------------------------------------------------------ 工具

    private int diffSeconds(Date from, Date to)
    {
        if (from == null || to == null)
        {
            return 0;
        }
        long diff = (to.getTime() - from.getTime()) / 1000;
        return diff < 0 ? 0 : (int) diff;
    }

    private String getString(Map<String, Object> params, String key)
    {
        if (params == null)
        {
            return null;
        }
        Object v = params.get(key);
        return v == null ? null : v.toString();
    }

    private Integer getInt(Map<String, Object> params, String key)
    {
        if (params == null)
        {
            return null;
        }
        Object v = params.get(key);
        if (v == null)
        {
            return null;
        }
        try
        {
            return Integer.valueOf(v.toString());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private String truncate(String s, int max)
    {
        if (s == null)
        {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
