package ai.lawyers.system.service.impl.lawyers.outbound;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import ai.lawyers.system.domain.lawyers.outbound.AiOutboundCallee;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundTask;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundCalleeMapper;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundTaskMapper;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundTaskService;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W3 多实例领号并发测试：号码级原子领取（claimCallee）+ 引擎层"领取失败即跳过"不变量。
 *
 * <p>生产 SQL：{@code update ai_outbound_callee set call_status='1'
 * where callee_id=? and call_status='0'}，原子性由 InnoDB 行锁 + 条件更新保证，
 * 影响行数 1=领取成功、0=已被其他实例领取。单元测试无法真实连 MySQL，
 * 用 Mockito Answer 复刻该条件更新语义（ConcurrentHashMap + AtomicInteger CAS），
 * 并把它作为<strong>两个"实例"共享的同一数据库</strong>，驱动真实
 * {@link OutboundExecutionServiceImpl#executeTask} 并发执行，验证业务最关键不变量：</p>
 *
 * <ul>
 *   <li>同一 calleeId 被两个线程同时 claim，仅一个成功（影响行数=1），另一个返回 0；</li>
 *   <li>多实例并发 executeTask 同一任务时，每个号码恰好下发一次（无重复拨号）；</li>
 *   <li>领取失败的号码不进入 dialCallee（不下发、不写状态）。</li>
 * </ul>
 *
 * @author ai-lawyers
 */
class OutboundClaimCalleeConcurrencyTest
{
    private static final Long TASK_ID = 100L;
    private static final Long CALLEE_A = 1L;
    private static final Long CALLEE_B = 2L;

    /** 共享"数据库"：calleeId -> 行状态（0=待呼叫，1=呼叫中），复刻 DB 行级原子性 */
    private final Map<Long, AtomicInteger> rows = new ConcurrentHashMap<>();

    /** 两个"实例"共享同一 Mapper（即同一数据库） */
    private AiOutboundCalleeMapper calleeMapper;

    private ICallDispatchService callDispatchService;

    /** claim 成功次数（Answer 内计数） */
    private final AtomicInteger claimSuccess = new AtomicInteger();

    @BeforeEach
    void setUp()
    {
        calleeMapper = mock(AiOutboundCalleeMapper.class);
        callDispatchService = mock(ICallDispatchService.class);

        // 复刻 claimCallee SQL：仅当 call_status=0 时 CAS 置 1，返回影响行数
        when(calleeMapper.claimCallee(anyLong(), anyString())).thenAnswer(inv -> {
            Long calleeId = inv.getArgument(0);
            AtomicInteger row = rows.computeIfAbsent(calleeId, k -> new AtomicInteger(0));
            boolean won = row.compareAndSet(0, 1);
            if (won)
            {
                claimSuccess.incrementAndGet();
            }
            return won ? 1 : 0;
        });
        // 完成判定：仍有未终态号码，任务不判完成（避免触发站内信旁路）
        when(calleeMapper.countUnfinishedByTaskId(TASK_ID)).thenReturn(1);
        // 拨号网关：一律下发成功
        when(callDispatchService.dialWithQueue(any())).thenAnswer(inv -> {
            DialResult r = new DialResult();
            r.setSuccess(true);
            r.setCallUuid("uuid-" + System.nanoTime());
            return r;
        });
    }

    /** 构造一个最小可执行的服务实例（dialConcurrent=1 串行、simulateAnswer=false 免事务旁路） */
    private OutboundExecutionServiceImpl newServiceInstance()
    {
        OutboundExecutionServiceImpl service = new OutboundExecutionServiceImpl();
        IAiOutboundTaskService taskService = mock(IAiOutboundTaskService.class);
        AiOutboundTask task = new AiOutboundTask();
        task.setTaskId(TASK_ID);
        task.setStatus("1"); // 执行中
        task.setTaskNo("PERF-001");
        task.setMaxConcurrent(10);
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task);

        AiOutboundTaskMapper taskMapper = mock(AiOutboundTaskMapper.class);
        IAiCallTicketService ticketService = mock(IAiCallTicketService.class);

        setField(service, "taskService", taskService);
        setField(service, "calleeMapper", calleeMapper);
        setField(service, "taskMapper", taskMapper);
        setField(service, "callDispatchService", callDispatchService);
        setField(service, "callTicketService", ticketService);
        setField(service, "dialConcurrent", 1);       // 串行执行 job，免 dialPool（未初始化）
        setField(service, "simulateAnswer", false);   // 下发即止，免 transactionTemplate/IVR 旁路
        return service;
    }

    /** 两个实例共享的待呼叫号码列表（同一批次，多实例扫描同库时即此场景） */
    private List<AiOutboundCallee> pendingCallees()
    {
        AiOutboundCallee a = new AiOutboundCallee();
        a.setCalleeId(CALLEE_A);
        a.setTaskId(TASK_ID);
        a.setCalleeNumber("13800000001");
        a.setCallStatus("0");
        AiOutboundCallee b = new AiOutboundCallee();
        b.setCalleeId(CALLEE_B);
        b.setTaskId(TASK_ID);
        b.setCalleeNumber("13800000002");
        b.setCallStatus("0");
        return new ArrayList<>(Arrays.asList(a, b));
    }

    /**
     * 场景1：同一 calleeId 被两个线程同时 claim（同一 Mapper=同一数据库行），
     * 仅一个线程影响行数=1，另一个=0。
     */
    @Test
    void claimCallee_sameCallee_twoThreads_onlyOneWins() throws InterruptedException
    {
        int threads = 2;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger wins = new AtomicInteger();
        for (int i = 0; i < threads; i++)
        {
            new Thread(() -> {
                try
                {
                    start.await();
                    wins.addAndGet(calleeMapper.claimCallee(CALLEE_A, "instance-" + Thread.currentThread().getName()));
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
                finally
                {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(wins.get()).as("同一号码并发领取影响行数合计必须为 1").isEqualTo(1);
        assertThat(claimSuccess.get()).isEqualTo(1);
        assertThat(rows.get(CALLEE_A).get()).as("号码终态为呼叫中(1)").isEqualTo(1);
    }

    /**
     * 场景2：两个"实例"并发 executeTask 同一任务（共享同一数据库），
     * 每个号码恰好下发一次、无重复拨号；领取失败方不触发 dial。
     */
    @Test
    void executeTask_twoInstances_noDuplicateDial() throws InterruptedException
    {
        List<AiOutboundCallee> pending = pendingCallees();
        when(calleeMapper.selectDialableCallees(TASK_ID, 10)).thenReturn(pending);

        OutboundExecutionServiceImpl instanceA = newServiceInstance();
        OutboundExecutionServiceImpl instanceB = newServiceInstance();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger totalDialled = new AtomicInteger();
        for (OutboundExecutionServiceImpl instance : Arrays.asList(instanceA, instanceB))
        {
            new Thread(() -> {
                try
                {
                    start.await();
                    totalDialled.addAndGet(instance.executeTask(TASK_ID));
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
                finally
                {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

        // 两个实例各自扫描到 2 个号码：领取尝试共 4 次，成功 2 次（每号码一次）
        verify(calleeMapper, times(4)).claimCallee(anyLong(), anyString());
        assertThat(claimSuccess.get()).as("两个号码各被领取一次").isEqualTo(2);
        // 每个号码恰好下发一次
        verify(callDispatchService, times(2)).dialWithQueue(any());
        assertThat(rows.get(CALLEE_A).get()).isEqualTo(1);
        assertThat(rows.get(CALLEE_B).get()).isEqualTo(1);
    }

    /**
     * 场景3：号码已被其他实例领取（行状态非 0）时，本实例 claim 返回 0，
     * executeTask 直接跳过，不产生任何下发。
     */
    @Test
    void executeTask_calleeAlreadyClaimed_skipDial()
    {
        rows.put(CALLEE_A, new AtomicInteger(1)); // 已被其他实例置为呼叫中
        when(calleeMapper.selectDialableCallees(TASK_ID, 10))
                .thenReturn(new ArrayList<>(Arrays.asList(pendingCallees().get(0))));

        OutboundExecutionServiceImpl instance = newServiceInstance();
        int dialled = instance.executeTask(TASK_ID);

        assertThat(dialled).as("扫描批次仍为 2 中取 1").isEqualTo(1);
        assertThat(claimSuccess.get()).as("领取必须失败（行已被占）").isEqualTo(0);
        Mockito.verifyNoInteractions(callDispatchService);
    }

    private static void setField(Object target, String name, Object value)
    {
        try
        {
            Field f = OutboundExecutionServiceImpl.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("注入字段失败: " + name, e);
        }
    }
}
