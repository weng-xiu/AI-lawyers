package ai.lawyers.system.task;

import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.trunk.AiCallDialLog;
import ai.lawyers.system.enums.DialStatusEnum;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;
import ai.lawyers.system.service.lawyers.trunk.gateway.esl.EslEventBridgeService;

/**
 * P3-B4：PBX/DB 五分钟对账任务。
 *
 * <p>背景：PBX 事件可能因网络闪断、ESL 重连窗口、PBX 侧异常而丢失，
 * 导致 {@code ai_call_dial_log} 长期卡在非终态（拨号中/振铃/已接通），
 * 线路并发与全局并发占用永不释放，最终拖垮外呼能力。</p>
 *
 * <p>每 5 分钟执行（集群单主，{@link RedisLeaderLock}）：</p>
 * <ol>
 *   <li><b>卡死矫正</b>：非终态超过 {@code call.reconcile.stale-minutes}（默认 10 分钟）
 *       的拨号日志按 FAILED 终态收敛——有 callUuid 的走标准事件通道
 *       {@link ICallDispatchService#onCallEvent}（复用 finishCall 释放并发与指标联动），
 *       无 callUuid 的直接置失败终态；</li>
 *   <li><b>通道数对账</b>：仅 ESL 消费者实例执行——汇总 FreeSWITCH 各节点
 *       {@code api show channels count} 与 DB 在途数比对，偏差绝对值超过
 *       {@code call.reconcile.diff-threshold}（默认 5）时 WARN 告警，
 *       提示运维核查事件丢失或并发漂移（数量级偏差无法安全自动修复，先可观测）。</li>
 * </ol>
 *
 * <p>说明：DB 在途数只含外呼拨号日志，PBX 通道数含入站腿，
 * 正常情形下 PBX ≥ DB；对账关注"偏差超阈值"的异常漂移而非精确相等。
 * ESL 全部不可达时跳过数量对账（返回 -1），卡死矫正不受影响仍执行。</p>
 *
 * @author ai-lawyers
 */
@Component
public class PbxReconcileTask
{
    private static final Logger log = LoggerFactory.getLogger(PbxReconcileTask.class);

    /** 集群单主锁（TTL 小于执行周期，防止崩溃后下一轮无法执行） */
    private static final String LOCK_NAME = "job:pbx-reconcile";
    private static final Duration LOCK_TTL = Duration.ofMinutes(4);

    /** 总开关：默认开启；应急可关（call.reconcile.enabled=false） */
    @Value("${call.reconcile.enabled:true}")
    private boolean enabled;

    /** DB/PBX 在途数偏差告警阈值（绝对值） */
    @Value("${call.reconcile.diff-threshold:5}")
    private int diffThreshold;

    /** 非终态超过该分钟数判定为卡死（须大于正常通话最长时长） */
    @Value("${call.reconcile.stale-minutes:10}")
    private int staleMinutes;

    /** 单轮矫正上限（防大批量矫正冲击在线业务，剩余下轮继续） */
    @Value("${call.reconcile.stale-limit:50}")
    private int staleLimit;

    @Autowired
    private RedisLeaderLock leaderLock;

    @Autowired
    private AiCallDialLogMapper dialLogMapper;

    @Autowired
    private ICallDispatchService callDispatchService;

    @Autowired(required = false)
    private EslEventBridgeService eslBridge;

    /**
     * 每 5 分钟执行（错开整分钟，避开其它定时任务集中触发）。
     */
    @Scheduled(cron = "${call.reconcile.cron:0 20 */5 * * ?}")
    public void reconcile()
    {
        if (!enabled)
        {
            return;
        }
        leaderLock.tryRun(LOCK_NAME, LOCK_TTL, this::doReconcile);
    }

    /** 执行一轮对账（包级可见便于单测） */
    void doReconcile()
    {
        correctStaleDials();
        reconcileChannelCount();
    }

    /**
     * 卡死矫正：非终态超时的拨号日志按 FAILED 收敛，释放并发占用。
     * 单条失败不影响其余记录矫正。
     */
    void correctStaleDials()
    {
        Date cutoff = new Date(System.currentTimeMillis() - staleMinutes * 60_000L);
        List<AiCallDialLog> stale;
        try
        {
            stale = dialLogMapper.selectStaleActiveDials(cutoff, staleLimit);
        }
        catch (Exception e)
        {
            log.warn("[PBX对账] 卡死扫描失败: {}", e.getMessage());
            return;
        }
        if (stale == null || stale.isEmpty())
        {
            return;
        }
        int corrected = 0;
        for (AiCallDialLog d : stale)
        {
            try
            {
                if (StringUtils.isNotEmpty(d.getCallUuid()))
                {
                    // 走标准事件通道：复用终态落库 + finishCall 并发释放 + 指标联动
                    Map<String, Object> params = new HashMap<>();
                    params.put("failReason", "PBX事件丢失，对账任务矫正");
                    params.put("hangupCause", "RECONCILE_STALE");
                    callDispatchService.onCallEvent(d.getCallUuid(), "FAILED", params);
                }
                else
                {
                    // 未回写 callUuid（下发后异常）：无法走事件通道，直接置失败终态；
                    // 其并发占用由启动时 resetAllConcurrent/resetGlobalConcurrent 兜底
                    AiCallDialLog update = new AiCallDialLog();
                    update.setLogId(d.getLogId());
                    update.setDialStatus(DialStatusEnum.FAILED.getCode());
                    update.setFailReason("PBX事件丢失且未回写callUuid，对账任务矫正");
                    update.setHangupTime(new Date());
                    dialLogMapper.updateAiCallDialLog(update);
                }
                corrected++;
            }
            catch (Exception e)
            {
                log.warn("[PBX对账] 矫正失败 logId={} uuid={} err={}",
                        d.getLogId(), d.getCallUuid(), e.getMessage());
            }
        }
        log.warn("[PBX对账] 卡死矫正完成：扫描到 {} 条非终态超时记录，已矫正 {} 条",
                stale.size(), corrected);
    }

    /**
     * 通道数对账：PBX 实际通道数 vs DB 在途数，偏差超阈值 WARN 告警。
     * 仅 ESL 消费者（leader）执行——ESL 连接在消费者实例上。
     */
    void reconcileChannelCount()
    {
        if (eslBridge == null || !eslBridge.isEslLeader())
        {
            return;
        }
        int pbx = eslBridge.queryPbxChannelCount();
        if (pbx < 0)
        {
            log.info("[PBX对账] ESL 无可用连接，跳过本轮通道数对账");
            return;
        }
        int db = dialLogMapper.countActiveDials();
        int diff = db - pbx;
        if (Math.abs(diff) > diffThreshold)
        {
            log.warn("[PBX对账] 在途通话 DB/PBX 偏差超阈值: dbActive={} pbxChannels={} diff={} threshold={}，"
                    + "可能存在事件丢失或并发计数漂移，请核查", db, pbx, diff, diffThreshold);
        }
        else
        {
            log.info("[PBX对账] 在途通话对账正常: dbActive={} pbxChannels={}", db, pbx);
        }
    }
}
