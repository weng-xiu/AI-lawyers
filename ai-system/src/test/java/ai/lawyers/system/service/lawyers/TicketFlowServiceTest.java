package ai.lawyers.system.service.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ai.lawyers.system.domain.lawyers.AiTicketFlowDefinition;
import ai.lawyers.system.domain.lawyers.AiTicketFlowLayout;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.mapper.lawyers.AiTicketFlowDefinitionMapper;
import ai.lawyers.system.mapper.lawyers.AiTicketFlowLayoutMapper;

/**
 * P1-6：工单流转状态机单测——角色过滤、动作校验。用手写 fake mapper，无 Mockito 依赖。
 *
 * @author ai-lawyers
 */
class TicketFlowServiceTest
{
    private TicketFlowService service;
    private AiCallTicketMapper ticketMapper;
    private AiTicketFlowLayoutMapper layoutMapper;

    @BeforeEach
    void setUp()
    {
        service = new TicketFlowService();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "flowMapper", fakeMapper());
        ticketMapper = mock(AiCallTicketMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        layoutMapper = mock(AiTicketFlowLayoutMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "layoutMapper", layoutMapper);
    }

    @Test
    void listAllowedActions_agentSeesStartOnly()
    {
        List<AiTicketFlowDefinition> actions = service.listAllowedActions(
                "HOTLINE", "0", Arrays.asList("agent"));
        assertEquals(1, actions.size());
        assertEquals("start", actions.get(0).getActionCode());
    }

    @Test
    void listAllowedActions_leaderSeesStartAndTransfer()
    {
        List<AiTicketFlowDefinition> actions = service.listAllowedActions(
                "HOTLINE", "0", Arrays.asList("leader"));
        assertEquals(2, actions.size());
    }

    @Test
    void validateAction_unknownAction_rejected()
    {
        assertThrows(IllegalArgumentException.class, () -> service.validateAction(
                "HOTLINE", "0", "delete", Arrays.asList("admin")));
    }

    @Test
    void validateAction_agentTransfer_forbidden()
    {
        assertThrows(IllegalArgumentException.class, () -> service.validateAction(
                "HOTLINE", "0", "transfer", Arrays.asList("agent")));
    }

    @Test
    void validateAction_adminAlwaysAllowed()
    {
        AiTicketFlowDefinition rule = service.validateAction(
                "HOTLINE", "0", "transfer", Arrays.asList("admin"));
        assertEquals("0", rule.getTargetStatus());
    }

    @Test
    void listAllowedActions_emptyRoles_noActions()
    {
        assertEquals(0, service.listAllowedActions(
                "HOTLINE", "0", Collections.emptyList()).size());
    }

    // ===== P1-6 V2.72：表单必填校验 + SLA 时限 =====

    @Test
    void validateForm_requiredFieldPresent_ok()
    {
        AiTicketFlowDefinition rule = rule("start", "1", "agent,leader");
        rule.setFormSchema("{\"required\":[\"processContent\"]}");
        Map<String, Object> form = new HashMap<>();
        form.put("processContent", "已受理");
        service.validateForm(rule, form); // 不抛异常即通过
    }

    @Test
    void validateForm_missingField_throws()
    {
        AiTicketFlowDefinition rule = rule("start", "1", "agent,leader");
        rule.setFormSchema("{\"required\":[\"processContent\"]}");
        assertThrows(IllegalArgumentException.class,
                () -> service.validateForm(rule, new HashMap<>()));
    }

    @Test
    void validateForm_emptyField_throws()
    {
        AiTicketFlowDefinition rule = rule("transfer", "0", "leader,admin");
        rule.setFormSchema("{\"required\":[\"orgId\"]}");
        Map<String, Object> form = new HashMap<>();
        form.put("orgId", "   ");
        assertThrows(IllegalArgumentException.class,
                () -> service.validateForm(rule, form));
    }

    @Test
    void validateForm_nullSchema_passes()
    {
        AiTicketFlowDefinition rule = rule("complete", "2", "agent,leader");
        rule.setFormSchema(null);
        service.validateForm(rule, new HashMap<>());
    }

