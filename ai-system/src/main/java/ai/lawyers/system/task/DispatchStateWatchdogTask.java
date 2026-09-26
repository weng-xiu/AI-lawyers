package ai.lawyers.system.task;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ai.lawyers.system.domain.lawyers.trunk.AiCallDialLog;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.enums.DialStatusEnum;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallTrunkMapper;
import ai.lawyers.system.service.impl.lawyers.trunk.ClusterDispatchState;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * P3-C1：呼叫调度状态看门狗（仅集群模式生效）。
 *
 * <p>多实例下 Redis 外置的调度状态仍可能因"释放指令丢失 / 节点崩溃 / 迟到回调"产生残留，
 * 本任务周期性执行两项自愈，经 {@link RedisLeaderLock} 全局单主执行：</p>
 * <ol>
 *   <li><b>全局并发漂移矫正</b>：以全部启用线路 DB 侧 current_concurrent 合计为权威值，
 *       Redis 计数高于「权威值 + 在途余量」时原子向下矫正，回收泄漏槽位（只收缩不扩张）；</li>
 *   <li><b>残留呼叫-线路占用清理</b>：SCAN holder（call:dispatch:trunk:*），话单已终态
 *       或查无记录的 holder 判为残留并删除（holder 仅是挂断定位加速映射，hangup 有 DB 回退）。</li>
 * </ol>
 *
 * <p>{@code call.dispatch.cluster.enabled=false}（默认单机模式）时本任务直接跳过，
 * 零行为变化；{@code call.dispatch.watchdog.enabled=false} 可应急关闭。</p>
 *
 * @author ai-lawyers
 */
@Component
public class DispatchStateWatchdogTask
{
    private static final Logger log = LoggerFactory.getLogger(DispatchStateWatchdogTask.class);

    private static final String LOCK_NAME = "job:dispatch-state-watchdog";

    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    @Value("${call.dispatch.watchdog.enabled:true}")
    private boolean watchdogEnabled;

    /** 在途容忍余量：覆盖"线路占位→全局占位 / 线路释放→全局释放"两个提交窗口 */
    @Value("${call.dispatch.watchdog.inflight-margin:8}")
    private int inflightMargin;

    @Autowired
    private ClusterDispatchState clusterState;

    @Autowired
    private AiCallTrunkMapper trunkMapper;

    @Autowired
    private AiCallDialLogMapper dialLogMapper;

    @Autowired
    private RedisLeaderLock leaderLock;

    @Scheduled(fixedDelayString = "${call.dispatch.watchdog.interval-ms:300000}",
            initialDelayString = "${call.dispatch.watchdog.initial-delay-ms:120000}")
    public void scan()
    {
        if (!watchdogEnabled || !clusterState.isClusterEnabled())
        {
            return;
        }
        leaderLock.tryRun(LOCK_NAME, LOCK_TTL, this::doScan);
    }

    private void doScan()
    {
        // 1. 全局并发漂移矫正：DB 全部启用线路当前并发合计为权威
        int dbTotal = 0;
        List<AiCallTrunk> trunks = trunkMapper.selectAllEnabledTrunks();
        if (trunks != null)
        {
            for (AiCallTrunk trunk : trunks)
            {
                if (trunk.getCurrentConcurrent() != null)
                {
                    dbTotal += trunk.getCurrentConcurrent();
                }
            }
        }
        long reclaimed = clusterState.reconcileGlobalConcurrent(dbTotal, inflightMargin);
        if (reclaimed > 0)
        {
            log.warn("[DispatchWatchdog] 全局并发计数向下矫正，回收泄漏槽位 {} 个（DB线路并发合计={} 余量={}）",
                    reclaimed, dbTotal, inflightMargin);
        }

        // 2. 残留呼叫-线路占用 holder 清理
        int cleaned = 0;
        for (String callUuid : clusterState.scanTrunkHolders())
        {
            AiCallDialLog dialLog = dialLogMapper.selectByCallUuid(callUuid);
            if (dialLog == null || DialStatusEnum.isFinal(dialLog.getDialStatus()))
            {
                clusterState.releaseTrunk(callUuid);
                cleaned++;
            }
        }
        if (cleaned > 0)
        {
            log.warn("[DispatchWatchdog] 清理残留呼叫线路占用 holder {} 个", cleaned);
        }
    }
}
