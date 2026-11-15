package ai.lawyers.system.service.lawyers.trunk.gateway.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.domain.lawyers.trunk.DialRequest;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.enums.DialStatusEnum;
import ai.lawyers.system.service.lawyers.trunk.gateway.GatewayHealth;

/**
 * {@link SimulatorGatewayAdapter} 模拟网关适配器测试。
 *
 * <p>覆盖 vendor 标识、模拟外呼成功/失败结果（状态、错误码、挂断原因）、
 * 挂断与转坐席恒成功、健康探测固定上报可达。</p>
 *
 * @author ai-lawyers
 */
class SimulatorGatewayAdapterTest
{
    private SimulatorGatewayAdapter adapter;

    private AiCallTrunk trunk;

    @BeforeEach
    void setUp()
    {
        adapter = new SimulatorGatewayAdapter();
        trunk = Mockito.mock(AiCallTrunk.class);
        ReflectionTestUtils.setField(adapter, "successRate", 100);
    }

    @Test
    void vendorIsSimulator()
    {
        assertThat(adapter.getVendor()).isEqualTo("SIMULATOR");
    }

    @Test
    void originateSucceedsWithDialingStatus()
    {
        DialRequest request = new DialRequest();
        request.setCalleeNumber("138-0013-8000");

        DialResult result = adapter.originate(trunk, request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCallUuid()).isNotBlank();
        assertThat(result.getDialStatus()).isEqualTo(DialStatusEnum.DIALING.getCode());
        assertThat(result.getDialTime()).isNotNull();
    }

    @Test
    void originateFailsWhenSuccessRateZero()
    {
        ReflectionTestUtils.setField(adapter, "successRate", 0);
        DialRequest request = new DialRequest();
        request.setCalleeNumber("13800138000");

        DialResult result = adapter.originate(trunk, request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("SIMULATED_FAIL");
        assertThat(result.getMessage()).isEqualTo("模拟网关随机失败");
        assertThat(result.getDialStatus()).isEqualTo(DialStatusEnum.FAILED.getCode());
        assertThat(result.getHangupCause()).isEqualTo("NORMAL_TEMPORARY_FAILURE");
    }

    @Test
    void originateTreatsOverHundredAsAlwaysSuccess()
    {
        ReflectionTestUtils.setField(adapter, "successRate", 150);

        assertThat(adapter.originate(trunk, new DialRequest()).isSuccess()).isTrue();
    }

    @Test
    void hangupAndBridgeAlwaysSucceed()
    {
        assertThat(adapter.hangup(trunk, "uuid-1")).isTrue();
        assertThat(adapter.bridgeToAgent(trunk, "uuid-1", "8001")).isTrue();
    }

    @Test
    void checkHealthReportsReachable()
    {
        GatewayHealth health = adapter.checkHealth(trunk);

        assertThat(health.isReachable()).isTrue();
        assertThat(health.getLatencyMs()).isEqualTo(1L);
        assertThat(health.getRegisterState()).isEqualTo("REGED");
        assertThat(health.getMessage()).contains("模拟网关");
    }
}