    @Test
    void applySla_nullHours_noUpdate()
    {
        AiTicketFlowDefinition rule = rule("complete", "2", "agent,leader");
        rule.setSlaHours(null);
        service.applySlaDeadline(1001L, rule);
        verify(ticketMapper, times(0)).updateTicketDueTime(any(), any());
    }

    @Test
    void applySla_zeroHours_clearDueTime()
    {
        AiTicketFlowDefinition rule = rule("archive", "3", "leader,admin");
        rule.setSlaHours(BigDecimal.ZERO);
        service.applySlaDeadline(1001L, rule);
        verify(ticketMapper, times(1)).updateTicketDueTime(eq(1001L), eq(null));
    }

    @Test
    void applySla_positiveHours_futureDueTime()
    {
        AiTicketFlowDefinition rule = rule("start", "1", "agent,leader");
        rule.setSlaHours(new BigDecimal("24"));
        long before = System.currentTimeMillis();
        service.applySlaDeadline(1001L, rule);
        long after = System.currentTimeMillis();
        Date expectedDue = new Date(before + 24L * 3600_000L);
        // 捕获实际写入的 due_time，断言落在 [before+24h, after+24h] 区间内
        org.mockito.ArgumentCaptor<Date> captor = org.mockito.ArgumentCaptor.forClass(Date.class);
        verify(ticketMapper, times(1)).updateTicketDueTime(eq(1001L), captor.capture());
        Date actual = captor.getValue();
        assertNotNull(actual);
        assertTrue(actual.getTime() >= before + 24L * 3600_000L
                && actual.getTime() <= after + 24L * 3600_000L,
                "SLA 时限应约等于 now+24h，实际偏差：" + (actual.getTime() - expectedDue.getTime()) + "ms");
    }

    // ===== P1-6：DSL 管理端 CRUD =====

    @Test
    void crud_addNewRule_persistsAndDefaultsStatus()
    {
        AiTicketFlowDefinition rule = rule("complete", "2", "agent,leader");
        rule.setStatusFrom("1");
        rule.setFormSchema(null);
        assertEquals(1, service.addDefinition(rule));
        assertNotNull(rule.getFlowId());
        assertEquals("0", rule.getStatus());
    }

    @Test
    void crud_addDuplicate_throws()
    {
        AiTicketFlowDefinition dup = rule("start", "1", "agent");
        // 与 seed 中 HOTLINE/0/start 冲突
        assertThrows(IllegalArgumentException.class, () -> service.addDefinition(dup));
    }

    @Test
    void crud_addMissingRequired_throws()
    {
        AiTicketFlowDefinition rule = rule("x", "2", "agent");
        rule.setActionCode("  ");
        assertThrows(IllegalArgumentException.class, () -> service.addDefinition(rule));
    }

    @Test
    void crud_addInvalidJsonSchema_throws()
    {
        AiTicketFlowDefinition rule = rule("complete", "2", "agent");
        rule.setStatusFrom("1");
        rule.setFormSchema("{not-json");
        assertThrows(IllegalArgumentException.class, () -> service.addDefinition(rule));
    }

    @Test
    void crud_getExisting_ok()
    {
        assertEquals("start", service.getDefinition(1L).getActionCode());
    }

    @Test
    void crud_getMissing_throws()
    {
        assertThrows(IllegalArgumentException.class, () -> service.getDefinition(999L));
    }

    @Test
    void crud_updateExisting_ok()
    {
        AiTicketFlowDefinition rule = service.getDefinition(1L);
        rule.setActionName("受理改名");
        assertEquals(1, service.updateDefinition(rule));
        assertEquals("受理改名", service.getDefinition(1L).getActionName());
    }

    @Test
    void crud_updateWithoutId_throws()
    {
        AiTicketFlowDefinition rule = rule("start", "1", "agent");
        assertThrows(IllegalArgumentException.class, () -> service.updateDefinition(rule));
    }

    @Test
    void crud_list_returnsAll()
    {
        assertEquals(2, service.listDefinitions(new AiTicketFlowDefinition()).size());
    }

    // ===== F2：可视化编排发布前强校验 =====

