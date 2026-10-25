package ai.lawyers.system.service.lawyers.trunk.event;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Lazy;

import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;
import ai.lawyers.system.service.lawyers.trunk.gateway.esl.InboundCallHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P10/D1：循环依赖治理的架构回归基线。
 *
 * <p>D1 原环：{@code CallDispatchServiceImpl → CallEventDispatcher → CallEventBus →
 * CallEventProcessor → ICallDispatchService / IAiCallAgentStatusService / InboundCallHandler}。
 * 治理后处理器只发布进程内事件，由 {@link CallEventListeners} 消费。本测试用反射锁定：</p>
 * <ul>
 *   <li>处理器不得直接持有三个成环下游类型的字段；</li>
 *   <li>处理器不得再出现 {@link Lazy} 补丁；</li>
 *   <li>总线只依赖 {@link ICallEventProcessor} 接口。</li>
 * </ul>
 */
class CallEventDependencyTest
{
    @Test
    void processorHasNoBackEdgeToStateMachineDependencies()
    {
        assertThat(fieldTypes(CallEventProcessor.class))
                .doesNotContain(ICallDispatchService.class, IAiCallAgentStatusService.class, InboundCallHandler.class);
    }

    @Test
    void processorNoLongerNeedsLazyAnnotation()
    {
        boolean hasLazy = Arrays.stream(CallEventProcessor.class.getDeclaredFields())
                .flatMap(field -> Arrays.stream(field.getAnnotations()))
                .map(Annotation::annotationType)
                .anyMatch(Lazy.class::equals);
        assertThat(hasLazy).as("CallEventProcessor 不应再用 @Lazy 打破循环依赖").isFalse();
    }

    @Test
    void busDependsOnProcessorInterfaceOnly()
    {
        Field processorField = Arrays.stream(CallEventBus.class.getDeclaredFields())
                .filter(field -> "processor".equals(field.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("CallEventBus 缺少 processor 注入字段"));
        assertThat(processorField.getType()).isEqualTo(ICallEventProcessor.class);
    }

    private Class<?>[] fieldTypes(Class<?> type)
    {
        return Arrays.stream(type.getDeclaredFields())
                .map(Field::getType)
                .toArray(Class<?>[]::new);
    }
}
