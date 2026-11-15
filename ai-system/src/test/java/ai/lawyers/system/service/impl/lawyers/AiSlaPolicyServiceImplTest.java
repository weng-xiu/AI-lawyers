package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.domain.lawyers.AiSlaPolicy;
import ai.lawyers.system.mapper.lawyers.AiSlaPolicyMapper;

/**
 * 工单 SLA 策略服务单测。
 *
 * <p>覆盖策略查询与删除委托、新增时状态默认值、新增/修改的字段校验规则，
 * 以及匹配策略时业务类型与优先级的默认值处理。</p>
 *
 * @author ai-lawyers
 */
class AiSlaPolicyServiceImplTest
{
    private AiSlaPolicyServiceImpl service;

    @Mock private AiSlaPolicyMapper aiSlaPolicyMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiSlaPolicyServiceImpl();
        ReflectionTestUtils.setField(service, "aiSlaPolicyMapper", aiSlaPolicyMapper);
    }

    private static AiSlaPolicy policy(Long policyId, String name, String bizType, String priority,
                                      Integer respond, Integer resolve)
    {
        AiSlaPolicy policy = new AiSlaPolicy();
        policy.setPolicyId(policyId);
        policy.setPolicyName(name);
        policy.setBizType(bizType);
        policy.setPriority(priority);
        policy.setRespondMinutes(respond);
        policy.setResolveMinutes(resolve);
        return policy;
    }

    @Test
    void selectAiSlaPolicyByPolicyId_delegates()
    {
        AiSlaPolicy expected = new AiSlaPolicy();
        when(aiSlaPolicyMapper.selectAiSlaPolicyByPolicyId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiSlaPolicyByPolicyId(1L));
    }

    @Test
    void selectAiSlaPolicyList_delegates()
    {
        AiSlaPolicy query = new AiSlaPolicy();
        List<AiSlaPolicy> list = Collections.singletonList(new AiSlaPolicy());
        when(aiSlaPolicyMapper.selectAiSlaPolicyList(query)).thenReturn(list);

        assertSame(list, service.selectAiSlaPolicyList(query));
    }

    @Test
    void insertAiSlaPolicy_valid_fillsDefaultStatus()
    {
        AiSlaPolicy policy = policy(null, "标准", "TICKET", "2", 10, 60);
        when(aiSlaPolicyMapper.insertAiSlaPolicy(policy)).thenReturn(1);

        assertEquals(1, service.insertAiSlaPolicy(policy));
        assertEquals("0", policy.getStatus());
    }

    @Test
    void insertAiSlaPolicy_null_throws()
    {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.insertAiSlaPolicy(null));
        assertEquals("策略信息不能为空", ex.getMessage());
    }

    @Test
    void insertAiSlaPolicy_missingName_throws()
    {
        AiSlaPolicy policy = policy(null, "", "TICKET", "2", 10, 60);
        assertEquals("策略名称不能为空",
            assertThrows(ServiceException.class, () -> service.insertAiSlaPolicy(policy)).getMessage());
    }

    @Test
    void insertAiSlaPolicy_missingBizTypeOrPriority_throws()
    {
        AiSlaPolicy noBiz = policy(null, "标准", "", "2", 10, 60);
        assertEquals("业务类型与优先级不能为空",
            assertThrows(ServiceException.class, () -> service.insertAiSlaPolicy(noBiz)).getMessage());

        AiSlaPolicy noPriority = policy(null, "标准", "TICKET", "", 10, 60);
        assertEquals("业务类型与优先级不能为空",
            assertThrows(ServiceException.class, () -> service.insertAiSlaPolicy(noPriority)).getMessage());
    }

    @Test
    void insertAiSlaPolicy_invalidRespondMinutes_throws()
    {
        AiSlaPolicy zero = policy(null, "标准", "TICKET", "2", 0, 60);
        assertEquals("响应时限必须为正整数（分钟）",
            assertThrows(ServiceException.class, () -> service.insertAiSlaPolicy(zero)).getMessage());

        AiSlaPolicy nullValue = policy(null, "标准", "TICKET", "2", null, 60);
        assertEquals("响应时限必须为正整数（分钟）",
            assertThrows(ServiceException.class, () -> service.insertAiSlaPolicy(nullValue)).getMessage());
    }

    @Test
    void insertAiSlaPolicy_invalidResolveMinutes_throws()
    {
        AiSlaPolicy zero = policy(null, "标准", "TICKET", "2", 10, 0);
        assertEquals("办结时限必须为正整数（分钟）",
            assertThrows(ServiceException.class, () -> service.insertAiSlaPolicy(zero)).getMessage());
    }

    @Test
    void insertAiSlaPolicy_resolveLessThanRespond_throws()
    {
        AiSlaPolicy policy = policy(null, "标准", "TICKET", "2", 60, 30);
        assertEquals("办结时限不能小于响应时限",
            assertThrows(ServiceException.class, () -> service.insertAiSlaPolicy(policy)).getMessage());
        verify(aiSlaPolicyMapper, never()).insertAiSlaPolicy(any());
    }

    @Test
    void updateAiSlaPolicy_missingId_throws()
    {
        AiSlaPolicy policy = policy(null, "标准", "TICKET", "2", 10, 60);
        assertEquals("策略ID不能为空",
            assertThrows(ServiceException.class, () -> service.updateAiSlaPolicy(policy)).getMessage());
    }

    @Test
    void updateAiSlaPolicy_valid_delegates()
    {
        AiSlaPolicy policy = policy(1L, "标准", "TICKET", "2", 10, 60);
        when(aiSlaPolicyMapper.updateAiSlaPolicy(policy)).thenReturn(1);

        assertEquals(1, service.updateAiSlaPolicy(policy));
    }

    @Test
    void deleteAiSlaPolicyByPolicyIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiSlaPolicyMapper.deleteAiSlaPolicyByPolicyIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiSlaPolicyByPolicyIds(ids));
    }

    @Test
    void matchPolicy_defaultsBizTypeAndPriority()
    {
        AiSlaPolicy expected = new AiSlaPolicy();
        when(aiSlaPolicyMapper.selectMatchedPolicy("TICKET", "2")).thenReturn(expected);

        assertSame(expected, service.matchPolicy(null, ""));
    }

    @Test
    void matchPolicy_passesThroughExplicitValues()
    {
        AiSlaPolicy expected = new AiSlaPolicy();
        when(aiSlaPolicyMapper.selectMatchedPolicy("CONSULT", "1")).thenReturn(expected);

        assertSame(expected, service.matchPolicy("CONSULT", "1"));
    }
}
