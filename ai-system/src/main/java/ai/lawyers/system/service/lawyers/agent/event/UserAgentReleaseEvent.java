package ai.lawyers.system.service.lawyers.agent.event;

/**
 * P10/D3：用户删除 → 坐席绑定释放事件（进程内 Spring 事件）。
 *
 * <p>由 {@code SysUserServiceImpl} 在删除用户（单个/批量）时发布，
 * {@link UserAgentBindingListener} 消费后调用 {@code IAiCallAgentStatusService#releaseAgentByUserId}，
 * 只解除绑定关系、不删除坐席运行记录。</p>
 *
 * @author ai-lawyers
 */
public class UserAgentReleaseEvent
{
    private final Long userId;

    public UserAgentReleaseEvent(Long userId)
    {
        this.userId = userId;
    }

    public Long getUserId()
    {
        return userId;
    }
}
