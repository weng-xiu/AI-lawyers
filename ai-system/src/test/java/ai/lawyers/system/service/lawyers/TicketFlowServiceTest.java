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
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ai.lawyers.system.domain.lawyers.AiTicketFlowDefinition;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.mapper.lawyers.AiTicketFlowDefinitionMapper;

/**
 * P1-6：工单流转状态机单测——角色过滤、动作校验。用手写 fake mapper，无 Mockito 依赖。
 *
 * @author ai-lawyers
 */
class TicketFlowServiceTest
{
    private TicketFlowService service;
    private AiCallTicketMapper ticketMapper;

    @BeforeEach
    void setUp()
    {
        service = new TicketFlowService();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "flowMapper", fakeMapper());
        ticketMapper = mock(AiCallTicketMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
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

    /** 构造与 seed 一致的 fake 流程 mapper */
    private AiTicketFlowDefinitionMapper fakeMapper()
    {
        return new AiTicketFlowDefinitionMapper()
        {
            @Override
            public List<AiTicketFlowDefinition> selectActions(String flowCode, String statusFrom)
            {
                if ("HOTLINE".equals(flowCode) && "0".equals(statusFrom))
                {
                    return Arrays.asList(rule("start", "1", "agent,leader"),
                            rule("transfer", "0", "leader,admin"));
                }
                return Collections.emptyList();
            }

            @Override
            public List<AiTicketFlowDefinition> selectByFlowCode(String flowCode)
            {
                return Collections.emptyList();
            }

            @Override
            public AiTicketFlowDefinition selectRule(String flowCode, String statusFrom,
                                                     String actionCode)
            {
                for (AiTicketFlowDefinition rule : selectActions(flowCode, statusFrom))
                {
                    if (rule.getActionCode().equals(actionCode))
                    {
                        return rule;
                    }
                }
                return null;
            }

            @Override
            public int insertDefinition(AiTicketFlowDefinition definition) { return 0; }

            @Override
            public int updateDefinition(AiTicketFlowDefinition definition) { return 0; }
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
        return d;
    }
}
