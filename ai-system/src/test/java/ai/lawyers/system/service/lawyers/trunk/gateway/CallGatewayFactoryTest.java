package ai.lawyers.system.service.lawyers.trunk.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;

/**
 * {@link CallGatewayFactory} 网关适配器工厂测试。
 *
 * <p>覆盖适配器注册（vendor 大写去空格、空 vendor 忽略、无适配器启动）、
 * 按 trunk/vendor 选路、大小写不敏感匹配以及 SIMULATOR 兜底。</p>
 *
 * @author ai-lawyers
 */
class CallGatewayFactoryTest
{
    @Test
    void initRegistersVendorsCaseInsensitively()
    {
        ICallGatewayAdapter http = adapter(" http_api ");
        ICallGatewayAdapter simulator = adapter("simulator");
        CallGatewayFactory factory = factory(http, simulator);

        assertThat(factory.getAll()).containsOnlyKeys("HTTP_API", "SIMULATOR");
        assertThat(factory.getByVendor("http_api")).isSameAs(http);
        assertThat(factory.getByVendor(" HTTP_API ")).isSameAs(http);
    }

    @Test
    void initIgnoresBlankVendor()
    {
        CallGatewayFactory factory = factory(adapter(null), adapter("   "));

        assertThat(factory.getAll()).isEmpty();
        assertThat(factory.getByVendor("FREESWITCH")).isNull();
    }

    @Test
    void initWithoutAdaptersKeepsEmptyMap()
    {
        CallGatewayFactory factory = new CallGatewayFactory();
        factory.init();

        assertThat(factory.getAll()).isEmpty();
        assertThat(factory.getByVendor("SIMULATOR")).isNull();
    }

    @Test
    void unknownVendorFallsBackToSimulator()
    {
        ICallGatewayAdapter simulator = adapter("SIMULATOR");
        CallGatewayFactory factory = factory(simulator);

        assertThat(factory.getByVendor("FREESWITCH")).isSameAs(simulator);
    }

    @Test
    void blankVendorFallsBackToSimulator()
    {
        ICallGatewayAdapter simulator = adapter("SIMULATOR");
        CallGatewayFactory factory = factory(simulator);

        assertThat(factory.getByVendor(null)).isSameAs(simulator);
        assertThat(factory.getByVendor("  ")).isSameAs(simulator);
    }

    @Test
    void unknownVendorWithoutSimulatorReturnsNull()
    {
        CallGatewayFactory factory = factory(adapter("ASTERISK"));

        assertThat(factory.getByVendor("FREESWITCH")).isNull();
    }

    @Test
    void getResolvesVendorFromTrunk()
    {
        ICallGatewayAdapter http = adapter("HTTP_API");
        CallGatewayFactory factory = factory(http, adapter("SIMULATOR"));
        AiCallTrunk trunk = mock(AiCallTrunk.class);
        when(trunk.getVendor()).thenReturn("http_api");

        assertThat(factory.get(trunk)).isSameAs(http);
        assertThat(factory.get(null)).isSameAs(factory.getByVendor("SIMULATOR"));
    }

    private CallGatewayFactory factory(ICallGatewayAdapter... adapters)
    {
        CallGatewayFactory factory = new CallGatewayFactory();
        ReflectionTestUtils.setField(factory, "adapters", adapters.length == 0
                ? Collections.emptyList() : Arrays.asList(adapters));
        factory.init();
        return factory;
    }

    private ICallGatewayAdapter adapter(String vendor)
    {
        ICallGatewayAdapter adapter = mock(ICallGatewayAdapter.class);
        when(adapter.getVendor()).thenReturn(vendor);
        return adapter;
    }
}
