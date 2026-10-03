package ai.lawyers.system.service.lawyers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.common.utils.SecurityUtils;
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

    private static final ObjectMapper JSON = new ObjectMapper();

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
     * 校验并执行动作：查规则 → 角色校验 → 表单必填校验 → 推进状态 → 按 sla_hours 重算 SLA。
     *
     * @param formParams 动作提交的表单字段（如 orgId/processContent），用于 form_schema 必填校验，
     *                   可为 null（表示无表单）。
     * @return 目标状态（与当前相同时状态不变，调用方可另行记录转办等业务）
     */
    public String applyAction(String flowCode, Long ticketId, String currentStatus,
                              String actionCode, List<String> roles, Map<String, Object> formParams)
    {
        AiTicketFlowDefinition rule = validateAction(flowCode, currentStatus, actionCode, roles);
        validateForm(rule, formParams);
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
        applySla(ticketId, rule);
        return rule.getTargetStatus();
    }

    /** 便捷重载：无表单参数 */
    public String applyAction(String flowCode, Long ticketId, String currentStatus,
                              String actionCode, List<String> roles)
    {
        return applyAction(flowCode, ticketId, currentStatus, actionCode, roles, null);
    }

    /**
     * 按规则 form_schema 校验表单必填字段。schema 形如 {"required":["orgId","remark"]}。
     * 缺失/为空字段一律抛 IllegalArgumentException；schema 为 null/空时放行。
     */
    public void validateForm(AiTicketFlowDefinition rule, Map<String, Object> formParams)
    {
        String schema = rule.getFormSchema();
        if (schema == null || schema.trim().isEmpty())
        {
            return;
        }
        try
        {
            JsonNode root = JSON.readTree(schema);
            JsonNode required = root.get("required");
            if (required == null || !required.isArray())
            {
                return;
            }
            for (JsonNode field : required)
            {
                String key = field.asText();
                Object val = formParams == null ? null : formParams.get(key);
                if (val == null || (val instanceof String && ((String) val).trim().isEmpty()))
                {
                    throw new IllegalArgumentException("动作「" + rule.getActionName()
                            + "」缺少必填字段：" + key);
                }
            }
        }
        catch (IllegalArgumentException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            log.warn("状态机表单 schema 解析失败，跳过校验 action={} schema={}",
                    rule.getActionCode(), schema, e);
        }
    }

    /**
     * 按规则 sla_hours 重算工单 SLA 截止时间（供调用方在自管状态推进后触发）。
     *   null  -> 不重算；0  -> 清空 due_time（归档终态）；>0 -> now + sla_hours 小时。
     */
    public void applySlaDeadline(Long ticketId, AiTicketFlowDefinition rule)
    {
        applySla(ticketId, rule);
    }

    private void applySla(Long ticketId, AiTicketFlowDefinition rule)
    {
        BigDecimal hours = rule.getSlaHours();
        if (hours == null)
        {
            return;
        }
        Date due = null;
        if (hours.compareTo(BigDecimal.ZERO) > 0)
        {
            due = new Date(System.currentTimeMillis()
                    + hours.multiply(BigDecimal.valueOf(3600_000L)).longValue());
        }
        ticketMapper.updateTicketDueTime(ticketId, due);
        log.info("工单 SLA 时限更新 ticketId={} action={} slaHours={} dueTime={}",
                ticketId, rule.getActionCode(), hours, due);
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

    /**
     * 取当前登录用户的角色 key 集合；无登录上下文（系统线程/未登录）时返回空表，
     * 调用方据此走"无角色，仅 admin 规则放行"或拒绝分支。
     */
    public List<String> currentRoleKeys()
    {
        List<String> keys = new ArrayList<>();
        try
        {
            if (SecurityUtils.getLoginUser() != null
                    && SecurityUtils.getLoginUser().getUser() != null
                    && SecurityUtils.getLoginUser().getUser().getRoles() != null)
            {
                SecurityUtils.getLoginUser().getUser().getRoles()
                        .forEach(role -> keys.add(role.getRoleKey()));
            }
        }
        catch (Exception e)
        {
            // 非 web 请求线程取登录态会抛异常，按无角色处理
            log.debug("当前上下文无登录用户：{}", e.getMessage());
        }
        return keys;
    }
}
