package ai.lawyers.system.service.impl.lawyers.outbound;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundCallee;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundTask;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundCalleeMapper;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundTaskMapper;
import ai.lawyers.system.service.ISysUserService;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;
import ai.lawyers.system.service.lawyers.cluster.InstanceDrainState;
import ai.lawyers.system.service.lawyers.concurrent.ExecutorRegistry;
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundTaskService;
import ai.lawyers.system.service.lawyers.outbound.IOutboundExecutionService;
import ai.lawyers.system.service.lawyers.queue.MessageNotifyDispatcher;
import ai.lawyers.system.service.lawyers.compliance.ComplianceGuard;

/**
 * 智能外呼执行引擎实现（调度门面）。
 *
 * 双模式：
 *  - 模拟模式（call.outbound.simulateAnswer=true，默认）：下发成功后直接模拟接通，
 *    进入 IVR 流程并生成话单/结果，便于无语音网关环境联调；
 *  - 网关模式：下发后保持"呼叫中"，由 FreeSWITCH/Asterisk 事件回调最终化。
 *
 * 重试策略：可重试失败（无可用线路/线路忙/网关错误等）在 retryCount 内自动回拨，
 * 重试次数用尽或永久失败后计入失败统计。
 *
 * <p>P5 超大类拆分：本类只保留"调度编排"职责——任务扫描/并发拨号编排/对账/完成判定/通知；
 * 单号码拨号执行下沉 {@link OutboundDialer}，落库收尾下沉 {@link OutboundCallFinalizer}，
 * 网关事件处理下沉 {@link OutboundEventCallback}，重试纯逻辑见 {@link OutboundRetryPolicy}。
 * 协作类均不反向依赖门面，无循环依赖。</p>
 */
@Service
public class OutboundExecutionServiceImpl implements IOutboundExecutionService
{
    private static final Logger log = LoggerFactory.getLogger(OutboundExecutionServiceImpl.class);

    @Autowired
    private IAiOutboundTaskService taskService;

    @Autowired
    private AiOutboundTaskMapper taskMapper;

    @Autowired
    private AiOutboundCalleeMapper calleeMapper;

    @Autowired
    private Environment environment;

    /** T5-3 消息中心：任务完成站内信投递（队列不可用时内部降级，不影响外呼主流程） */
    @Autowired(required = false)
    private MessageNotifyDispatcher messageNotifyDispatcher;

    /** P3-C6：实例排空状态，排空中外呼扫描停领新任务 */
    @Autowired(required = false)
    private InstanceDrainState drainState;

    /** W4：合规守卫（扫描时段判定；拨号侧拦截见 Dialer） */
    @Autowired(required = false)
    private ComplianceGuard complianceGuard;

    /** P5：拨号执行协作类（合规拦截/高频置底/下发/模拟接通/失败重试） */
    @Autowired
    private OutboundDialer dialer;

    /** P5：落库收尾协作类（号码状态/结果/计数/话单/工单） */
    @Autowired
    private OutboundCallFinalizer finalizer;

    /** P5：网关事件回调协作类（ANSWERED/HANGUP/FAILED 幂等终态化） */
    @Autowired
    private OutboundEventCallback eventCallback;

    /** createBy（登录名）反查 userId，用于确定站内信接收人 */
    @Autowired
    private ISysUserService userService;

    /** 模拟接通：无真实网关时直接完成全链路（仅用于启动期风险告警判定） */
    @Value("${call.outbound.simulateAnswer:true}")
    private boolean simulateAnswer;

    /** 是否启用定时扫描执行 */
    @Value("${call.outbound.scanEnabled:true}")
    private boolean scanEnabled;

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

    /** T2-2 并发拨号线程池（P2：改由 ExecutorRegistry 统一纳管，有界队列+CallerRuns 回压+水位指标） */
    private ExecutorService dialPool;

    /** 拨号池有界队列容量：单任务批次 ≤100，千级缓冲足够；打满由 CallerRuns 回压到扫描线程 */
    private static final int OUTBOUND_DIAL_QUEUE_CAPACITY = 1000;

    /** N7：扫描/对账任务的集群单主锁（Lua CAS 释放，替代原裸 delete 锁） */
    private static final String SCAN_LOCK_NAME = "job:outbound-scan";
    private static final String RECONCILE_LOCK_NAME = "job:outbound-reconcile";
    private static final Duration RECONCILE_LOCK_TTL = Duration.ofMinutes(10);

    @Autowired
    private RedisLeaderLock leaderLock;

    /** P2：线程池统一注册中心（命名/有界队列/拒绝策略/Micrometer 指标/水位告警/统一停机） */
    @Autowired
    private ExecutorRegistry executorRegistry;

    /**
     * R11 防护：模拟接通开关仅允许在开发/测试环境开启。
     * 生产环境若误开，将直接产生假话单/假接通统计，启动时强告警以便第一时间发现配置错误。
     */
    @PostConstruct
    public void checkSimulateSwitch()
    {
        // T2-2 并发拨号线程池：P2 改由 ExecutorRegistry 统一创建——
        // 有界队列（原实现为无界 LinkedBlockingQueue）+ CallerRuns 回压 + Micrometer 指标 + 30s 水位巡检
        int poolSize = Math.max(1, Math.min(dialConcurrent, 64));
        dialPool = executorRegistry.create("outbound-dial", poolSize, poolSize, OUTBOUND_DIAL_QUEUE_CAPACITY);
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
                    dialer.dialCallee(task, callee);
                }
                catch (Exception e)
                {
                    log.error("外呼号码执行异常 taskId={} calleeId={}", taskId, callee.getCalleeId(), e);
                    finalizer.markCalleeFailed(task, callee, "执行异常：" + e.getMessage());
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
            // P3-C6：实例排空（优雅停机/无损发布）期间不领取新任务，
            // 在途拨号继续执行至完成；多实例下由其他实例扫描接管
            if (drainState != null && drainState.isDraining())
            {
                log.info("实例排空中，本轮外呼扫描跳过新任务");
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
                        finalizer.finalizeCallee(task.getTaskId(), callee, true, 0, "RECONCILE");
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
                                    OutboundRetryPolicy.backoffRetryTime(retryTimes + 1, retryBackoffBaseMs),
                                    "outbound-reconcile");
                        }
                        else
                        {
                            finalizer.markCalleeFailed(task, callee, "呼叫超时未收到网关事件，对账判定失败");
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
    public void onCallEvent(String callUuid, String eventType, java.util.Map<String, Object> params)
    {
        // P5：网关事件处理下沉 OutboundEventCallback，门面只做路由
        eventCallback.handle(callUuid, eventType, params);
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
}
