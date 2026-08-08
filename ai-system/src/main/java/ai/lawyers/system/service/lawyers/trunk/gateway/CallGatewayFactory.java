package ai.lawyers.system.service.lawyers.trunk.gateway;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;

/**
 * 网关适配器工厂
 *
 * 启动时收集容器中全部 {@link ICallGatewayAdapter} 实现，按 vendor 建立索引。
 * 选路完成后由调度器根据线路的 vendor 字段取出对应适配器执行拨号。
 */
@Component
public class CallGatewayFactory
{
    private static final Logger log = LoggerFactory.getLogger(CallGatewayFactory.class);

    /** 找不到匹配厂商时使用的兜底适配器 */
    private static final String FALLBACK_VENDOR = "SIMULATOR";

    @Autowired(required = false)
    private List<ICallGatewayAdapter> adapters;

    private final Map<String, ICallGatewayAdapter> adapterMap = new HashMap<>();

    @PostConstruct
    public void init()
    {
        if (adapters == null || adapters.isEmpty())
        {
            log.warn("未发现任何语音网关适配器实现，外呼功能不可用");
            return;
        }
        for (ICallGatewayAdapter adapter : adapters)
        {
            String vendor = adapter.getVendor();
            if (vendor == null || vendor.trim().isEmpty())
            {
                log.warn("适配器 {} 未声明 vendor，已忽略", adapter.getClass().getName());
                continue;
            }
            adapterMap.put(vendor.trim().toUpperCase(), adapter);
            log.info("注册语音网关适配器: vendor={}, class={}", vendor, adapter.getClass().getSimpleName());
        }
    }

    /**
     * 按线路取适配器，找不到时回退到模拟器，保证系统不因配置错误而崩溃。
     */
    public ICallGatewayAdapter get(AiCallTrunk trunk)
    {
        String vendor = trunk == null ? null : trunk.getVendor();
        return getByVendor(vendor);
    }

    public ICallGatewayAdapter getByVendor(String vendor)
    {
        if (vendor != null && !vendor.trim().isEmpty())
        {
            ICallGatewayAdapter adapter = adapterMap.get(vendor.trim().toUpperCase());
            if (adapter != null)
            {
                return adapter;
            }
            log.warn("未找到 vendor={} 的网关适配器，回退到 {}", vendor, FALLBACK_VENDOR);
        }
        return adapterMap.get(FALLBACK_VENDOR);
    }

    public Map<String, ICallGatewayAdapter> getAll()
    {
        return adapterMap;
    }
}