    @Test
    void validateFlow_completeChain_valid()
    {
        Map<String, Object> result = serviceWithRules(chain()).validateFlow("HOTLINE");
        assertEquals(true, result.get("valid"));
        assertEquals(3, result.get("checked"));
        assertTrue(((List<?>) result.get("errors")).isEmpty());
        assertTrue(((List<?>) result.get("warnings")).isEmpty());
    }

    @Test
    void validateFlow_emptyFlow_error()
    {
        Map<String, Object> result = serviceWithRules(new ArrayList<>()).validateFlow("HOTLINE");
        assertEquals(false, result.get("valid"));
        assertEquals(0, result.get("checked"));
        assertEquals(1, ((List<?>) result.get("errors")).size());
        assertTrue(String.valueOf(((List<?>) result.get("errors")).get(0)).contains("未配置任何规则"));
    }

    @Test
    void validateFlow_deadEndAndUnreachable_reported()
    {
        List<AiTicketFlowDefinition> rules = new ArrayList<>();
        rules.add(rule("start", "1", "agent"));           // 0 -> 1
        AiTicketFlowDefinition orphan = rule("resume", "2", "leader");
        orphan.setStatusFrom("9");                        // 9 -> 2，从起点不可达
        rules.add(orphan);

        Map<String, Object> result = serviceWithRules(rules).validateFlow("HOTLINE");
        assertEquals(false, result.get("valid"));
        String errors = String.valueOf(result.get("errors"));
        assertTrue(errors.contains("没有出向动作"), errors);
        assertTrue(errors.contains("不可达"), errors);
    }

    @Test
    void validateFlow_duplicateActionAndBadSchemaAndNegativeSla_reported()
    {
        List<AiTicketFlowDefinition> rules = new ArrayList<>();
        AiTicketFlowDefinition a = rule("start", "1", "agent");
        AiTicketFlowDefinition b = rule("start", "1", "agent");   // 同源同动作重复
        b.setFormSchema("{not-json");
        b.setSlaHours(new BigDecimal("-1"));
        rules.add(a);
        rules.add(b);

        Map<String, Object> result = serviceWithRules(rules).validateFlow("HOTLINE");
        assertEquals(false, result.get("valid"));
        String errors = String.valueOf(result.get("errors"));
        assertTrue(errors.contains("重复"), errors);
        assertTrue(errors.contains("form_schema 不是合法 JSON"), errors);
        assertTrue(errors.contains("SLA 时限不能为负数"), errors);
    }

    @Test
    void validateFlow_disabledRuleIgnored_andAdminOnlyWarned()
    {
        List<AiTicketFlowDefinition> rules = chain();
        rules.forEach(r -> r.setRoleKey("admin"));
        AiTicketFlowDefinition disabled = rule("reopen", "0", "leader");
        disabled.setStatusFrom("2");
        disabled.setStatus("1");                          // 停用：不参与可达性/死路分析
        rules.add(disabled);

        Map<String, Object> result = serviceWithRules(rules).validateFlow("HOTLINE");
        assertEquals(true, result.get("valid"), String.valueOf(result.get("errors")));
        assertTrue(String.valueOf(result.get("warnings")).contains("仅 admin"), 
                String.valueOf(result.get("warnings")));
    }

    @Test
    void validateFlow_archiveOutgoing_warned()
    {
        List<AiTicketFlowDefinition> rules = chain();
        AiTicketFlowDefinition reopen = rule("reopen", "2", "leader");
        reopen.setStatusFrom("3");
        rules.add(reopen);

        Map<String, Object> result = serviceWithRules(rules).validateFlow("HOTLINE");
        assertEquals(true, result.get("valid"), String.valueOf(result.get("errors")));
        assertTrue(String.valueOf(result.get("warnings")).contains("归档"), 
                String.valueOf(result.get("warnings")));
    }

    // ===== F2：画布节点布局持久化 =====

    @Test
    void layout_list_normalizesDefaultFlow()
    {
        List<AiTicketFlowLayout> saved = new ArrayList<>();
        saved.add(layout("0", 40, 140));
        when(layoutMapper.selectLayoutByFlowCode("HOTLINE")).thenReturn(saved);

        List<AiTicketFlowLayout> result = service.listLayout("  ");
        assertEquals(1, result.size());
        verify(layoutMapper).selectLayoutByFlowCode("HOTLINE");
    }

