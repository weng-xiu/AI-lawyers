package ai.lawyers.system.service.lawyers;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.AiTicketFlowDefinition;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.mapper.lawyers.AiTicketFlowDefinitionMapper;

/**
 * P1-6（V2.59）：工单流转状态机服务——按 ai_ticket_flow_definition 配置查询/校验/执行动作。
 *
 * <p>新工单类型只需配置流程不改代码；非法动作、越权角色一律拒绝（抛
 * {@link IllegalArgumentException}），动作合法性以配置为唯一依据。</p>
 *
 * @author ai-lawyers
 */
@Service
public class TicketFlowService
{
    private static final Logger log = LoggerFactory.getLogger(TicketFlowService.class);

    /** 默认热线流程编码 */
    public static final String DEFAULT_FLOW = "HOTLINE";
    /** 超级角色：全部动作放行 */
    public static final String ROLE_ADMIN = "admin";

    @Autowired
    private AiTicketFlowDefinitionMapper flowMapper;

    @Autowired
    private AiCallTicketMapper ticketMapper;

    /**
     * 查询当前状态下当前角色可执行的动作（前端按钮动态渲染）。
     *
     * @param flowCode 流程编码（空取 HOTLINE）
     * @param status   当前工单状态
     * @param roles    当前用户角色 key 集合
     */
    public List<AiTicketFlowDefinition> listAllowedActions(String flowCode, String status, List<String> roles)
    {
        List<AiTicketFlowDefinition> allowed = new ArrayList<>();
        List<AiTicketFlowDefinition> actions = flowMapper.selectActions(normalizeFlow(flowCode), status);
        if (actions != null)
        {
            for (AiTicketFlowDefinition action : actions)
            {
                if (hasRole(action.getRoleKey(), roles))
                {
                    allowed.add(action);
                }
            }
        }
        return allowed;
    }

    /**
     * 校验并执行动作：查规则 → 角色校验 → 目标状态不同则推进工单状态。
     *
     * @return 目标状态（与当前相同时状态不变，调用方可另行记录转办等业务）
     */
    public String applyAction(String flowCode, Long ticketId, String currentStatus,
                              String actionCode, List<String> roles)
    {
        AiTicketFlowDefinition rule = validateAction(flowCode, currentStatus, actionCode, roles);
        if (!rule.getTargetStatus().equals(currentStatus))
        {
            int rows = ticketMapper.updateTicketStatus(ticketId, rule.getTargetStatus());
            if (rows == 0)
            {
                throw new IllegalArgumentException("工单状态推进失败 ticketId=" + ticketId);
            }
            log.info("工单状态机流转 ticketId={} {}: {} -> {}",
                    ticketId, actionCode, currentStatus, rule.getTargetStatus());
        }
        return rule.getTargetStatus();
    }

    /** 校验动作存在且角色允许，返回规则定义 */
    public AiTicketFlowDefinition validateAction(String flowCode, String currentStatus,
                                                 String actionCode, List<String> roles)
    {
        AiTicketFlowDefinition rule = flowMapper.selectRule(
                normalizeFlow(flowCode), currentStatus, actionCode);
        if (rule == null)
        {
            throw new IllegalArgumentException(
                    "当前状态下不存在该动作：" + currentStatus + "/" + actionCode);
        }
        if (!hasRole(rule.getRoleKey(), roles))
        {
            throw new IllegalArgumentException("当前角色无权执行该动作：" + actionCode);
        }
        return rule;
    }

    /** 规则配置角色与用户角色是否匹配（admin 放行） */
    static boolean hasRole(String requiredRoles, List<String> userRoles)
    {
        if (userRoles != null)
        {
            for (String role : userRoles)
            {
                if (ROLE_ADMIN.equalsIgnoreCase(role))
                {
                    return true;
                }
            }
        }
        if (requiredRoles == null)
        {
            return false;
        }
        if (userRoles == null)
        {
            return false;
        }
        for (String required : requiredRoles.split(","))
        {
            String r = required.trim();
            for (String role : userRoles)
            {
                if (r.equalsIgnoreCase(role))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private String normalizeFlow(String flowCode)
    {
        return flowCode == null || flowCode.trim().isEmpty() ? DEFAULT_FLOW : flowCode.trim();
    }
}
