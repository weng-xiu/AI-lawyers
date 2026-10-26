package ai.lawyers.system.service.lawyers.agent.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * P10/D3：坐席绑定事件监听器单测——锁定「发布事件」与原生直调参数/异常语义一致。
 */
class UserAgentBindingListenerTest
{
    private UserAgentBindingListener listener;
    private IAiCallAgentStatusService agentStatusService;

    @BeforeEach
    void setUp()
    {
        listener = new UserAgentBindingListener();
        agentStatusService = mock(IAiCallAgentStatusService.class);
        ReflectionTestUtils.setField(listener, "agentStatusService", agentStatusService);
    }

    @Test
    void syncEventDelegatesAllBindingFields()
    {
        listener.onUserAgentSync(new UserAgentSyncEvent(7L, 1001L, "张律师", "8001", "1", "admin"));

        verify(agentStatusService).syncAgentFromUser(7L, 1001L, "张律师", "8001", "1", "admin");
    }

    @Test
    void syncEventKeepsUnbindSemanticsWhenAgentIdIsNull()
    {
        listener.onUserAgentSync(new UserAgentSyncEvent(7L, null, "张律师", null, null, "admin"));

        verify(agentStatusService).syncAgentFromUser(7L, null, "张律师", null, null, "admin");
    }

    /** 同步派发语义：换绑冲突异常必须回抛给发布方，才能触发原事务回滚与前端提示 */
    @Test
    void syncConflictPropagatesToPublisher()
    {
        doThrow(new ServiceException("坐席工号 1001 已绑定其他用户，无法重复绑定"))
                .when(agentStatusService).syncAgentFromUser(7L, 1001L, "张律师", "8001", "1", "admin");

        assertThatThrownBy(() -> listener.onUserAgentSync(
                new UserAgentSyncEvent(7L, 1001L, "张律师", "8001", "1", "admin")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已绑定其他用户");
    }

    @Test
    void releaseEventDelegatesUserId()
    {
        listener.onUserAgentRelease(new UserAgentReleaseEvent(7L));

        verify(agentStatusService).releaseAgentByUserId(7L);
    }
}
