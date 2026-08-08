package ai.lawyers.system.service.lawyers.trunk;

import java.util.List;
import java.util.Map;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.domain.lawyers.trunk.AiTrunkAlarm;

/**
 * 线路状态监控、质量统计与告警服务
 */
public interface ITrunkMonitorService
{
    /**
     * 对全部启用线路执行健康探测，并驱动健康状态机。
     * 由定时任务按固定频率调用。
     */
    void healthCheckAll();

    /**
     * 探测单条线路。
     *
     * @return 是否可用
     */
    boolean healthCheck(AiCallTrunk trunk);

    /**
     * 呼叫成功回调：重置失败计数，必要时恢复健康状态并关闭告警。
     */
    void onCallSuccess(AiCallTrunk trunk);

    /**
     * 呼叫失败回调：累计失败次数，达到阈值时熔断并告警。
     */
    void onCallFailed(AiCallTrunk trunk);

    /**
     * 熔断恢复检查：冷却期结束后探测并尝试恢复线路。
     */
    void recoverCircuitBreakers();

    /**
     * 聚合上一分钟的拨号日志，生成线路质量统计并刷新线路滚动指标。
     */
    void collectMetrics();

    /**
     * 基于滚动质量指标进行阈值告警判定（接通率、MOS、并发使用率）。
     */
    void evaluateQualityAlarm();

    /** 无可用线路告警 */
    void raiseNoTrunkAlarm(String carrier);

    /** 排队溢出告警 */
    void raiseQueueOverflowAlarm(int queueSize, int capacity);

    /** 实时线路状态列表（含并发使用率、质量指标） */
    List<Map<String, Object>> realtimeTrunkStatus();

    /** 按运营商维度的实时统计 */
    List<Map<String, Object>> realtimeCarrierStat();

    /** 今日总体概览 */
    Map<String, Object> todayOverview();

    /** 未处理告警列表 */
    List<AiTrunkAlarm> activeAlarms();

    /** 处理告警 */
    int handleAlarm(Long alarmId, String status, String handleBy, String remark);
}