    @Test
    void layout_list_nullResult_returnsEmpty()
    {
        when(layoutMapper.selectLayoutByFlowCode("HOTLINE")).thenReturn(null);
        assertNotNull(service.listLayout(null));
        assertTrue(service.listLayout(null).isEmpty());
    }

    @Test
    void layout_save_forcesFlowCodeAndUpsertsEachNode()
    {
        List<AiTicketFlowLayout> layouts = new ArrayList<>();
        layouts.add(layout(" 0 ", 40, 140));
        AiTicketFlowLayout second = layout("1", 300, 140);
        second.setFlowCode("OTHER"); // 跨流程脏数据应被路径参数覆盖
        layouts.add(second);

        assertEquals(2, service.saveLayout("HOTLINE", layouts));
        assertEquals("HOTLINE", layouts.get(0).getFlowCode());
        assertEquals("0", layouts.get(0).getStatusCode());
        assertEquals("HOTLINE", layouts.get(1).getFlowCode());
        assertNull(layouts.get(0).getUpdateBy()); // 单测无登录上下文，操作人缺省
        verify(layoutMapper, times(2)).upsertLayout(any(AiTicketFlowLayout.class));
    }

    @Test
    void layout_save_emptyList_throws()
    {
        assertThrows(IllegalArgumentException.class, () -> service.saveLayout("HOTLINE", null));
        assertThrows(IllegalArgumentException.class,
                () -> service.saveLayout("HOTLINE", new ArrayList<>()));
        verify(layoutMapper, times(0)).upsertLayout(any(AiTicketFlowLayout.class));
    }

