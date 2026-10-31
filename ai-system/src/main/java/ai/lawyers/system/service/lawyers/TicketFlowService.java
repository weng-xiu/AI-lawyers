package ai.lawyers.system.service.lawyers;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiTicketFlowDefinition;
import ai.lawyers.system.domain.lawyers.AiTicketFlowLayout;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.mapper.lawyers.AiTicketFlowDefinitionMapper;
import ai.lawyers.system.mapper.lawyers.AiTicketFlowLayoutMapper;

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
    /** 流程起点状态（与工单新建时默认状态一致） */
    public static final String INITIAL_STATUS = "0";
    /** 非终态：必须至少有一条出向动作，否则流程死锁 */
    private static final Set<String> NON_TERMINAL_STATUSES =
            new HashSet<>(Arrays.asList("0", "1"));
    /** F2：画布坐标上限（前端画布约 1050×400，留冗余防脏数据） */
    private static final int MAX_LAYOUT_POS = 2000;
    /** F2：单流程布局节点数上限，避免一次请求写入海量行 */
    private static final int MAX_LAYOUT_NODES = 20;

    @Autowired
    private AiTicketFlowDefinitionMapper flowMapper;

    @Autowired
    private AiTicketFlowLayoutMapper layoutMapper;

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

    // ================= F2：可视化编排发布前强校验 =================

    /**
     * 发布前校验指定流程的全部规则，返回结构化结果供画布提示。
     *
     * <p>校验项：必填与重复动作、form_schema 合法性、SLA 负数、从起点不可达的状态、
     * 非终态死路、终态仍有出向动作（告警）。错误存在时 valid=false，前端阻断发布。</p>
     *
     * @param flowCode 流程编码（空取 HOTLINE）
     * @return {flowCode, valid, checked, errors[], warnings[]}
     */
    public Map<String, Object> validateFlow(String flowCode)
    {
        String flow = normalizeFlow(flowCode);
        List<AiTicketFlowDefinition> rules = flowMapper.selectByFlowCode(flow);
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("flowCode", flow);
        if (rules == null || rules.isEmpty())
        {
            errors.add("流程未配置任何规则");
            result.put("valid", false);
            result.put("checked", 0);
            result.put("errors", errors);
            result.put("warnings", warnings);
            return result;
        }

        // 1) 单条规则基础校验（启用/停用规则都要保证可编辑、可发布）
        Map<String, Set<String>> actionByFrom = new HashMap<>();
        for (AiTicketFlowDefinition rule : rules)
        {
            String label = "规则" + (rule.getFlowId() == null ? "" : "#" + rule.getFlowId())
                    + "(" + safe(rule.getStatusFrom()) + "→" + safe(rule.getActionCode()) + ")";
            if (isBlank(rule.getStatusFrom()) || isBlank(rule.getActionCode())
                    || isBlank(rule.getTargetStatus()) || isBlank(rule.getActionName()))
            {
                errors.add(label + "：源状态/动作标识/动作名称/目标状态均不能为空");
                continue;
            }
            if (isBlank(rule.getRoleKey()))
            {
                errors.add(label + "：允许角色不能为空");
            }
            if (rule.getRoleKey() != null && ROLE_ADMIN.equalsIgnoreCase(rule.getRoleKey().trim()))
            {
                warnings.add(label + "：仅 admin 可执行，业务角色将看不到该动作");
            }
            if (rule.getSlaHours() != null && rule.getSlaHours().compareTo(BigDecimal.ZERO) < 0)
            {
                errors.add(label + "：SLA 时限不能为负数");
            }
            Set<String> actions = actionByFrom.computeIfAbsent(rule.getStatusFrom(), k -> new HashSet<>());
            if (!actions.add(rule.getActionCode()))
            {
                errors.add(label + "：同源状态下动作标识重复，运行时无法确定目标状态");
            }
            String schema = rule.getFormSchema();
            if (!isBlank(schema))
            {
                try
                {
                    JsonNode root = JSON.readTree(schema);
                    JsonNode required = root.get("required");
                    if (required == null || !required.isArray())
                    {
                        warnings.add(label + "：form_schema 缺少 required 数组，发布后不会校验必填");
                    }
                }
                catch (Exception e)
                {
                    errors.add(label + "：form_schema 不是合法 JSON");
                }
            }
        }

        // 2) 仅启用规则参与图可达性分析（停用规则不影响运行时）
        Map<String, Set<String>> edges = new HashMap<>();
        Set<String> nodes = new LinkedHashSet<>();
        for (AiTicketFlowDefinition rule : rules)
        {
            if (!isEnabled(rule) || isBlank(rule.getStatusFrom()) || isBlank(rule.getTargetStatus()))
            {
                continue;
            }
            nodes.add(rule.getStatusFrom());
            nodes.add(rule.getTargetStatus());
            edges.computeIfAbsent(rule.getStatusFrom(), k -> new LinkedHashSet<>())
                    .add(rule.getTargetStatus());
        }
        if (edges.isEmpty())
        {
            errors.add("流程没有任何启用中的规则，无法运行");
        }
        else
        {
            Set<String> reachable = new HashSet<>();
            Deque<String> queue = new ArrayDeque<>();
            reachable.add(INITIAL_STATUS);
            queue.add(INITIAL_STATUS);
            while (!queue.isEmpty())
            {
                String from = queue.poll();
                for (String to : edges.getOrDefault(from, new LinkedHashSet<>()))
                {
                    if (reachable.add(to))
                    {
                        queue.add(to);
                    }
                }
            }
            for (String node : nodes)
            {
                if (!reachable.contains(node))
                {
                    errors.add("状态「" + node + "」从起点 " + INITIAL_STATUS + " 不可达，动作永远不会触发");
                }
            }
            for (String node : nodes)
            {
                if (NON_TERMINAL_STATUSES.contains(node)
                        && edges.getOrDefault(node, new LinkedHashSet<>()).isEmpty())
                {
                    errors.add("状态「" + node + "」没有出向动作，工单将卡死在该状态");
                }
            }
            if (!edges.getOrDefault("3", new LinkedHashSet<>()).isEmpty())
            {
                warnings.add("已归档状态仍有出向动作，归档后工单可能被重新推进");
            }
        }

        result.put("valid", errors.isEmpty());
        result.put("checked", rules.size());
        result.put("errors", errors);
        result.put("warnings", warnings);
        return result;
    }

    private boolean isEnabled(AiTicketFlowDefinition rule)
    {
        return rule.getStatus() == null || "0".equals(rule.getStatus());
    }

    private String safe(String s)
    {
        return s == null ? "" : s;
    }

    // ================= F2：画布节点布局持久化 =================

    /**
     * 查询指定流程已保存的画布节点布局；从未保存过时返回空列表，前端回退默认排布。
     *
     * @param flowCode 流程编码（空取 HOTLINE）
     */
    public List<AiTicketFlowLayout> listLayout(String flowCode)
    {
        List<AiTicketFlowLayout> layouts =
                layoutMapper.selectLayoutByFlowCode(normalizeFlow(flowCode));
        return layouts == null ? new ArrayList<>() : layouts;
    }

    /**
     * 保存画布节点布局：按 (flowCode, statusCode) 唯一键逐条覆盖写入。
     *
     * <p>服务端校验：列表非空、节点数上限、状态编码非空且不重复、坐标非空且在
     * [0, MAX_LAYOUT_POS] 内；flowCode 强制取路径参数，防止跨流程覆盖布局。</p>
     *
     * @return 已处理的节点数
     */
    public int saveLayout(String flowCode, List<AiTicketFlowLayout> layouts)
    {
        String flow = normalizeFlow(flowCode);
        if (layouts == null || layouts.isEmpty())
        {
            throw new IllegalArgumentException("布局数据不能为空");
        }
        if (layouts.size() > MAX_LAYOUT_NODES)
        {
            throw new IllegalArgumentException("单流程布局节点不能超过 " + MAX_LAYOUT_NODES + " 个");
        }
        Set<String> statusCodes = new HashSet<>();
        String operator = currentUsername();
        int rows = 0;
        for (AiTicketFlowLayout layout : layouts)
        {
            if (layout == null || isBlank(layout.getStatusCode()))
            {
                throw new IllegalArgumentException("节点状态编码不能为空");
            }
            String statusCode = layout.getStatusCode().trim();
            if (!statusCodes.add(statusCode))
            {
                throw new IllegalArgumentException("节点状态编码重复：" + statusCode);
            }
            Integer x = layout.getPosX();
            Integer y = layout.getPosY();
            if (x == null || y == null)
            {
                throw new IllegalArgumentException("节点「" + statusCode + "」坐标不能为空");
            }
            if (x < 0 || x > MAX_LAYOUT_POS || y < 0 || y > MAX_LAYOUT_POS)
            {
                throw new IllegalArgumentException("节点「" + statusCode + "」坐标超出范围（0~"
                        + MAX_LAYOUT_POS + "）");
            }
            layout.setFlowCode(flow);
            layout.setStatusCode(statusCode);
            layout.setUpdateBy(operator);
            layoutMapper.upsertLayout(layout);
            rows++;
        }
        log.info("工单流程布局保存 flowCode={} nodes={} by={}", flow, rows, operator);
        return rows;
    }

    /** 取当前登录用户名；无登录上下文（定时任务/单测）时返回 null，不阻断保存 */
    private String currentUsername()
    {
        try
        {
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            log.debug("当前上下文无登录用户，布局操作人不记录：{}", e.getMessage());
            return null;
        }
    }

    // ================= P1-6：DSL 管理端 CRUD =================

    /** 管理端条件过滤列表 */
    public List<AiTicketFlowDefinition> listDefinitions(AiTicketFlowDefinition query)
    {
        return flowMapper.selectDefinitionList(query == null ? new AiTicketFlowDefinition() : query);
    }

    /** 按主键查询（编辑回显） */
    public AiTicketFlowDefinition getDefinition(Long flowId)
    {
        AiTicketFlowDefinition rule = flowMapper.selectByFlowId(flowId);
        if (rule == null)
        {
            throw new IllegalArgumentException("流程规则不存在：" + flowId);
        }
        return rule;
    }

    /** 新增规则：必填 + form_schema JSON 合法性 + (flow,from,action) 唯一校验 */
    public int addDefinition(AiTicketFlowDefinition rule)
    {
        prepareDefinition(rule);
        if (flowMapper.selectRule(rule.getFlowCode(), rule.getStatusFrom(), rule.getActionCode()) != null)
        {
            throw new IllegalArgumentException("该动作规则已存在："
                    + rule.getFlowCode() + "/" + rule.getStatusFrom() + "/" + rule.getActionCode());
        }
        if (rule.getStatus() == null)
        {
            rule.setStatus("0");
        }
        return flowMapper.insertDefinition(rule);
    }

    /** 修改规则：必填 + form_schema JSON 合法性（流程/源状态/动作不可改，避免破坏既有引用） */
    public int updateDefinition(AiTicketFlowDefinition rule)
    {
        if (rule.getFlowId() == null)
        {
            throw new IllegalArgumentException("流程规则ID不能为空");
        }
        prepareDefinition(rule);
        return flowMapper.updateDefinition(rule);
    }

    private void prepareDefinition(AiTicketFlowDefinition rule)
    {
        if (rule == null)
        {
            throw new IllegalArgumentException("规则内容不能为空");
        }
        if (isBlank(rule.getFlowCode()) || isBlank(rule.getStatusFrom())
                || isBlank(rule.getActionCode()) || isBlank(rule.getTargetStatus()))
        {
            throw new IllegalArgumentException("流程编码、源状态、动作、目标状态均不能为空");
        }
        rule.setFlowCode(rule.getFlowCode().trim());
        rule.setStatusFrom(rule.getStatusFrom().trim());
        rule.setActionCode(rule.getActionCode().trim());
        rule.setTargetStatus(rule.getTargetStatus().trim());
        if (!isBlank(rule.getFormSchema()))
        {
            try
            {
                JSON.readTree(rule.getFormSchema());
            }
            catch (Exception e)
            {
                throw new IllegalArgumentException("表单 schema 不是合法 JSON：" + e.getMessage());
            }
        }
    }

    private boolean isBlank(String s)
    {
        return s == null || s.trim().isEmpty();
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
