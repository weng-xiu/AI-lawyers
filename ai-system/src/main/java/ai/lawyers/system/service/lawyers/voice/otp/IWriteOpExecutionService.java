package ai.lawyers.system.service.lawyers.voice.otp;

/**
 * P3-#12：机器人写操作业务执行编排接口（CONFIRMED 后落库 → complete 回执）。
 *
 * <p>调用时序（机器人通话链路，后续迭代接线）：状态机 {@code confirm(opId)}
 * 返回 CONFIRMED 后调用本接口；也可在自动执行失败后重试。</p>
 *
 * @author ai-lawyers
 */
public interface IWriteOpExecutionService
{
    /**
     * 执行已确认（CONFIRMED）的写操作：按动作分发执行器落库，
     * 成功后回执 complete（→EXECUTED 终态）；失败保持 CONFIRMED 可重试。
     *
     * @param opId 确认会话ID
     * @return 执行结果（失败不外抛）
     */
    WriteOpExecutor.ExecutionResult executeConfirmed(String opId);
}
