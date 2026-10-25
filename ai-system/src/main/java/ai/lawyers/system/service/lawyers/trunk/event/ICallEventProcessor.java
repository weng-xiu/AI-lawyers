package ai.lawyers.system.service.lawyers.trunk.event;

/**
 * P10/D1：统一呼叫事件处理器接口。
 *
 * <p>总线（{@link CallEventBus}）只依赖本接口，不再感知具体实现，
 * 便于替换实现、单测打桩，并作为循环依赖治理的边界：实现类不得反向依赖
 * {@code ICallDispatchService} / {@code IAiCallAgentStatusService} / {@code InboundCallHandler}，
 * 需要触发的下游动作统一通过 Spring 事件发布（见 {@link CallEventListeners}）。</p>
 *
 * @author ai-lawyers
 */
public interface ICallEventProcessor
{
    /**
     * 处理一条归一化呼叫事件。
     *
     * @param event 统一事件模型
     */
    void handle(CallEvent event);
}
