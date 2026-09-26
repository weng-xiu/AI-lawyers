package ai.lawyers.web.controller.lawyers.trunk;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.trunk.AiTrunkAlarm;
import ai.lawyers.system.domain.lawyers.trunk.AiTrunkMetric;
import ai.lawyers.system.mapper.lawyers.trunk.AiTrunkAlarmMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiTrunkMetricMapper;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;
import ai.lawyers.system.service.lawyers.trunk.ITrunkMonitorService;

/**
 * 线路质量监控与告警 Controller
 */
@RestController
@RequestMapping("/lawyers/trunk/monitor")
public class TrunkMonitorController extends BaseController
{
    @Autowired
    private ITrunkMonitorService trunkMonitorService;

    @Autowired
    private ICallDispatchService callDispatchService;

    @Autowired
    private AiTrunkMetricMapper trunkMetricMapper;

    @Autowired
    private AiTrunkAlarmMapper trunkAlarmMapper;

    @Autowired
    private RedisLeaderLock redisLeaderLock;

    /**
     * 监控大屏总览：今日呼叫量、接通率、线路健康、并发使用率、告警数
     */
    @PreAuthorize("@ss.hasPermi('lawyers:trunk:monitor')")
    @GetMapping("/overview")
    public AjaxResult overview()
    {
        Map<String, Object> data = trunkMonitorService.todayOverview();
        data.put("queueSize", callDispatchService.getQueueSize());
        data.put("globalConcurrent", callDispatchService.getGlobalConcurrent());
        // P3-C2：全集群去重在线坐席数（单机模式返回本机数）
        data.put("wsOnlineCount", ai.lawyers.framework.websocket.CallWebSocketServer.globalOnlineCount());
        // P3-C3：当前正在执行的定时任务锁快照（任务名/持有者实例/剩余TTL），无锁时为空列表
        data.put("scheduledLocks", redisLeaderLock.scanJobLocks());
        return success(data);
    }

    /**
     * 实时线路状态列表
     */
    @PreAuthorize("@ss.hasPermi('lawyers:trunk:monitor')")
    @GetMapping("/trunkStatus")
    public AjaxResult trunkStatus()
    {
        return success(trunkMonitorService.realtimeTrunkStatus());
    }

    /**
     * 按运营商维度的实时统计
     */
    @PreAuthorize("@ss.hasPermi('lawyers:trunk:monitor')")
    @GetMapping("/carrierStat")
    public AjaxResult carrierStat()
    {
        return success(trunkMonitorService.realtimeCarrierStat());
    }

    /**
     * 线路质量趋势（分钟级）
     */
    @PreAuthorize("@ss.hasPermi('lawyers:trunk:monitor')")
    @GetMapping("/metricList")
    public TableDataInfo metricList(AiTrunkMetric aiTrunkMetric)
    {
        startPage();
        List<AiTrunkMetric> list = trunkMetricMapper.selectAiTrunkMetricList(aiTrunkMetric);
        return getDataTable(list);
    }

    /**
     * 单条线路最近 N 个统计点
     */
    @PreAuthorize("@ss.hasPermi('lawyers:trunk:monitor')")
    @GetMapping("/trend")
    public AjaxResult trend(Long trunkId, String dimension, Integer limit)
    {
        String dim = dimension == null || dimension.isEmpty() ? "1" : dimension;
        int size = limit == null || limit <= 0 ? 60 : Math.min(limit, 500);
        return success(trunkMetricMapper.selectRecentByTrunk(trunkId, dim, size));
    }

    // ---------------------------------------------------------------- 告警

    @PreAuthorize("@ss.hasPermi('lawyers:trunk:monitor')")
    @GetMapping("/alarm/list")
    public TableDataInfo alarmList(AiTrunkAlarm aiTrunkAlarm)
    {
        startPage();
        List<AiTrunkAlarm> list = trunkAlarmMapper.selectAiTrunkAlarmList(aiTrunkAlarm);
        return getDataTable(list);
    }

    /**
     * 未处理告警（用于顶部红点与大屏滚动）
     */
    @PreAuthorize("@ss.hasPermi('lawyers:trunk:monitor')")
    @GetMapping("/alarm/active")
    public AjaxResult activeAlarms()
    {
        return success(trunkMonitorService.activeAlarms());
    }

    /**
     * 处理告警：确认 / 忽略 / 标记恢复
     */
    @PreAuthorize("@ss.hasPermi('lawyers:trunk:alarm')")
    @Log(title = "线路告警处理", businessType = BusinessType.UPDATE)
    @PostMapping("/alarm/handle")
    public AjaxResult handleAlarm(@RequestBody Map<String, Object> param)
    {
        Long alarmId = param.get("alarmId") == null ? null : Long.valueOf(param.get("alarmId").toString());
        String status = param.get("alarmStatus") == null ? "1" : param.get("alarmStatus").toString();
        String remark = param.get("handleRemark") == null ? "" : param.get("handleRemark").toString();
        if (alarmId == null)
        {
            return AjaxResult.error("告警ID不能为空");
        }
        return toAjax(trunkMonitorService.handleAlarm(alarmId, status, SecurityUtils.getUsername(), remark));
    }

    /**
     * 手动触发一次全量健康探测
     */
    @PreAuthorize("@ss.hasPermi('lawyers:trunk:test')")
    @Log(title = "线路健康探测", businessType = BusinessType.OTHER)
    @PostMapping("/healthCheck")
    public AjaxResult healthCheck()
    {
        trunkMonitorService.healthCheckAll();
        return success("健康探测已执行");
    }
}
