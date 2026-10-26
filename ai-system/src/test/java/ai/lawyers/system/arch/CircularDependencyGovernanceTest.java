package ai.lawyers.system.arch;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Lazy;

import ai.lawyers.system.mapper.lawyers.AiCallAgentStatusMapper;
import ai.lawyers.system.service.impl.lawyers.quality.AiQualityInspectionServiceImpl;
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;
import ai.lawyers.system.service.lawyers.trunk.event.CallEventProcessor;
import ai.lawyers.system.service.lawyers.trunk.gateway.esl.InboundCallHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P10/D1 + D2：循环依赖治理的架构回归基线（全量收口）。
 *
 * <p>已消除的两条环：</p>
 * <ol>
 *   <li>D1：调度 → 事件总线 → 处理器 → 调度 / 坐席状态 / 入站处理器（改为发布订阅 + 接口层）；</li>
 *   <li>D2：质检 → 坐席状态 → 调度 → 事件总线 → 质检转写投递 → 质检
 *       （坐席只读反查改走 Mapper）。</li>
 * </ol>
 *
 * <p>本测试锁定：上述两个门面类不得再持有成环下游类型的字段，也不得再用 {@link Lazy} 补丁。</p>
 */
class CircularDependencyGovernanceTest
{
    @Test
    void callEventProcessorKeepsReverseDependenciesOut()
    {
        assertThat(fieldTypes(CallEventProcessor.class))
                .doesNotContain(ICallDispatchService.class, IAiCallAgentStatusService.class, InboundCallHandler.class);
        assertNoLazy(CallEventProcessor.class);
    }

    @Test
    void qualityInspectionUsesMapperInsteadOfAgentStatusService()
    {
        assertThat(fieldTypes(AiQualityInspectionServiceImpl.class))
                .doesNotContain(IAiCallAgentStatusService.class)
                .contains(AiCallAgentStatusMapper.class);
        assertNoLazy(AiQualityInspectionServiceImpl.class);
    }

    private void assertNoLazy(Class<?> type)
    {
        boolean hasLazy = Arrays.stream(type.getDeclaredFields())
                .flatMap(field -> Arrays.stream(field.getAnnotations()))
                .map(Annotation::annotationType)
                .anyMatch(Lazy.class::equals);
        assertThat(hasLazy).as("%s 不应再用 @Lazy 打破循环依赖", type.getSimpleName()).isFalse();
    }

    private Class<?>[] fieldTypes(Class<?> type)
    {
        return Arrays.stream(type.getDeclaredFields())
                .map(Field::getType)
                .toArray(Class<?>[]::new);
    }
}
