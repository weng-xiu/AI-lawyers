package ai.lawyers.system.service.impl.lawyers.trunk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.domain.lawyers.trunk.AiTrunkAlarm;
import ai.lawyers.system.domain.lawyers.trunk.AiTrunkMetric;
import ai.lawyers.system.enums.CarrierEnum;
import ai.lawyers.system.enums.TrunkHealthEnum;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallTrunkMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiTrunkAlarmMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiTrunkMetricMapper;
import ai.lawyers.system.service.lawyers.trunk.ITrunkMonitorService;
import ai.lawyers.system.service.lawyers.trunk.gateway.CallGatewayFactory;
import ai.lawyers.system.service.lawyers.trunk.gateway.GatewayHealth;
import ai.lawyers.system.service.lawyers.trunk.gateway.ICallGatewayAdapter;

/**
 * 线路监控与告警服务实现
 *
 * 健康状态机：
 *   探测成功            -> NORMAL（并关闭 TRUNK_DOWN 告警）
 *   探测失败            -> FAULT（累计失败次数）
 *   连续失败 >= 阈值    -> CIRCUIT_OPEN 熔断，摘除出选路池并告警
 *   熔断冷却期结束+探通  -> NORMAL 自动恢复
 *   接通率低于阈值      -> DEGRADED（仍参与选路但优先级降低）并告警
 */
@Service
public class TrunkMonitorServiceImpl implements ITrunkMonitorService
{
    private static final Logger log = LoggerFactory.getLogger(TrunkMonitorServiceImpl.class);

    /** 告警类型常量 */
    private static final String ALARM_TRUNK_DOWN = "TRUNK_DOWN";

    private static final String ALARM_CIRCUIT_OPEN = "CIRCUIT_OPEN";

    private static final String ALARM_CONNECT_RATE_LOW = "CONNECT_RATE_LOW";

    private static final String ALARM_CONCURRENT_FULL = "CONCURRENT_FULL";

    private static final String ALARM_MOS_LOW = "MOS_LOW";

    private static final String ALARM_QUEUE_OVERFLOW = "QUEUE_OVERFLOW";

    private static final String ALARM_NO_TRUNK = "NO_AVAILABLE_TRUNK";

    @Autowired
    private AiCallTrunkMapper trunkMapper;

    @Autowired
    private AiCallDialLogMapper dialLogMapper;

    @Autowired
    private AiTrunkMetricMapper metricMapper;

    @Autowired
    private AiTrunkAlarmMapper alarmMapper;

    @Autowired
    private CallGatewayFactory gatewayFactory;

    /** 熔断冷却时长(秒) */
    @Value("${call.monitor.circuitCooldownSeconds:120}")
    private int circuitCooldownSeconds;

    /** 告警抑制窗口(分钟)，同线路同类型告警在窗口内只产生一条 */
    @Value("${call.monitor.alarmSuppressMinutes:10}")
    private int alarmSuppressMinutes;

    /** 质量评估最小样本量，样本过少不做接通率告警，避免误报 */
    @Value("${call.monitor.minSampleForAlarm:20}")
    private int minSampleForAlarm;

    /** 并发使用率告警阈值(%) */
    @Value("${call.monitor.concurrentPercentThreshold:90}")
    private int concurrentAlarmThreshold;

    /** MOS 告警阈值 */
    @Value("${call.monitor.mosThreshold:3.0}")
    private double mosThreshold;

    // ------------------------------------------------------------------ 健康探测

    @Override
    public void healthCheckAll()
    {
        List<AiCallTrunk> trunks = trunkMapper.selectAllEnabledTrunks();
        if (trunks == null || trunks.isEmpty())
        {
            return;
        }
        for (AiCallTrunk trunk : trunks)
        {
            try
            {
                healthCheck(trunk);
            }
            catch (Exception e)
            {
                log.error("线路 {} 健康探测异常", trunk.getTrunkCode(), e);
            }
        }
    }

