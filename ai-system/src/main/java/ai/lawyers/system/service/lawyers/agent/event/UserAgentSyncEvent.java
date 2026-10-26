package ai.lawyers.system.service.lawyers.agent.event;

/**
 * P10/D3：用户管理 → 坐席配置同步事件（进程内 Spring 事件）。
 *
 * <p>由 {@code SysUserServiceImpl} 在新增/修改/导入用户时发布，
 * {@link UserAgentBindingListener} 消费后调用 {@code IAiCallAgentStatusService}，
 * 避免「系统用户服务 ↔ 坐席状态服务 ↔ 会话/知识库 ↔ 消息通知」兜圈。</p>
 *
 * <p>Spring 事件默认同步派发，监听器与发布方同线程、同事务，
 * 换绑校验失败抛出的 {@code ServiceException} 仍会回抛到发布方，保持原有事务回滚语义。</p>
 *
 * @author ai-lawyers
 */
public class UserAgentSyncEvent
{
    private final Long userId;

    private final Long agentId;

    private final String agentName;

    private final String sipExtension;

    private final String callMode;

    private final String operator;

    public UserAgentSyncEvent(Long userId, Long agentId, String agentName,
                              String sipExtension, String callMode, String operator)
    {
        this.userId = userId;
        this.agentId = agentId;
        this.agentName = agentName;
        this.sipExtension = sipExtension;
        this.callMode = callMode;
        this.operator = operator;
    }

    public Long getUserId()
    {
        return userId;
    }

    public Long getAgentId()
    {
        return agentId;
    }

    public String getAgentName()
    {
        return agentName;
    }

    public String getSipExtension()
    {
        return sipExtension;
    }

    public String getCallMode()
    {
        return callMode;
    }

    public String getOperator()
    {
        return operator;
    }
}
