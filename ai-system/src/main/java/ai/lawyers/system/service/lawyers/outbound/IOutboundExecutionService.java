package ai.lawyers.system.service.lawyers.outbound;

import java.util.Map;

/**
 * 智能外呼执行引擎。
 *
 * 融合方案 3.5.3：外呼结果自动生成通话记录、可触发工单创建、任务支持回访联动。
 * 执行链路：任务 -> 号码 -> 呼叫调度器 -> 接通后进入 IVR 流程 -> 生成话单/外呼结果/统计。
 */
public interface IOutboundExecutionService
{
    /**
     * 执行单个外呼任务的一批号码（批量大小取任务最大并发数）。
     *
     * @return 本批处理的号码数量
     */
    int executeTask(Long taskId);

    /**
     * 扫描并执行所有运行中的任务（由定时器驱动）。
     */
    void scanRunningTasks();

    /**
     * 网关事件回写（ANSWERED / HANGUP / FAILED），用于真实网关场景下
     * 最终化号码状态、生成话单与外呼结果。
     */
    void onCallEvent(String callUuid, String eventType, Map<String, Object> params);
}