    @Override
    public boolean healthCheck(AiCallTrunk trunk)
    {
        // 熔断中的线路交由 recoverCircuitBreakers 处理，此处跳过
        if (TrunkHealthEnum.CIRCUIT_OPEN.getCode().equals(trunk.getHealthStatus()))
        {
            return false;
        }

        ICallGatewayAdapter adapter = gatewayFactory.get(trunk);
        if (adapter == null)
        {
            markFault(trunk, "未找到网关适配器 vendor=" + trunk.getVendor());
            return false;
        }

        GatewayHealth health = adapter.checkHealth(trunk);
        if (health != null && health.isReachable())
        {
            if (!TrunkHealthEnum.NORMAL.getCode().equals(trunk.getHealthStatus())
                    && !TrunkHealthEnum.DEGRADED.getCode().equals(trunk.getHealthStatus()))
            {
                trunkMapper.updateHealthStatus(trunk.getTrunkId(), TrunkHealthEnum.NORMAL.getCode(), 0);
                alarmMapper.recoverAlarms(trunk.getTrunkId(), ALARM_TRUNK_DOWN);
                log.info("线路 {} 探测恢复正常，耗时 {}ms", trunk.getTrunkCode(), health.getLatencyMs());
            }
            else
            {
                trunkMapper.updateHealthStatus(trunk.getTrunkId(), trunk.getHealthStatus(), 0);
            }
            return true;
        }

        String msg = health == null ? "探测无响应" : health.getMessage();
        markFault(trunk, msg);
        return false;
    }

    private void markFault(AiCallTrunk trunk, String message)
    {
        int failCount = (trunk.getFailCount() == null ? 0 : trunk.getFailCount()) + 1;
        int threshold = trunk.getAlarmFailTimes() == null ? 5 : trunk.getAlarmFailTimes();

        if (failCount >= threshold)
        {
            trunkMapper.updateHealthStatus(trunk.getTrunkId(), TrunkHealthEnum.CIRCUIT_OPEN.getCode(), failCount);
            raiseAlarm(trunk, ALARM_CIRCUIT_OPEN, "4",
                    "线路熔断: " + trunk.getTrunkName(),
                    "连续失败 " + failCount + " 次，已自动摘除该线路。原因: " + message,
                    String.valueOf(failCount), String.valueOf(threshold));
            log.error("线路 {} 连续失败 {} 次，触发熔断", trunk.getTrunkCode(), failCount);
        }
        else
        {
            trunkMapper.updateHealthStatus(trunk.getTrunkId(), TrunkHealthEnum.FAULT.getCode(), failCount);
            raiseAlarm(trunk, ALARM_TRUNK_DOWN, "3",
                    "线路探测失败: " + trunk.getTrunkName(),
                    "第 " + failCount + " 次探测失败。原因: " + message,
                    String.valueOf(failCount), String.valueOf(threshold));
            log.warn("线路 {} 探测失败({}/{}): {}", trunk.getTrunkCode(), failCount, threshold, message);
        }
    }

    @Override
    public void onCallSuccess(AiCallTrunk trunk)
    {
        if (trunk == null)
        {
            return;
        }
        // markCallSuccess 已在 SQL 中重置 fail_count 并把 0/3/4 状态提升为 1
        if (!TrunkHealthEnum.NORMAL.getCode().equals(trunk.getHealthStatus()))
        {
            alarmMapper.recoverAlarms(trunk.getTrunkId(), ALARM_TRUNK_DOWN);
            alarmMapper.recoverAlarms(trunk.getTrunkId(), ALARM_CIRCUIT_OPEN);
        }
    }

    @Override
    public void onCallFailed(AiCallTrunk trunk)
    {
        if (trunk == null)
        {
            return;
        }
        AiCallTrunk latest = trunkMapper.selectAiCallTrunkByTrunkId(trunk.getTrunkId());
        if (latest == null)
        {
            return;
        }
        int failCount = latest.getFailCount() == null ? 0 : latest.getFailCount();
        int threshold = latest.getAlarmFailTimes() == null ? 5 : latest.getAlarmFailTimes();
        if (failCount >= threshold
                && !TrunkHealthEnum.CIRCUIT_OPEN.getCode().equals(latest.getHealthStatus()))
        {
            trunkMapper.updateHealthStatus(latest.getTrunkId(), TrunkHealthEnum.CIRCUIT_OPEN.getCode(), failCount);
            raiseAlarm(latest, ALARM_CIRCUIT_OPEN, "4",
                    "线路熔断: " + latest.getTrunkName(),
                    "连续呼叫失败 " + failCount + " 次，已自动摘除该线路并切换备用线路",
                    String.valueOf(failCount), String.valueOf(threshold));
            log.error("线路 {} 连续呼叫失败 {} 次，触发熔断", latest.getTrunkCode(), failCount);
        }
    }

