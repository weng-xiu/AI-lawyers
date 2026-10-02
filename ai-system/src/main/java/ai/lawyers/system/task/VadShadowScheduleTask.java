package ai.lawyers.system.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ai.lawyers.system.service.lawyers.voice.vad.SileroVadShadowService;

/**
 * Silero VAD 影子对比日统计定时任务（P2-12 方案 A）。
 *
 * <p>每日 00:05（{@code voice.vad-shadow-report-cron} 可调）触发影子服务输出
 * 结构化日报并 UPSERT 落 ai_vad_shadow_daily。</p>
 *
 * <p><b>不走 RedisLeaderLock 单主竞选</b>：帧级统计为本实例内存计数器，
 * 每个实例都必须落自己的日报；多实例按同一 stat_date 原子累加（见 UPSERT 语义），
 * 率值由累加后计数在 SQL 侧重算。影子开关关闭时服务内部直接跳过。</p>
 *
 * @author ai-lawyers
 */
@Component
public class VadShadowScheduleTask
{
    private static final Logger log = LoggerFactory.getLogger(VadShadowScheduleTask.class);

    @Autowired
    private SileroVadShadowService vadShadowService;

    @Scheduled(cron = "${voice.vad-shadow-report-cron:0 5 0 * * ?}")
    public void reportDaily()
    {
        try
        {
            vadShadowService.dailyReport();
        }
        catch (Exception e)
        {
            // 影子链路异常不影响次日调度与语音主链路
            log.error("[VAD-SHADOW] 日统计输出异常: {}", e.getMessage(), e);
        }
    }
}
