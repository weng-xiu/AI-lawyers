package ai.lawyers.system.service.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ai.lawyers.system.domain.lawyers.AiTicketFlowDefinition;
import ai.lawyers.system.mapper.lawyers.AiTicketFlowDefinitionMapper;

/**
 * P1-6：工单流转状态机单测——角色过滤、动作校验。用手写 fake mapper，无 Mockito 依赖。
 *
 * @author ai-lawyers
 */
class TicketFlowServiceTest
{
    private TicketFlowService service;

    @BeforeEach
    void setUp()
    {
        service = new TicketFlowService();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "flowMapper", fakeMapper());
        // ticketMapper 仅 applyAction 推进状态时使用，本类用例不触发，不注入
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
