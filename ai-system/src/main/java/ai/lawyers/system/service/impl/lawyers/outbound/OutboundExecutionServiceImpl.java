package ai.lawyers.system.service.impl.lawyers.outbound;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiHotspotSuppress;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundCallee;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundResult;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundTask;
import ai.lawyers.system.domain.lawyers.trunk.AiCallDialLog;
import ai.lawyers.system.domain.lawyers.trunk.DialRequest;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundCalleeMapper;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundTaskMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.service.ISysUserService;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.IAiHotspotSuppressService;
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundResultService;
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundTaskService;
import ai.lawyers.system.service.lawyers.outbound.IOutboundExecutionService;
import ai.lawyers.system.service.lawyers.queue.CallEventDispatcher;
import ai.lawyers.system.service.lawyers.queue.MessageNotifyDispatcher;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;
import ai.lawyers.system.service.lawyers.ivr.engine.IIvrEngineService;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;

/**
 * 智能外呼执行引擎实现。
 *
 * 双模式：
 *  - 模拟模式（call.outbound.simulateAnswer=true，默认）：下发成功后直接模拟接通，
 *    进入 IVR 流程并生成话单/结果，便于无语音网关环境联调；
 *  - 网关模式：下发后保持"呼叫中"，由 FreeSWITCH/Asterisk 事件回调最终化。
 *
 * 重试策略：可重试失败（无可用线路/线路忙/网关错误等）在 retryCount 内自动回拨，
 * 重试次数用尽或永久失败后计入失败统计。
 */
