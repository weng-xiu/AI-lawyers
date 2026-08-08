package ai.lawyers.system.service.lawyers.trunk;

import java.util.Map;
import ai.lawyers.system.domain.lawyers.trunk.DialRequest;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;

/**
 * 呼叫调度服务
 *
 * 外呼统一入口，职责：
 *   1. 号码校验与运营商识别；
 *   2. 线路选择（同运营商优先）；
 *   3. 并发控制（线路级 + 全局级）与排队；
 *   4. 网关下发与故障自动切换；
 *   5. 拨号日志落库与线路指标更新。
 */
public interface ICallDispatchService
{
    /**
     * 同步发起外呼：无可用线路时立即返回失败。
     */
    DialResult dial(DialRequest request);

    /**
     * 发起外呼，线路满载时进入排队队列，由调度线程择机重试。
     *
     * @return 立即返回排队中的结果（dialStatus=0），实际结果通过日志与事件回调更新
     */
    DialResult dialWithQueue(DialRequest request);

    /**
     * 挂断呼叫。
     */
    boolean hangup(String callUuid);

    /**
     * 网关事件回调：更新拨号状态、时长、释放并发槽位。
     *
     * @param callUuid   呼叫标识
     * @param eventType  RINGING / ANSWERED / HANGUP / FAILED
     * @param params     附加参数（hangupCause、sipCode、talkDuration、mos 等）
     */
    void onCallEvent(String callUuid, String eventType, Map<String, Object> params);

    /**
     * 当前排队长度。
     */
    int getQueueSize();

    /**
     * 当前全局并发数。
     */
    int getGlobalConcurrent();
}
