package ai.lawyers.system.service.lawyers.agent.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;

/**
 * P10/D3：用户管理与坐席绑定的进程内事件监听器。
 *
 * <p>把原 {@code SysUserServiceImpl} 对 {@code IAiCallAgentStatusService} 的直接依赖
 * 反转为「发布事件 → 本监听器消费」，断开
 * {@code SysUserServiceImpl → IAiCallAgentStatusService → … → MessageNotifyDispatcher → ISysUserService} 这条环。</p>
 *
 * <p>本类不被任何业务 Bean 依赖，仅由 Spring 事件机制驱动，因此不产生新的依赖边。
 * Spring 事件默认同步派发：监听器在发布方线程与事务内执行，
 * 换绑冲突异常仍回抛给调用方，事务回滚与异常提示保持原语义。</p>
 *
 * @author ai-lawyers
 */
@Component
public class UserAgentBindingListener
{
    @Autowired
    private IAiCallAgentStatusService agentStatusService;

    /** 新增/修改/导入用户 → 同步坐席工号、分机、呼叫模式（含换绑校验） */
    @EventListener
    public void onUserAgentSync(UserAgentSyncEvent event)
    {
        agentStatusService.syncAgentFromUser(event.getUserId(), event.getAgentId(),
                event.getAgentName(), event.getSipExtension(), event.getCallMode(), event.getOperator());
    }

    /** 删除用户（单个/批量）→ 释放坐席绑定，保留运行记录 */
    @EventListener
    public void onUserAgentRelease(UserAgentReleaseEvent event)
    {
        agentStatusService.releaseAgentByUserId(event.getUserId());
    }
}
