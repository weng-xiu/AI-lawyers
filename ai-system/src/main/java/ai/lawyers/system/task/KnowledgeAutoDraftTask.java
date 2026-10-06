package ai.lawyers.system.task;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ai.lawyers.system.mapper.SysUserMapper;
import ai.lawyers.system.service.lawyers.KnowledgeDraftService;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;
import ai.lawyers.system.service.lawyers.queue.MessageNotifyDispatcher;

/**
 * 知识库自动沉淀定时任务（P1-10）。
 *
 * <p>每日凌晨扫描近 N 天高分质检通话（默认质检分≥90），经 LLM 批量沉淀为
 * <b>待审核</b>知识草稿；多实例经 {@link RedisLeaderLock} 保证单实例执行。
 * 新生成草稿后给管理员角色发站内信提醒，模型不直接发布。</p>
 *
 * @author ai-lawyers
 */
@Component
public class KnowledgeAutoDraftTask
{
    private static final Logger log = LoggerFactory.getLogger(KnowledgeAutoDraftTask.class);

    /** 集群单主锁名（TTL 10 分钟，大于单轮扫描最坏耗时） */
    private static final String LOCK_NAME = "job:knowledge-auto-draft";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    @Autowired
    private KnowledgeDraftService knowledgeDraftService;

    @Autowired
    private RedisLeaderLock leaderLock;

    @Autowired
    private MessageNotifyDispatcher messageNotifyDispatcher;

    @Autowired
    private SysUserMapper sysUserMapper;

    /** 总开关（ai.knowledge.auto-draft-enabled，关闭后任务空转） */
    @Value("${ai.knowledge.auto-draft-enabled:true}")
    private boolean enabled;

    /** 回溯天数 */
    @Value("${ai.knowledge.auto-draft-days:7}")
    private int days;

    /** 最低质检分 */
    @Value("${ai.knowledge.auto-draft-min-score:90}")
    private double minScore;

    /** 单轮最多生成条数 */
    @Value("${ai.knowledge.auto-draft-limit:20}")
    private int limit;

    /** 每日 03:17 执行（分钟位错开统计/补传任务） */
    @Scheduled(cron = "${ai.knowledge.auto-draft-cron:0 17 3 * * ?}")
    public void scan()
    {
        if (!enabled)
        {
            return;
        }
        leaderLock.tryRun(LOCK_NAME, LOCK_TTL, () -> {
            try
            {
                int created = knowledgeDraftService.scanQualityCalls(
                        days, BigDecimal.valueOf(minScore), limit);
                log.info("[KnowledgeAutoDraft] 自动沉淀扫描完成，新生成待审草稿 {} 条", created);
                if (created > 0)
                {
                    notifyAdmins(created);
                }
            }
            catch (Exception ex)
            {
                // 单轮失败不影响下一轮
                log.error("[KnowledgeAutoDraft] 自动沉淀异常: {}", ex.getMessage(), ex);
            }
        });
    }

    /** 给管理员角色发新增草稿站内信提醒 */
    private void notifyAdmins(int created)
    {
        String title = "知识库自动沉淀：新增待审草稿 " + created + " 条";
        String content = "系统已从近 " + days + " 天高分质检通话（≥" + (int) minScore
                + " 分）自动沉淀 " + created + " 条知识草稿，请前往知识审核页面核验事实与法条后发布。";
        try
        {
            List<Long> adminIds = sysUserMapper.selectUserIdsByRoleKey("admin");
            if (adminIds != null)
            {
                for (Long uid : adminIds)
                {
                    messageNotifyDispatcher.notify(uid, "1", title, content,
                            "knowledge", null, "system");
                }
            }
        }
        catch (Exception e)
        {
            log.warn("[KnowledgeAutoDraft] 草稿提醒发送失败: {}", e.getMessage());
        }
    }
}