@Service
public class OutboundExecutionServiceImpl implements IOutboundExecutionService
{
    private static final Logger log = LoggerFactory.getLogger(OutboundExecutionServiceImpl.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private IAiOutboundTaskService taskService;

    @Autowired
    private IAiOutboundResultService resultService;

    @Autowired
    private IAiCallRecordService callRecordService;

    @Autowired
    private IAiCallTicketService callTicketService;

    @Autowired
    private ICallDispatchService callDispatchService;

    @Autowired
    private IIvrEngineService ivrEngineService;

    @Autowired
    private AiOutboundTaskMapper taskMapper;

    @Autowired
    private AiOutboundCalleeMapper calleeMapper;

    @Autowired
    private AiCallDialLogMapper dialLogMapper;

    @Autowired
    private Environment environment;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** T5-1：外呼指标（未引入 micrometer 时内部静默） */
    @Autowired(required = false)
    private HotlineMetrics metrics;

    /** T5-3 消息中心：任务完成站内信投递（队列不可用时内部降级，不影响外呼主流程） */
    @Autowired(required = false)
    private MessageNotifyDispatcher messageNotifyDispatcher;

    @Autowired
    private CallEventDispatcher callEventDispatcher;

    @Autowired(required = false)
    private ai.lawyers.system.service.lawyers.compliance.ComplianceGuard complianceGuard;

    /** P3-D1 高频置底：外呼拨号前号码规则判定（REJECT 跳过 / PRIORITY 沉底） */
    @Autowired
    private IAiHotspotSuppressService hotspotSuppressService;

    /** 高频置底总开关（与入站共用） */
    @Value("${hotspot.suppress.enabled:true}")
    private boolean hotspotEnabled;

    /**
     * 外呼置底沉底偏移量：外呼内存队列按 priority 升序（越小越优先），与入站 ACD desc 相反，
     * 沉底=抬高数值；加固定偏移而非取极大值，保持同批被压制号码间仍按任务原优先级有序。
     */
    private static final int HOTSPOT_SINK_OFFSET = 10000;

    /** createBy（登录名）反查 userId，用于确定站内信接收人 */
    @Autowired
    private ISysUserService userService;

    /** 多步写库（号码状态 + 结果 + 任务计数）统一在此事务模板内提交，保证一致性 */
    private TransactionTemplate transactionTemplate;

    /** 模拟接通：无真实网关时直接完成全链路 */
    @Value("${call.outbound.simulateAnswer:true}")
    private boolean simulateAnswer;

    /** 模拟接通后的通话时长（秒），仅模拟模式使用 */
    @Value("${call.outbound.simulateTalkSeconds:45}")
    private int simulateTalkSeconds;

    /** 是否启用定时扫描执行 */
    @Value("${call.outbound.scanEnabled:true}")
    private boolean scanEnabled;

    /** 回访/转人工类外呼接通后自动创建工单（融合点：外呼可触发工单创建） */
    @Value("${call.outbound.autoCreateTicket:false}")
    private boolean autoCreateTicket;

    /** T2-2 单任务并发拨号线程数（=1 时退化为串行，兼容旧行为） */
    @Value("${call.outbound.dial.concurrent:8}")
    private int dialConcurrent;

    /** T2-2 扫描分布式锁持有时长（秒），保证多实例全局只一个实例扫描 */
    @Value("${call.outbound.scan.lockSeconds:30}")
    private int scanLockSeconds;

    /** T2-2 失败重试退避基数（毫秒），按重试次数指数增长：base * 2^(retryTimes-1)，上限 10 分钟 */
    @Value("${call.outbound.retry.backoffBaseMs:15000}")
    private long retryBackoffBaseMs;

    /** T1-6 对账：呼叫中(1)卡住超过该分钟数视为网关事件丢失，置失败/待重试 */
    @Value("${call.outbound.reconcile.staleMinutes:10}")
    private int staleMinutes;

    private final AtomicBoolean scanning = new AtomicBoolean(false);

    /** T2-2 并发拨号线程池（有界，daemon） */
    private ExecutorService dialPool;

    /** N7：扫描/对账任务的集群单主锁（Lua CAS 释放，替代原裸 delete 锁） */
    private static final String SCAN_LOCK_NAME = "job:outbound-scan";
    private static final String RECONCILE_LOCK_NAME = "job:outbound-reconcile";
    private static final Duration RECONCILE_LOCK_TTL = Duration.ofMinutes(10);

    @Autowired
    private RedisLeaderLock leaderLock;

    /**
     * R11 防护：模拟接通开关仅允许在开发/测试环境开启。
     * 生产环境若误开，将直接产生假话单/假接通统计，启动时强告警以便第一时间发现配置错误。
     */
    @PostConstruct
    public void checkSimulateSwitch()
    {
        transactionTemplate = new TransactionTemplate(transactionManager);
        // T2-2 并发拨号线程池：按配置并发数创建有界固定池，daemon 线程不阻止 JVM 退出
        int poolSize = Math.max(1, Math.min(dialConcurrent, 64));
        final AtomicInteger threadSeq = new AtomicInteger(1);
        dialPool = Executors.newFixedThreadPool(poolSize, new ThreadFactory()
        {
            @Override
            public Thread newThread(Runnable r)
            {
                Thread t = new Thread(r, "outbound-dial-" + threadSeq.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });
        if (!simulateAnswer)
        {
            return;
        }
        String[] activeProfiles = environment.getActiveProfiles();
        boolean prodLike = false;
        for (String profile : activeProfiles)
        {
            String p = profile == null ? "" : profile.toLowerCase();
            if (p.contains("prod") || p.contains("pro") || p.contains("release"))
            {
                prodLike = true;
                break;
            }
        }
        if (prodLike)
        {
            log.error("========== 高风险配置告警 ==========");
            log.error("当前为生产环境但 call.outbound.simulateAnswer=true，外呼将被模拟接通并生成虚假话单/统计！");
            log.error("请立即设置环境变量 CALL_OUTBOUND_SIMULATEANSWER=false 或关闭配置后重启！");
            log.error("====================================");
        }
        else
        {
            log.warn("外呼模拟接通已开启(simulateAnswer=true)，仅可用于开发/测试联调，生产环境必须关闭。");
        }
    }

    @Override
    public int executeTask(Long taskId)
    {
        AiOutboundTask task = taskService.selectAiOutboundTaskByTaskId(taskId);
        if (task == null)
        {
            log.warn("外呼任务不存在 taskId={}", taskId);
            return 0;
        }
        if (!"1".equals(task.getStatus()))
        {
            log.debug("外呼任务不在执行中状态 taskId={} status={}", taskId, task.getStatus());
            return 0;
        }

        int batchSize = task.getMaxConcurrent() == null || task.getMaxConcurrent() <= 0
                ? 10 : Math.min(task.getMaxConcurrent(), 100);
        // T2-2：只捞取已到重试窗口的待呼叫号码（含退避），未到 next_retry_time 的本轮跳过
        List<AiOutboundCallee> pending = calleeMapper.selectDialableCallees(taskId, batchSize);
        if (pending.isEmpty())
        {
            checkTaskCompletion(taskId);
            return 0;
        }

        // T2-2 并发拨号：号码级原子领取（claimCallee）+ 有界线程池并发下发；dialConcurrent=1 时串行兼容
        List<Runnable> jobs = new java.util.ArrayList<>();
        for (AiOutboundCallee callee : pending)
        {
            jobs.add(() -> {
                // C3：号码级原子领取，多实例/多线程并发只有一个领取成功，影响行数=0 直接跳过
                int claimed = calleeMapper.claimCallee(callee.getCalleeId(), "outbound-executor");
                if (claimed <= 0)
                {
                    log.debug("号码已被其他实例/线程领取，跳过 taskId={} calleeId={}", taskId, callee.getCalleeId());
                    return;
                }
                callee.setCallStatus("1");
                callee.setCallTime(new Date());
                try
                {
                    dialCallee(task, callee);
                }
                catch (Exception e)
                {
                    log.error("外呼号码执行异常 taskId={} calleeId={}", taskId, callee.getCalleeId(), e);
                    markCalleeFailed(task, callee, "执行异常：" + e.getMessage());
                }
            });
        }
        if (dialConcurrent > 1 && jobs.size() > 1)
        {
            try
            {
                List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
                for (Runnable r : jobs)
                {
                    // T5-1：拨号任务在线程池执行，透传调度线程 MDC（traceId）
                    futures.add(dialPool.submit(ai.lawyers.common.utils.MdcUtils.wrap(r)));
                }
                for (java.util.concurrent.Future<?> f : futures)
                {
                    try { f.get(120, TimeUnit.SECONDS); }
                    catch (Exception e) { log.warn("外呼并发任务等待异常: {}", e.getMessage()); }
                }
            }
            catch (Exception e)
            {
                log.warn("并发拨号降级为串行: {}", e.getMessage());
                for (Runnable r : jobs) { r.run(); }
            }
        }
        else
        {
            for (Runnable r : jobs) { r.run(); }
        }
        checkTaskCompletion(taskId);
        return pending.size();
    }

    @Override
    @Scheduled(fixedDelayString = "${call.outbound.scanIntervalMs:15000}")
    public void scanRunningTasks()
    {
        if (!scanEnabled)
        {
            return;
        }
        // N7：统一走 RedisLeaderLock 单次单主锁（Lua CAS 释放，修复旧实现裸 delete 误删风险；
        // Redis 异常时跳过本轮防多实例重复拨号，cluster.lock.enabled=false 可回退旧行为）
        leaderLock.tryRun(SCAN_LOCK_NAME, Duration.ofSeconds(scanLockSeconds), this::doScanRunningTasks);
    }

    private void doScanRunningTasks()
    {
        if (!scanning.compareAndSet(false, true))
        {
            return;
        }
        try
        {
            // W4：非允许外呼时段，整批跳过不拨号（下轮扫描再判断）
            if (complianceGuard != null && !complianceGuard.isCallingAllowed())
            {
                log.info("当前为非外呼服务时段，本轮扫描跳过");
                return;
            }
            List<AiOutboundTask> running = taskMapper.selectRunningTasks();
            int scanned = 0;
            for (AiOutboundTask task : running)
            {
                if (scanned >= 50)
                {
                    break;
                }
                executeTask(task.getTaskId());
                scanned++;
            }
        }
        catch (Exception e)
        {
            log.error("外呼任务定时扫描异常", e);
        }
        finally
        {
            scanning.set(false);
        }
    }

    /**
     * T1-6 对账任务（每 5 分钟）：
     * 1) 扫描长时间卡在"呼叫中(1)"的号码（网关事件丢失兜底），按是否已生成话单判定失败/待重试；
     * 2) 校正进行中任务状态，避免计数漂移导致任务永久卡在"执行中"。
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void reconcileStaleCalls()
    {
        if (!scanEnabled)
        {
            return;
        }
        // N7：对账兜底全组仅一个实例执行，避免重复重试/重复置失败
        leaderLock.tryRun(RECONCILE_LOCK_NAME, RECONCILE_LOCK_TTL, this::doReconcileStaleCalls);
    }

    private void doReconcileStaleCalls()
    {
        try
        {
            List<AiOutboundCallee> stale = calleeMapper.selectStaleCallingCallees(staleMinutes);
            if (stale == null || stale.isEmpty())
            {
                return;
            }
            log.warn("外呼对账：发现{}个号码卡在呼叫中超过{}分钟，进行兜底处理", stale.size(), staleMinutes);
            for (AiOutboundCallee callee : stale)
            {
                try
                {
                    AiOutboundTask task = taskService.selectAiOutboundTaskByTaskId(callee.getTaskId());
                    if (task == null)
                    {
                        continue;
                    }
                    // 已有话单（曾接通）按已完成收尾，否则按失败计入，保证任务可终态
                    if (callee.getRecordId() != null)
                    {
                        finalizeCallee(task.getTaskId(), callee, true, 0, "RECONCILE");
                    }
                    else
                    {
                        int retryTimes = callee.getRetryTimes() == null ? 0 : callee.getRetryTimes();
                        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
                        if (retryTimes < retryCount)
                        {
                            // 退回待呼叫并设退避窗口，下一轮扫描重新外呼
                            AiOutboundCallee upd = new AiOutboundCallee();
                            upd.setCalleeId(callee.getCalleeId());
                            upd.setCallStatus("0");
                            upd.setRetryTimes(retryTimes + 1);
                            upd.setLastRetryTime(new Date());
                            upd.setFailReason("呼叫超时未收到事件，对账触发重试");
                            upd.setUpdateBy("outbound-reconcile");
                            calleeMapper.updateCalleeStatus(upd);
                            calleeMapper.updateNextRetryTime(callee.getCalleeId(),
                                    backoffRetryTime(retryTimes + 1), "outbound-reconcile");
                        }
                        else
                        {
                            markCalleeFailed(task, callee, "呼叫超时未收到网关事件，对账判定失败");
                        }
                    }
                }
                catch (Exception e)
                {
                    log.error("对账处理号码异常 calleeId={}", callee.getCalleeId(), e);
                }
            }
        }
        catch (Exception e)
        {
            log.error("外呼对账任务异常", e);
        }
    }

    /** T2-2 指数退避：base * 2^(retryTimes-1)，上限 10 分钟 */
    private Date backoffRetryTime(int retryTimes)
    {
        long backoff = retryBackoffBaseMs;
        for (int i = 1; i < Math.min(retryTimes, 8); i++)
        {
            backoff = Math.min(backoff * 2, 10L * 60 * 1000);
        }
        return new Date(System.currentTimeMillis() + backoff);
    }

    @PreDestroy
    public void shutdown()
    {
        if (dialPool != null)
        {
            dialPool.shutdown();
            try
            {
                if (!dialPool.awaitTermination(10, TimeUnit.SECONDS))
                {
                    dialPool.shutdownNow();
                }
            }
            catch (InterruptedException e)
            {
                dialPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void onCallEvent(String callUuid, String eventType, Map<String, Object> params)
    {
        if (callUuid == null || eventType == null)
        {
            return;
        }
        AiCallDialLog dialLog = dialLogMapper.selectByCallUuid(callUuid);
        if (dialLog == null || dialLog.getTaskId() == null)
        {
            return;
        }
        Long taskId = dialLog.getTaskId();
        String event = eventType.toUpperCase();

        // C5：按 callUuid → dialLog → calleeId 精确定位被叫，避免任务内按状态猜号码导致错配；
        // dialLog 无 calleeId（历史数据或人工外呼）时，回退按 recordId/号码匹配。
        AiOutboundCallee target = null;
        if (dialLog.getCalleeId() != null)
        {
            target = calleeMapper.selectAiOutboundCalleeByCalleeId(dialLog.getCalleeId());
        }
        if (target == null)
        {
            List<AiOutboundCallee> callees = calleeMapper.selectAiOutboundCalleeByTaskId(taskId);
            target = findCallee(callees, dialLog, event);
        }
        if (target == null)
        {
            return;
        }

        if ("ANSWERED".equals(event))
        {
            // 幂等：仅呼叫中(1)的号码处理接通，已接通/已终态的重复事件直接跳过
            if (!"1".equals(target.getCallStatus()))
            {
                log.debug("ANSWERED事件忽略：号码不在呼叫中 taskId={} calleeId={} status={}",
                        taskId, target.getCalleeId(), target.getCallStatus());
                return;
            }
            AiCallRecord record = createCallRecord(taskId, target);
            AiOutboundCallee upd = new AiOutboundCallee();
            upd.setCalleeId(target.getCalleeId());
            upd.setCallStatus("2");
            upd.setRecordId(record.getRecordId());
            upd.setUpdateBy("outbound-event");
            calleeMapper.updateCalleeStatus(upd);
            // 回写 dialLog.recordId/calleeId，保证后续 HANGUP/FAILED 事件可按 recordId 精确关联
            AiCallDialLog logUpd = new AiCallDialLog();
            logUpd.setLogId(dialLog.getLogId());
            logUpd.setRecordId(record.getRecordId());
            logUpd.setCalleeId(target.getCalleeId());
            logUpd.setUpdateBy("outbound-event");
            dialLogMapper.updateAiCallDialLog(logUpd);
            maybeCreateTicket(taskService.selectAiOutboundTaskByTaskId(taskId), target,
                    record.getRecordId(), null);
            // T5-1：外呼接通计数（网关模式，ANSWERED 事件）
            if (metrics != null)
            {
                metrics.incrementOutbound("answer");
            }
            log.info("外呼接通 taskId={} calleeId={} recordId={}", taskId, target.getCalleeId(), record.getRecordId());
            return;
        }

        if ("HANGUP".equals(event) || "FAILED".equals(event))
        {
            // 幂等：已达终态(3未接/4失败/5完成)的号码不再重复处理
            if ("3".equals(target.getCallStatus()) || "4".equals(target.getCallStatus())
                    || "5".equals(target.getCallStatus()))
            {
                log.debug("HANGUP/FAILED事件忽略：号码已终态 taskId={} calleeId={} status={}",
                        taskId, target.getCalleeId(), target.getCallStatus());
                return;
            }
            boolean connected = isConnected(params, dialLog);
            finalizeCallee(taskId, target, connected, getTalkSeconds(params, dialLog), event);
        }
    }

    // ------------------------------------------------------------------ 内部实现

    /**
     * W4：合规拦截（退订/非时段）时标记被叫为已跳过，不重试、不计失败，仅计入完成数避免任务卡死。
     */
    private void markCalleeSkipped(AiOutboundCallee callee, String reason)
    {
        try
        {
            AiOutboundCallee upd = new AiOutboundCallee();
            upd.setCalleeId(callee.getCalleeId());
            upd.setCallStatus("4");
            upd.setCallTime(new Date());
            upd.setFailReason(truncate("合规拦截:" + reason, 500));
            upd.setUpdateBy("outbound-executor");
            calleeMapper.updateCalleeStatus(upd);
            taskMapper.incrementCompletedCount(callee.getTaskId());
        }
        catch (Exception e)
        {
            log.warn("标记被叫跳过失败 calleeId={} reason={}: {}", callee.getCalleeId(), reason, e.getMessage());
        }
    }

    private void dialCallee(AiOutboundTask task, AiOutboundCallee callee)
    {
        // W4：退订号码拦截（已回复"T"退订的号码禁止外呼）
        if (complianceGuard != null && complianceGuard.isUnsubscribed(callee.getCalleeNumber()))
        {
            log.info("外呼号码已退订，跳过拨号 taskId={} calleeId={}", task.getTaskId(), callee.getCalleeId());
            markCalleeSkipped(callee, "UNSUBSCRIBED");
            return;
        }
        // P3-D1：高频置底外呼侧判定——命中 REJECT 跳过拨号（状态4跳过、不计失败、计入完成防任务卡死）；
        // 命中 PRIORITY 抬高排队优先级数值沉底（外呼队列升序，方向与入站相反）
        AiHotspotSuppress suppress = null;
        if (hotspotEnabled)
        {
            suppress = hotspotSuppressService.matchOutbound(callee.getCalleeNumber(), task.getTaskId());
        }
        if (suppress != null && "REJECT".equals(suppress.getAction()))
        {
            log.info("外呼号码命中高频置底规则[{}]，跳过拨号 taskId={} calleeId={}",
                    suppress.getRuleName(), task.getTaskId(), callee.getCalleeId());
            markCalleeSkipped(callee, "高频置底拦截:" + suppress.getRuleName());
            return;
        }
        Date dialTime = new Date();
        // 号码已由 executeTask 通过 claimCallee 原子置为呼叫中(1)，此处不再重复置状态

        DialRequest request = new DialRequest();
        request.setCalleeNumber(callee.getCalleeNumber());
        request.setCallerNumber(task.getCallerNumber());
        request.setTaskId(task.getTaskId());
        // C5：把被叫ID透传到 dial_log，回调事件按 callUuid→dialLog→calleeId 精确定位
        request.setCalleeId(callee.getCalleeId());
        int dialPriority = task.getPriority() == null ? 100 : task.getPriority();
        if (suppress != null && "PRIORITY".equals(suppress.getAction()))
        {
            log.info("外呼号码命中置底降权规则[{}]，排队优先级 {} -> {} taskId={} calleeId={}",
                    suppress.getRuleName(), dialPriority, dialPriority + HOTSPOT_SINK_OFFSET,
                    task.getTaskId(), callee.getCalleeId());
            dialPriority += HOTSPOT_SINK_OFFSET;
        }
        request.setPriority(dialPriority);
        request.setAnswerAction(task.getIvrFlowId() == null ? "BRIDGE_AGENT" : "IVR");
        request.setIvrFlowId(task.getIvrFlowId());
        request.setEnableRecord(true);
        request.setCreateBy(task.getCreateBy() == null ? "outbound-executor" : task.getCreateBy());
        request.setRemark("外呼任务:" + task.getTaskNo());

        DialResult result = callDispatchService.dialWithQueue(request);
        // T5-1：外呼下发计数（含重试再拨，每次成功下发计 1）
        if (metrics != null && result.isSuccess())
        {
            metrics.incrementOutbound("dial");
        }
        if (!result.isSuccess())
        {
            handleDialFailed(task, callee, result);
            return;
        }

        if (simulateAnswer)
        {
            handleSimulatedAnswered(task, callee, dialTime);
        }
        else
        {
            log.info("外呼已下发，等待网关事件 taskId={} calleeId={} uuid={}",
                    task.getTaskId(), callee.getCalleeId(), result.getCallUuid());
        }
    }

    private void handleSimulatedAnswered(AiOutboundTask task, AiOutboundCallee callee, Date dialTime)
    {
        AiCallRecord record = createCallRecord(task.getTaskId(), callee);
        // IVR 流程为模型/远程调用，放在事务外执行，避免长事务占用数据库连接
        IvrExecuteResult flowResult = runIvrFlow(task, callee, record.getRecordId());

        final int talkSeconds = simulateTalkSeconds > 0 ? simulateTalkSeconds : 0;
        final AiOutboundTask fTask = task;
        final AiOutboundCallee fCallee = callee;
        final AiCallRecord fRecord = record;
        final IvrExecuteResult fFlowResult = flowResult;
        final Date fDialTime = dialTime;
        // C4：号码状态、外呼结果、任务计数多步写库放在同一事务内，失败整体回滚，保证计数一致
        transactionTemplate.executeWithoutResult(status ->
        {
            AiOutboundCallee upd = new AiOutboundCallee();
            upd.setCalleeId(fCallee.getCalleeId());
            upd.setCallStatus("5");
            upd.setRecordId(fRecord.getRecordId());
            upd.setCallDuration(talkSeconds);
            upd.setUpdateBy("outbound-executor");
            calleeMapper.updateCalleeStatus(upd);

            createOutboundResult(fTask, fCallee, fRecord.getRecordId(), "1", fDialTime, new Date(),
                    talkSeconds, fFlowResult);
            taskMapper.incrementAnsweredCount(fTask.getTaskId());
            taskMapper.incrementCompletedCount(fTask.getTaskId());
        });

        if (flowResult != null)
        {
            updateRecordWithFlow(record.getRecordId(), flowResult);
        }
        maybeCreateTicket(task, callee, record.getRecordId(), flowResult);
        // T5-1：外呼接通计数（模拟模式）
        if (metrics != null)
        {
            metrics.incrementOutbound("answer");
        }
        log.info("外呼模拟接通完成 taskId={} calleeId={} recordId={} 意图={}",
                task.getTaskId(), callee.getCalleeId(), record.getRecordId(),
                flowResult == null ? null : flowResult.getMatchedIntentionName());
    }

    private IvrExecuteResult runIvrFlow(AiOutboundTask task, AiOutboundCallee callee, Long recordId)
    {
        if (task.getIvrFlowId() == null)
        {
            return null;
        }
        IvrExecuteRequest req = new IvrExecuteRequest();
        req.setFlowId(task.getIvrFlowId());
        req.setRecordId(recordId);
        req.setCallerNumber(task.getCallerNumber());
        req.setCalleeNumber(callee.getCalleeNumber());
        String sampleInput = sampleInput(callee);
        req.getInputs().add(sampleInput);
        req.getVariables().put("taskId", task.getTaskId());
        req.getVariables().put("taskNo", task.getTaskNo());
        req.getVariables().put("taskName", task.getTaskName());
        req.getVariables().put("calleeName", callee.getCalleeName());
        req.getVariables().put("calleeNumber", callee.getCalleeNumber());
        try
        {
            return ivrEngineService.executeFlow(req);
        }
        catch (Exception e)
        {
            log.warn("外呼IVR流程执行异常 taskId={} calleeId={} error={}",
                    task.getTaskId(), callee.getCalleeId(), e.getMessage());
            return null;
        }
    }

    /** 演示用输入：优先取号码附加参数中的 input 字段，其次姓名，最后默认法律咨询话术 */
    private String sampleInput(AiOutboundCallee callee)
    {
        if (StringUtils.isNotEmpty(callee.getCalleeParams()))
        {
            try
            {
                Map<String, Object> params = MAPPER.readValue(callee.getCalleeParams(),
                        MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                if (params.get("input") != null && StringUtils.isNotEmpty(params.get("input").toString()))
                {
                    return params.get("input").toString();
                }
            }
            catch (Exception ignored)
            {
            }
        }
        if (StringUtils.isNotEmpty(callee.getCalleeName()))
        {
            return "我想咨询" + callee.getCalleeName() + "相关的法律问题";
        }
        return "我想咨询一下法律问题，比如合同纠纷";
    }

    private void handleDialFailed(AiOutboundTask task, AiOutboundCallee callee, DialResult result)
    {
        String reason = result == null ? "未知错误" : result.getMessage();
        int retryTimes = callee.getRetryTimes() == null ? 0 : callee.getRetryTimes();
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();

        if (retryTimes < retryCount && isRetryableError(result))
        {
            // 重试：号码回到待呼叫队列，等待下一轮扫描
            AiOutboundCallee upd = new AiOutboundCallee();
            upd.setCalleeId(callee.getCalleeId());
            upd.setCallStatus("0");
            upd.setRetryTimes(retryTimes + 1);
            upd.setLastRetryTime(new Date());
            upd.setFailReason(truncate(reason, 500));
            upd.setUpdateBy("outbound-executor");
            calleeMapper.updateCalleeStatus(upd);
            // T2-2 指数退避：记录下次可重试时间，未到期号码扫描时不捞出，避免失败号码被每 15s 反复重拨
            calleeMapper.updateNextRetryTime(callee.getCalleeId(),
                    backoffRetryTime(retryTimes + 1), "outbound-executor");
            // T5-1：外呼重试计数
            if (metrics != null)
            {
                metrics.incrementOutbound("retry");
            }
            log.info("外呼失败进入重试 taskId={} calleeId={} 第{}次 reason={}",
                    task.getTaskId(), callee.getCalleeId(), retryTimes + 1, reason);
            return;
        }

        markCalleeFailed(task, callee, reason);
    }

    private void markCalleeFailed(final AiOutboundTask task, final AiOutboundCallee callee, final String reason)
    {
        // C4：号码失败状态、外呼结果、任务失败/完成计数同事务提交
        transactionTemplate.executeWithoutResult(status ->
        {
            AiOutboundCallee upd = new AiOutboundCallee();
            upd.setCalleeId(callee.getCalleeId());
            upd.setCallStatus("4");
            upd.setCallTime(new Date());
            upd.setFailReason(truncate(reason, 500));
            upd.setUpdateBy("outbound-executor");
            calleeMapper.updateCalleeStatus(upd);

            createOutboundResult(task, callee, null, "7", new Date(), new Date(), 0, null);
            taskMapper.incrementFailedCount(task.getTaskId());
            taskMapper.incrementCompletedCount(task.getTaskId());
        });
        // T5-1：外呼终态失败计数（重试耗尽/永久失败；未接不计入此口径）
        if (metrics != null)
        {
            metrics.incrementOutbound("fail");
        }
    }

    private AiCallRecord createCallRecord(Long taskId, AiOutboundCallee callee)
    {
        AiOutboundTask task = taskService.selectAiOutboundTaskByTaskId(taskId);
        AiCallRecord record = new AiCallRecord();
        record.setCallerNumber(task == null ? null : task.getCallerNumber());
        record.setCallerName(callee.getCalleeName());
        record.setCallerAddress(callee.getCalleeAddress());
        record.setCallTime(new Date());
        record.setStatus("1");
        record.setContent("外呼任务[" + (task == null ? "" : task.getTaskNo())
                + "] 被叫号码:" + callee.getCalleeNumber());
        record.setRemark("智能外呼接通，任务:" + (task == null ? "" : task.getTaskNo()));
        record.setCreateBy(task == null ? "outbound-executor" : task.getCreateBy());
        callRecordService.insertAiCallRecord(record);
        return record;
    }

    private void updateRecordWithFlow(Long recordId, IvrExecuteResult flowResult)
    {
        try
        {
            AiCallRecord update = new AiCallRecord();
            update.setRecordId(recordId);
            if (flowResult.getCategoryId() != null)
            {
                update.setCategoryId(flowResult.getCategoryId());
            }
            if (StringUtils.isNotEmpty(flowResult.getTransferTarget()))
            {
                update.setStatus("2");
            }
            update.setRemark("【IVR】流程[" + flowResult.getFlowName() + "] 意图="
                    + flowResult.getMatchedIntentionName() + "(" + flowResult.getMatchedIntention() + ")"
                    + " 分类=" + flowResult.getCategoryName());
            callEventDispatcher.updateCallRecordAsync(update);
        }
        catch (Exception e)
        {
            log.warn("外呼话单回写IVR结果失败 recordId={} error={}", recordId, e.getMessage());
        }
    }

    /**
     * 融合点 3.5.3：外呼任务与工单集成——回访类任务或转人工类外呼接通后自动创建工单。
     */
    private void maybeCreateTicket(AiOutboundTask task, AiOutboundCallee callee,
                                   Long recordId, IvrExecuteResult flowResult)
    {
        if (!autoCreateTicket || task == null || recordId == null)
        {
            return;
        }
        boolean visitTask = "2".equals(task.getTaskType());
        boolean transferNeeded = flowResult != null && StringUtils.isNotEmpty(flowResult.getTransferTarget());
        if (!visitTask && !transferNeeded)
        {
            return;
        }
        try
        {
            AiCallTicket ticket = new AiCallTicket();
            ticket.setRecordId(recordId);
            ticket.setTitle((visitTask ? "回访工单" : "跟进工单") + "：" + task.getTaskName());
            ticket.setContent("外呼任务[" + task.getTaskNo() + "] 被叫号码:" + callee.getCalleeNumber()
                    + " 被叫姓名:" + (callee.getCalleeName() == null ? "" : callee.getCalleeName())
                    + (flowResult != null && StringUtils.isNotEmpty(flowResult.getMatchedIntentionName())
                            ? " 识别意图:" + flowResult.getMatchedIntentionName() : "")
                    + "。请及时跟进处理。");
            ticket.setPriority("2");
            ticket.setStatus("0");
            ticket.setAssignUserName(task.getCallerName());
            ticket.setCreateBy(task.getCreateBy() == null ? "outbound-executor" : task.getCreateBy());
            callTicketService.insertAiCallTicket(ticket);
            log.info("外呼自动创建工单 taskId={} calleeId={} ticketNo={}",
                    task.getTaskId(), callee.getCalleeId(), ticket.getTicketNo());
        }
        catch (Exception e)
        {
            log.warn("外呼自动创建工单失败: {}", e.getMessage());
        }
    }

    private void createOutboundResult(AiOutboundTask task, AiOutboundCallee callee, Long recordId,
                                      String callResult, Date startTime, Date endTime,
                                      int duration, IvrExecuteResult flowResult)
    {
        AiOutboundResult result = new AiOutboundResult();
        result.setTaskId(task.getTaskId());
        result.setCalleeId(callee.getCalleeId());
        result.setCalleeNumber(callee.getCalleeNumber());
        result.setCalleeName(callee.getCalleeName());
        result.setRecordId(recordId);
        result.setCallResult(callResult);
        result.setStartTime(startTime);
        result.setEndTime(endTime);
        result.setCallDuration(duration);
        if (flowResult != null)
        {
            result.setIntentionCode(flowResult.getMatchedIntention());
            result.setIntentionName(flowResult.getMatchedIntentionName());
            result.setSummary("IVR流程[" + flowResult.getFlowName() + "]执行完成，共"
                    + (flowResult.getSteps() == null ? 0 : flowResult.getSteps().size()) + "个节点");
            try
            {
                result.setFlowData(MAPPER.writeValueAsString(flowResult));
            }
            catch (Exception ignored)
            {
            }
        }
        else
        {
            result.setSummary("外呼完成（无IVR流程）");
        }
        result.setCreateBy(task.getCreateBy());
        resultService.insertAiOutboundResult(result);
    }

    /** 网关事件：最终化号码（接通/未接/失败）并生成结果 */
    private void finalizeCallee(final Long taskId, final AiOutboundCallee callee, final boolean connected,
                                final int talkSeconds, final String event)
    {
        final AiOutboundTask task = taskService.selectAiOutboundTaskByTaskId(taskId);
        if (task == null)
        {
            return;
        }
        final int retryTimes = callee.getRetryTimes() == null ? 0 : callee.getRetryTimes();
        final int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();

        // C4：号码状态流转、外呼结果、任务计数在同一事务内提交，避免中途失败导致计数与状态不一致
        transactionTemplate.executeWithoutResult(status ->
        {
            if (connected)
            {
                AiOutboundCallee upd = new AiOutboundCallee();
                upd.setCalleeId(callee.getCalleeId());
                upd.setCallStatus("5");
                upd.setCallDuration(talkSeconds);
                upd.setUpdateBy("outbound-event");
                calleeMapper.updateCalleeStatus(upd);

                createOutboundResult(task, callee, callee.getRecordId(), "1",
                        callee.getCallTime(), new Date(), talkSeconds, null);
                taskMapper.incrementAnsweredCount(taskId);
                taskMapper.incrementCompletedCount(taskId);
                return;
            }

            if (retryTimes < retryCount && !"FAILED".equalsIgnoreCase(event))
            {
                AiOutboundCallee upd = new AiOutboundCallee();
                upd.setCalleeId(callee.getCalleeId());
                upd.setCallStatus("0");
                upd.setRetryTimes(retryTimes + 1);
                upd.setLastRetryTime(new Date());
                upd.setUpdateBy("outbound-event");
                calleeMapper.updateCalleeStatus(upd);
                // T2-2 指数退避：网关事件判定未接通的重试同样设置下次重试窗口
                calleeMapper.updateNextRetryTime(callee.getCalleeId(),
                        backoffRetryTime(retryTimes + 1), "outbound-event");
                log.info("外呼未接通进入重试 taskId={} calleeId={} 第{}次", taskId, callee.getCalleeId(), retryTimes + 1);
                return;
            }

            String callResult = "FAILED".equalsIgnoreCase(event) ? "4" : "2";
            AiOutboundCallee upd = new AiOutboundCallee();
            upd.setCalleeId(callee.getCalleeId());
            upd.setCallStatus("FAILED".equalsIgnoreCase(event) ? "4" : "3");
            upd.setCallDuration(0);
            upd.setFailReason("网关事件:" + event);
            upd.setUpdateBy("outbound-event");
            calleeMapper.updateCalleeStatus(upd);

            createOutboundResult(task, callee, callee.getRecordId(), callResult,
                    callee.getCallTime(), new Date(), 0, null);
            if ("FAILED".equalsIgnoreCase(event))
            {
                taskMapper.incrementFailedCount(taskId);
            }
            else
            {
                taskMapper.incrementNoAnswerCount(taskId);
            }
            taskMapper.incrementCompletedCount(taskId);
        });
    }

    private void checkTaskCompletion(Long taskId)
    {
        AiOutboundTask task = taskService.selectAiOutboundTaskByTaskId(taskId);
        if (task == null || !"1".equals(task.getStatus()))
        {
            return;
        }
        // T1-6 完成判定收口为单条 SQL：不存在任何非终态号码(0待呼叫/1呼叫中/2已接通待挂断)即全部完成，
        // 替代旧的三次 count 求和（N+1 且依赖计数一致性，计数漂移会导致任务永久卡在执行中）
        if (calleeMapper.countUnfinishedByTaskId(taskId) == 0)
        {
            AiOutboundTask upd = new AiOutboundTask();
            upd.setTaskId(taskId);
            upd.setStatus("2");
            upd.setUpdateBy("outbound-executor");
            taskService.updateAiOutboundTask(upd);
            log.info("外呼任务全部执行完成 taskId={}", taskId);
            notifyTaskCompleted(task);
        }
    }

    /**
     * T5-3 消息中心：任务全部完成后向创建人投递站内信。
     * 消费/定时线程无 HTTP 上下文，接收人经 createBy（登录名）反查 userId；
     * 投递失败仅告警，不影响外呼主流程。
     */
    private void notifyTaskCompleted(AiOutboundTask task)
    {
        if (messageNotifyDispatcher == null || task == null || StringUtils.isEmpty(task.getCreateBy()))
        {
            return;
        }
        try
        {
            SysUser creator = userService.selectUserByUserName(task.getCreateBy());
            if (creator == null || creator.getUserId() == null)
            {
                log.debug("外呼任务完成通知跳过：创建人不存在 createBy={}", task.getCreateBy());
                return;
            }
            String name = StringUtils.isNotEmpty(task.getTaskName()) ? task.getTaskName() : String.valueOf(task.getTaskId());
            String content = "外呼任务「" + name + "」已全部执行完成：计划 "
                    + (task.getTotalCount() == null ? 0 : task.getTotalCount()) + " 个号码，接通 "
                    + (task.getAnsweredCount() == null ? 0 : task.getAnsweredCount()) + "，未接听 "
                    + (task.getNoAnswerCount() == null ? 0 : task.getNoAnswerCount()) + "，失败 "
                    + (task.getFailedCount() == null ? 0 : task.getFailedCount()) + "，请查看外呼结果。";
            messageNotifyDispatcher.notify(creator.getUserId(), "1", "外呼任务已完成：" + name,
                    content, "outbound", task.getTaskId(), "system");
        }
        catch (Exception e)
        {
            log.warn("外呼任务完成站内信投递失败, taskId={}", task.getTaskId(), e);
        }
    }

    /**
     * 回退匹配（dialLog 无 calleeId 时使用）：优先 recordId 精确匹配，其次被叫号码精确匹配，
     * 最后按事件类型选择状态匹配（ANSWERED→呼叫中1/已接通2，HANGUP/FAILED→已接通2）。
     */
    private AiOutboundCallee findCallee(List<AiOutboundCallee> callees, AiCallDialLog dialLog, String event)
    {
        if (callees == null || callees.isEmpty())
        {
            return null;
        }
        if (dialLog != null && dialLog.getRecordId() != null)
        {
            for (AiOutboundCallee c : callees)
            {
                if (dialLog.getRecordId().equals(c.getRecordId()))
                {
                    return c;
                }
            }
        }
        if (dialLog != null && StringUtils.isNotEmpty(dialLog.getCalleeNumber()))
        {
            for (AiOutboundCallee c : callees)
            {
                if (dialLog.getCalleeNumber().equals(c.getCalleeNumber()))
                {
                    return c;
                }
            }
        }
        String[] prefer = "ANSWERED".equals(event) ? new String[]{"1", "2"} : new String[]{"2", "1"};
        for (String status : prefer)
        {
            for (AiOutboundCallee c : callees)
            {
                if (status.equals(c.getCallStatus()))
                {
                    return c;
                }
            }
        }
        return null;
    }

    private boolean isConnected(Map<String, Object> params, AiCallDialLog dialLog)
    {
        if (dialLog != null && ("1".equals(dialLog.getIsConnected()) || dialLog.getAnswerTime() != null))
        {
            return true;
        }
        if (params == null)
        {
            return false;
        }
        Object connected = params.get("isConnected");
        if (connected != null)
        {
            return "1".equals(connected.toString());
        }
        Object talk = params.get("talkDuration");
        if (talk == null)
        {
            return false;
        }
        // R12：回调参数可能为非数字，parseInt 需防护，避免异常中断事件处理
        try
        {
            return Integer.parseInt(talk.toString()) > 0;
        }
        catch (NumberFormatException e)
        {
            log.warn("talkDuration 参数非数字，按未接通处理 value={}", talk);
            return false;
        }
    }

    private int getTalkSeconds(Map<String, Object> params, AiCallDialLog dialLog)
    {
        if (params != null && params.get("talkDuration") != null)
        {
            try
            {
                return Integer.parseInt(params.get("talkDuration").toString());
            }
            catch (Exception ignored)
            {
            }
        }
        if (dialLog != null && dialLog.getTalkDuration() != null)
        {
            return dialLog.getTalkDuration();
        }
        return 0;
    }

    private boolean isRetryableError(DialResult result)
    {
        if (result == null || result.getErrorCode() == null)
        {
            return false;
        }
        switch (result.getErrorCode())
        {
            case "NO_AVAILABLE_TRUNK":
            case "TRUNK_BUSY":
            case "CPS_LIMIT":
            case "QUEUE_FULL":
            case "GATEWAY_ERROR":
            case "GATEWAY_REJECT":
            case "DIAL_FAILED":
                return true;
            default:
                return false;
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