    @Test
    void layout_save_duplicateStatus_throws()
    {
        List<AiTicketFlowLayout> layouts = new ArrayList<>();
        layouts.add(layout("0", 40, 140));
        layouts.add(layout("0", 40, 140));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.saveLayout("HOTLINE", layouts));
        assertTrue(e.getMessage().contains("重复"), e.getMessage());
    }

    @Test
    void layout_save_invalidCoordinate_throws()
    {
        List<AiTicketFlowLayout> negative = new ArrayList<>();
        negative.add(layout("0", -1, 140));
        assertThrows(IllegalArgumentException.class,
                () -> service.saveLayout("HOTLINE", negative));

        List<AiTicketFlowLayout> tooLarge = new ArrayList<>();
        tooLarge.add(layout("0", 40, 3000));
        assertThrows(IllegalArgumentException.class,
                () -> service.saveLayout("HOTLINE", tooLarge));

        List<AiTicketFlowLayout> missing = new ArrayList<>();
        AiTicketFlowLayout noY = layout("0", 40, 140);
        noY.setPosY(null);
        missing.add(noY);
        assertThrows(IllegalArgumentException.class,
                () -> service.saveLayout("HOTLINE", missing));
        verify(layoutMapper, times(0)).upsertLayout(any(AiTicketFlowLayout.class));
    }

    @Test
    void layout_save_blankStatusCode_throws()
    {
        List<AiTicketFlowLayout> layouts = new ArrayList<>();
        layouts.add(layout("  ", 40, 140));
        assertThrows(IllegalArgumentException.class,
                () -> service.saveLayout("HOTLINE", layouts));
    }

    /** 构造布局节点（默认 flowCode 与请求路径不一致，用于验证强制覆盖） */
    private AiTicketFlowLayout layout(String statusCode, int x, int y)
    {
        AiTicketFlowLayout layout = new AiTicketFlowLayout();
        layout.setFlowCode("OTHER");
        layout.setStatusCode(statusCode);
        layout.setPosX(x);
        layout.setPosY(y);
        return layout;
    }

    /** 0→1→2→3 的完整合法链路 */
    private List<AiTicketFlowDefinition> chain()
    {
        List<AiTicketFlowDefinition> rules = new ArrayList<>();
        rules.add(rule("start", "1", "agent,leader"));
        AiTicketFlowDefinition complete = rule("complete", "2", "agent,leader");
        complete.setStatusFrom("1");
        rules.add(complete);
        AiTicketFlowDefinition archive = rule("archive", "3", "leader");
        archive.setStatusFrom("2");
        rules.add(archive);
        return rules;
    }

    /** 用 Mockito mapper 构造独立 service，只关心 selectByFlowCode */
    private TicketFlowService serviceWithRules(List<AiTicketFlowDefinition> rules)
    {
        AiTicketFlowDefinitionMapper mapper = mock(AiTicketFlowDefinitionMapper.class);
        when(mapper.selectByFlowCode("HOTLINE")).thenReturn(rules);
        TicketFlowService s = new TicketFlowService();
        org.springframework.test.util.ReflectionTestUtils.setField(s, "flowMapper", mapper);
        org.springframework.test.util.ReflectionTestUtils.setField(s, "ticketMapper", mock(AiCallTicketMapper.class));
        return s;
    }

    /** 构造与 seed 一致的 fake 流程 mapper（带内存存储，支持管理端 CRUD 测试） */
    private AiTicketFlowDefinitionMapper fakeMapper()
    {
        final List<AiTicketFlowDefinition> store = new ArrayList<>();
        final AiTicketFlowDefinition start = rule("start", "1", "agent,leader");
        start.setFlowId(1L);
        final AiTicketFlowDefinition transfer = rule("transfer", "0", "leader,admin");
        transfer.setFlowId(2L);
        store.add(start);
        store.add(transfer);

        return new AiTicketFlowDefinitionMapper()
        {
            @Override
            public List<AiTicketFlowDefinition> selectActions(String flowCode, String statusFrom)
            {
                List<AiTicketFlowDefinition> r = new ArrayList<>();
                for (AiTicketFlowDefinition d : store)
                {
                    if ("0".equals(d.getStatus()) && d.getFlowCode().equals(flowCode)
                            && d.getStatusFrom().equals(statusFrom))
                    {
                        r.add(d);
                    }
                }
                return r;
            }

            @Override
            public List<AiTicketFlowDefinition> selectByFlowCode(String flowCode)
            {
                List<AiTicketFlowDefinition> r = new ArrayList<>();
                for (AiTicketFlowDefinition d : store)
                {
                    if (d.getFlowCode().equals(flowCode))
                    {
                        r.add(d);
                    }
                }
                return r;
            }

            @Override
            public List<AiTicketFlowDefinition> selectDefinitionList(AiTicketFlowDefinition query)
            {
                return new ArrayList<>(store);
            }

            @Override
            public AiTicketFlowDefinition selectByFlowId(Long flowId)
            {
                for (AiTicketFlowDefinition d : store)
                {
                    if (d.getFlowId().equals(flowId))
                    {
                        return d;
                    }
                }
                return null;
            }

            @Override
            public AiTicketFlowDefinition selectRule(String flowCode, String statusFrom,
                                                     String actionCode)
            {
                for (AiTicketFlowDefinition d : selectActions(flowCode, statusFrom))
                {
                    if (d.getActionCode().equals(actionCode))
                    {
                        return d;
                    }
                }
                return null;
            }

            @Override
            public int insertDefinition(AiTicketFlowDefinition definition)
            {
                definition.setFlowId((long) (store.size() + 1));
                store.add(definition);
                return 1;
            }

            @Override
            public int updateDefinition(AiTicketFlowDefinition definition)
            {
                for (int i = 0; i < store.size(); i++)
                {
                    if (store.get(i).getFlowId().equals(definition.getFlowId()))
                    {
                        store.set(i, definition);
                        return 1;
                    }
                }
                return 0;
            }
        };
    }

    private AiTicketFlowDefinition rule(String action, String target, String role)
    {
        AiTicketFlowDefinition d = new AiTicketFlowDefinition();
        d.setFlowCode("HOTLINE");
        d.setStatusFrom("0");
        d.setActionCode(action);
        d.setActionName(action);
        d.setTargetStatus(target);
        d.setRoleKey(role);
        d.setStatus("0");
        return d;
    }
}