    @Override
    public void recoverCircuitBreakers()
    {
        List<AiCallTrunk> trunks = trunkMapper.selectAllEnabledTrunks();
        if (trunks == null)
        {
            return;
        }
        long now = System.currentTimeMillis();
        for (AiCallTrunk trunk : trunks)
        {
            if (!TrunkHealthEnum.CIRCUIT_OPEN.getCode().equals(trunk.getHealthStatus()))
            {
                continue;
            }
            Date openTime = trunk.getCircuitOpenTime();
            if (openTime != null && now - openTime.getTime() < circuitCooldownSeconds * 1000L)
            {
                continue;
            }
            // 冷却期结束，半开探测
            ICallGatewayAdapter adapter = gatewayFactory.get(trunk);
            GatewayHealth health = adapter == null ? null : adapter.checkHealth(trunk);
            if (health != null && health.isReachable())
            {
                trunkMapper.updateHealthStatus(trunk.getTrunkId(), TrunkHealthEnum.NORMAL.getCode(), 0);
                alarmMapper.recoverAlarms(trunk.getTrunkId(), ALARM_CIRCUIT_OPEN);
                alarmMapper.recoverAlarms(trunk.getTrunkId(), ALARM_TRUNK_DOWN);
                log.info("线路 {} 熔断恢复，重新加入选路池", trunk.getTrunkCode());
            }
            else
            {
                // 探测仍失败，刷新熔断时间继续冷却
                trunkMapper.updateHealthStatus(trunk.getTrunkId(),
                        TrunkHealthEnum.CIRCUIT_OPEN.getCode(), trunk.getFailCount());
                log.debug("线路 {} 熔断恢复探测失败，继续冷却", trunk.getTrunkCode());
            }
        }
    }

    // ------------------------------------------------------------------ 质量统计

    @Override
    public void collectMetrics()
    {
        // 统计上一个完整分钟
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date end = cal.getTime();
        cal.add(Calendar.MINUTE, -1);
        Date begin = cal.getTime();

        List<Map<String, Object>> rows = dialLogMapper.aggregateByTrunk(begin, end);
        if (rows == null || rows.isEmpty())
        {
            return;
        }
        for (Map<String, Object> row : rows)
        {
            Long trunkId = toLong(row.get("trunkId"));
            if (trunkId == null)
            {
                continue;
            }
            AiTrunkMetric metric = new AiTrunkMetric();
            metric.setTrunkId(trunkId);
            metric.setTrunkCode(toStr(row.get("trunkCode")));
            metric.setCarrier(toStr(row.get("carrier")));
            metric.setStatTime(begin);
            metric.setStatDimension("1");
            metric.setTotalCalls(toInt(row.get("totalCalls")));
            metric.setConnectedCalls(toInt(row.get("connectedCalls")));
            metric.setFailedCalls(toInt(row.get("failedCalls")));
            metric.setConnectRate(toDecimal(row.get("connectRate")));
            metric.setAsr(toDecimal(row.get("connectRate")));
            metric.setAcd(toInt(row.get("acd")));
            metric.setTotalDuration(toLong(row.get("totalDuration")));
            metric.setAvgRingDuration(toInt(row.get("avgRingDuration")));
            metric.setAvgMos(toDecimal(row.get("avgMos")));
            metric.setAvgPacketLoss(toDecimal(row.get("avgPacketLoss")));

            AiCallTrunk trunk = trunkMapper.selectAiCallTrunkByTrunkId(trunkId);
            metric.setMaxConcurrent(trunk == null ? 0
                    : (trunk.getCurrentConcurrent() == null ? 0 : trunk.getCurrentConcurrent()));

            metricMapper.saveOrUpdate(metric);

            // 同步刷新线路上的滚动质量指标，供选路与列表展示使用
            AiCallTrunk quality = new AiCallTrunk();
            quality.setTrunkId(trunkId);
            quality.setConnectRate(metric.getConnectRate());
            quality.setAsr(metric.getAsr());
            quality.setAcd(metric.getAcd());
            quality.setMos(metric.getAvgMos());
            trunkMapper.updateQualityMetric(quality);
        }
        log.debug("线路质量统计完成，共 {} 条线路，统计时间点 {}", rows.size(), begin);
    }

    @Override
    public void evaluateQualityAlarm()
    {
        List<AiCallTrunk> trunks = trunkMapper.selectAllEnabledTrunks();
        if (trunks == null)
        {
            return;
        }
        // 近 15 分钟窗口
        Calendar cal = Calendar.getInstance();
        Date end = cal.getTime();
        cal.add(Calendar.MINUTE, -15);
        Date begin = cal.getTime();

        for (AiCallTrunk trunk : trunks)
        {
            try
            {
                evaluateOne(trunk, begin, end);
            }
            catch (Exception e)
            {
                log.error("线路 {} 质量评估异常", trunk.getTrunkCode(), e);
            }
        }
    }

