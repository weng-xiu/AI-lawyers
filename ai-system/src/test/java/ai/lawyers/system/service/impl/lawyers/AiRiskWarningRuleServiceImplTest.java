package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiRiskWarningRule;
import ai.lawyers.system.mapper.lawyers.AiRiskWarningRuleMapper;

/**
 * 风险预警规则服务单测。
 *
 * <p>覆盖规则查询、修改、删除，以及新增时默认启用状态与预警等级的填充。</p>
 *
 * @author ai-lawyers
 */
class AiRiskWarningRuleServiceImplTest
{
    private AiRiskWarningRuleServiceImpl service;

    @Mock private AiRiskWarningRuleMapper aiRiskWarningRuleMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiRiskWarningRuleServiceImpl();
        ReflectionTestUtils.setField(service, "aiRiskWarningRuleMapper", aiRiskWarningRuleMapper);
    }

    @Test
    void selectAiRiskWarningRuleByRuleId_delegates()
    {
        AiRiskWarningRule expected = new AiRiskWarningRule();
        when(aiRiskWarningRuleMapper.selectAiRiskWarningRuleByRuleId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiRiskWarningRuleByRuleId(1L));
    }

    @Test
    void selectAiRiskWarningRuleList_delegates()
    {
        AiRiskWarningRule query = new AiRiskWarningRule();
        List<AiRiskWarningRule> list = Collections.singletonList(new AiRiskWarningRule());
        when(aiRiskWarningRuleMapper.selectAiRiskWarningRuleList(query)).thenReturn(list);

        assertSame(list, service.selectAiRiskWarningRuleList(query));
    }

    @Test
    void insertAiRiskWarningRule_emptyFields_fillsDefaults()
    {
        AiRiskWarningRule rule = new AiRiskWarningRule();
        rule.setIsEnabled("");
        rule.setRuleLevel(null);
        when(aiRiskWarningRuleMapper.insertAiRiskWarningRule(any(AiRiskWarningRule.class))).thenReturn(1);

        assertEquals(1, service.insertAiRiskWarningRule(rule));

        assertEquals("1", rule.getIsEnabled());
        assertEquals("3", rule.getRuleLevel());
        verify(aiRiskWarningRuleMapper).insertAiRiskWarningRule(rule);
    }

    @Test
    void insertAiRiskWarningRule_providedValues_kept()
    {
        AiRiskWarningRule rule = new AiRiskWarningRule();
        rule.setIsEnabled("0");
        rule.setRuleLevel("1");
        when(aiRiskWarningRuleMapper.insertAiRiskWarningRule(rule)).thenReturn(1);

        assertEquals(1, service.insertAiRiskWarningRule(rule));

        assertEquals("0", rule.getIsEnabled());
        assertEquals("1", rule.getRuleLevel());
    }

    @Test
    void updateAiRiskWarningRule_delegates()
    {
        AiRiskWarningRule rule = new AiRiskWarningRule();
        when(aiRiskWarningRuleMapper.updateAiRiskWarningRule(rule)).thenReturn(1);

        assertEquals(1, service.updateAiRiskWarningRule(rule));
    }

    @Test
    void deleteAiRiskWarningRuleByRuleIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiRiskWarningRuleMapper.deleteAiRiskWarningRuleByRuleIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiRiskWarningRuleByRuleIds(ids));
    }
}