    private void evaluateOne(AiCallTrunk trunk, Date begin, Date end)
    {
        // 1) 并发使用率
        int max = trunk.getMaxConcurrent() == null ? 0 : trunk.getMaxConcurrent();
        int cur = trunk.getCurrentConcurrent() == null ? 0 : trunk.getCurrentConcurrent();
        if (max > 0)
        {
            int usage = cur * 100 / max;
            if (usage >= concurrentAlarmThreshold)
            {
                raiseAlarm(trunk, ALARM_CONCURRENT_FULL, "2",
                        "线路并发接近上限: " + trunk.getTrunkName(),
                        "当前并发 " + cur + "/" + max + "，使用率 " + usage + "%",
                        usage + "%", concurrentAlarmThreshold + "%");
            }
            else
            {
                alarmMapper.recoverAlarms(trunk.getTrunkId(), ALARM_CONCURRENT_FULL);
            }
        }

        // 2) 接通率与 MOS
        Map<String, Object> stat = dialLogMapper.aggregateOneTrunk(trunk.getTrunkId(), begin, end);
        if (stat == null)
        {
            return;
        }
        int total = toInt(stat.get("totalCalls"));
        if (total < minSampleForAlarm)
        {
            return;
        }

        BigDecimal rate = toDecimal(stat.get("connectRate"));
        BigDecimal threshold = trunk.getAlarmConnectRate() == null
                ? new BigDecimal("60.00") : trunk.getAlarmConnectRate();
        if (rate.compareTo(threshold) < 0)
        {
            // 接通率过低 -> 降级为亚健康
            if (TrunkHealthEnum.NORMAL.getCode().equals(trunk.getHealthStatus()))
            {
                trunkMapper.updateHealthStatus(trunk.getTrunkId(),
                        TrunkHealthEnum.DEGRADED.getCode(), trunk.getFailCount());
            }
            raiseAlarm(trunk, ALARM_CONNECT_RATE_LOW, "3",
                    "线路接通率过低: " + trunk.getTrunkName(),
                    "近15分钟接通率 " + rate + "%，样本 " + total + " 通，低于阈值 " + threshold + "%",
                    rate + "%", threshold + "%");
        }
        else
        {
            alarmMapper.recoverAlarms(trunk.getTrunkId(), ALARM_CONNECT_RATE_LOW);
            if (TrunkHealthEnum.DEGRADED.getCode().equals(trunk.getHealthStatus()))
            {
                trunkMapper.updateHealthStatus(trunk.getTrunkId(), TrunkHealthEnum.NORMAL.getCode(), 0);
            }
        }

        BigDecimal mos = toDecimal(stat.get("avgMos"));
        if (mos.doubleValue() > 0 && mos.doubleValue() < mosThreshold)
        {
            raiseAlarm(trunk, ALARM_MOS_LOW, "2",
                    "线路语音质量下降: " + trunk.getTrunkName(),
                    "近15分钟平均 MOS " + mos + "，低于阈值 " + mosThreshold,
                    mos.toString(), String.valueOf(mosThreshold));
        }
        else
        {
            alarmMapper.recoverAlarms(trunk.getTrunkId(), ALARM_MOS_LOW);
        }
    }

    // ------------------------------------------------------------------ 告警

    @Override
    public void raiseNoTrunkAlarm(String carrier)
    {
        AiTrunkAlarm alarm = new AiTrunkAlarm();
        alarm.setCarrier(carrier);
        alarm.setAlarmType(ALARM_NO_TRUNK);
        alarm.setAlarmLevel("4");
        alarm.setAlarmTitle("无可用线路: " + CarrierEnum.infoOf(carrier));
        alarm.setAlarmContent("运营商 " + CarrierEnum.infoOf(carrier) + " 下没有任何可用线路，外呼已被拒绝");
        alarm.setAlarmStatus("0");
        alarm.setAlarmTime(new Date());
        insertIfNotSuppressed(null, ALARM_NO_TRUNK, alarm);
    }

    @Override
    public void raiseQueueOverflowAlarm(int queueSize, int capacity)
    {
        AiTrunkAlarm alarm = new AiTrunkAlarm();
        alarm.setAlarmType(ALARM_QUEUE_OVERFLOW);
        alarm.setAlarmLevel("3");
        alarm.setAlarmTitle("呼叫队列溢出");
        alarm.setAlarmContent("排队呼叫数 " + queueSize + " 已达容量上限 " + capacity + "，新呼叫被拒绝");
        alarm.setMetricValue(String.valueOf(queueSize));
        alarm.setThresholdValue(String.valueOf(capacity));
        alarm.setAlarmStatus("0");
        alarm.setAlarmTime(new Date());
        insertIfNotSuppressed(null, ALARM_QUEUE_OVERFLOW, alarm);
    }

    private void raiseAlarm(AiCallTrunk trunk, String type, String level, String title,
                            String content, String metricValue, String thresholdValue)
    {
        AiTrunkAlarm alarm = new AiTrunkAlarm();
        alarm.setTrunkId(trunk.getTrunkId());
        alarm.setTrunkCode(trunk.getTrunkCode());
        alarm.setCarrier(trunk.getCarrier());
        alarm.setAlarmType(type);
        alarm.setAlarmLevel(level);
        alarm.setAlarmTitle(title);
        alarm.setAlarmContent(content);
        alarm.setMetricValue(metricValue);
        alarm.setThresholdValue(thresholdValue);
        alarm.setAlarmStatus("0");
        alarm.setAlarmTime(new Date());
        insertIfNotSuppressed(trunk.getTrunkId(), type, alarm);
    }

    /** 告警收敛：抑制窗口内同线路同类型只保留一条 */
    private void insertIfNotSuppressed(Long trunkId, String type, AiTrunkAlarm alarm)
    {
        try
        {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, -alarmSuppressMinutes);
            AiTrunkAlarm exist = alarmMapper.selectActiveSameAlarm(trunkId, type, cal.getTime());
            if (exist != null)
            {
                return;
            }
            alarmMapper.insertAiTrunkAlarm(alarm);
            log.warn("产生线路告警 [{}] {} - {}", type, alarm.getAlarmTitle(), alarm.getAlarmContent());
        }
        catch (Exception e)
        {
            log.error("写入告警失败", e);
        }
    }

    @Override
    public List<AiTrunkAlarm> activeAlarms()
    {
        return alarmMapper.selectActiveAlarms();
    }

    @Override
    public int handleAlarm(Long alarmId, String status, String handleBy, String remark)
    {
        AiTrunkAlarm alarm = new AiTrunkAlarm();
        alarm.setAlarmId(alarmId);
        alarm.setAlarmStatus(status);
        alarm.setHandleBy(handleBy);
        alarm.setHandleTime(new Date());
        alarm.setHandleRemark(remark);
        if ("2".equals(status))
        {
            alarm.setRecoverTime(new Date());
        }
        return alarmMapper.updateAiTrunkAlarm(alarm);
    }

    // ------------------------------------------------------------------ 实时统计

    @Override
    public List<Map<String, Object>> realtimeTrunkStatus()
    {
        List<AiCallTrunk> trunks = trunkMapper.selectAllEnabledTrunks();
        List<Map<String, Object>> result = new ArrayList<>();
        if (trunks == null)
        {
            return result;
        }
        for (AiCallTrunk trunk : trunks)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("trunkId", trunk.getTrunkId());
            item.put("trunkCode", trunk.getTrunkCode());
            item.put("trunkName", trunk.getTrunkName());
            item.put("carrier", trunk.getCarrier());
            item.put("carrierName", CarrierEnum.infoOf(trunk.getCarrier()));
            item.put("lineType", trunk.getLineType());
            item.put("vendor", trunk.getVendor());
            item.put("healthStatus", trunk.getHealthStatus());
            item.put("healthName", TrunkHealthEnum.infoOf(trunk.getHealthStatus()));
            item.put("isBackup", trunk.getIsBackup());

            int max = trunk.getMaxConcurrent() == null ? 0 : trunk.getMaxConcurrent();
            int cur = trunk.getCurrentConcurrent() == null ? 0 : trunk.getCurrentConcurrent();
            item.put("maxConcurrent", max);
            item.put("currentConcurrent", cur);
            item.put("concurrentUsage", max > 0 ? cur * 100 / max : 0);

            item.put("connectRate", trunk.getConnectRate());
            item.put("acd", trunk.getAcd());
            item.put("mos", trunk.getMos());
            item.put("totalCalls", trunk.getTotalCalls());
            item.put("successCalls", trunk.getSuccessCalls());
            item.put("failCount", trunk.getFailCount());
            item.put("lastCheckTime", trunk.getLastCheckTime());
            item.put("lastSuccessTime", trunk.getLastSuccessTime());
            result.add(item);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> realtimeCarrierStat()
    {
        Calendar cal = Calendar.getInstance();
        Date end = cal.getTime();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date begin = cal.getTime();

        // 线路容量维度
        List<Map<String, Object>> overview = trunkMapper.selectCarrierOverview();
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        if (overview != null)
        {
            for (Map<String, Object> row : overview)
            {
                String carrier = toStr(row.get("carrier"));
                Map<String, Object> item = new LinkedHashMap<>(row);
                item.put("carrierName", CarrierEnum.infoOf(carrier));
                merged.put(carrier, item);
            }
        }

        // 今日通话质量维度
        List<Map<String, Object>> callStat = dialLogMapper.aggregateByCarrier(begin, end);
        if (callStat != null)
        {
            for (Map<String, Object> row : callStat)
            {
                String carrier = toStr(row.get("carrier"));
                Map<String, Object> item = merged.get(carrier);
                if (item == null)
                {
                    item = new LinkedHashMap<>();
                    item.put("carrier", carrier);
                    item.put("carrierName", CarrierEnum.infoOf(carrier));
                    merged.put(carrier, item);
                }
                item.put("todayTotalCalls", row.get("totalCalls"));
                item.put("todayConnectedCalls", row.get("connectedCalls"));
                item.put("todayConnectRate", row.get("connectRate"));
                item.put("todayTotalDuration", row.get("totalDuration"));
                item.put("todayAcd", row.get("acd"));
            }
        }
        return new ArrayList<>(merged.values());
    }

    @Override
    public Map<String, Object> todayOverview()
    {
        Map<String, Object> overview = dialLogMapper.selectTodayOverview();
        Map<String, Object> result = overview == null ? new HashMap<>() : new HashMap<>(overview);

        List<AiCallTrunk> trunks = trunkMapper.selectAllEnabledTrunks();
        int total = 0;
        int normal = 0;
        int fault = 0;
        int maxConcurrent = 0;
        int curConcurrent = 0;
        if (trunks != null)
        {
            total = trunks.size();
            for (AiCallTrunk t : trunks)
            {
                if (TrunkHealthEnum.NORMAL.getCode().equals(t.getHealthStatus()))
                {
                    normal++;
                }
                else if (TrunkHealthEnum.FAULT.getCode().equals(t.getHealthStatus())
                        || TrunkHealthEnum.CIRCUIT_OPEN.getCode().equals(t.getHealthStatus()))
                {
                    fault++;
                }
                maxConcurrent += t.getMaxConcurrent() == null ? 0 : t.getMaxConcurrent();
                curConcurrent += t.getCurrentConcurrent() == null ? 0 : t.getCurrentConcurrent();
            }
        }
        result.put("trunkTotal", total);
        result.put("trunkNormal", normal);
        result.put("trunkFault", fault);
        result.put("maxConcurrent", maxConcurrent);
        result.put("currentConcurrent", curConcurrent);
        result.put("concurrentUsage", maxConcurrent > 0 ? curConcurrent * 100 / maxConcurrent : 0);

        List<AiTrunkAlarm> alarms = alarmMapper.selectActiveAlarms();
        result.put("activeAlarmCount", alarms == null ? 0 : alarms.size());
        return result;
    }

    // ------------------------------------------------------------------ 类型转换

    private String toStr(Object v)
    {
        return v == null ? "" : v.toString();
    }

    private Integer toInt(Object v)
    {
        if (v == null)
        {
            return 0;
        }
        if (v instanceof Number)
        {
            return ((Number) v).intValue();
        }
        try
        {
            return Integer.valueOf(v.toString());
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    private Long toLong(Object v)
    {
        if (v == null)
        {
            return null;
        }
        if (v instanceof Number)
        {
            return ((Number) v).longValue();
        }
        try
        {
            return Long.valueOf(v.toString());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private BigDecimal toDecimal(Object v)
    {
        if (v == null)
        {
            return BigDecimal.ZERO;
        }
        try
        {
            return new BigDecimal(v.toString()).setScale(2, RoundingMode.HALF_UP);
        }
        catch (NumberFormatException e)
        {
            return BigDecimal.ZERO;
        }
    }
}
