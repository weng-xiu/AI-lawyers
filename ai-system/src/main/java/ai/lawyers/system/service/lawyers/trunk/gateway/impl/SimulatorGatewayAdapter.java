package ai.lawyers.system.service.lawyers.trunk.gateway.impl;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.domain.lawyers.trunk.DialRequest;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.enums.DialStatusEnum;
import ai.lawyers.system.service.lawyers.trunk.gateway.GatewayHealth;
import ai.lawyers.system.service.lawyers.trunk.gateway.ICallGatewayAdapter;
import ai.lawyers.system.utils.trunk.NumberTransformUtils;

/**
 * 模拟网关适配器
 *
 * 作用：
 *   1. 开发/测试环境下无真实语音网关时仍可跑通完整外呼链路；
 *   2. 作为 {@code CallGatewayFactory} 的兜底实现，防止 vendor 配置错误导致 NPE。
 *
 * 通过 call.gateway.simulator.successRate 控制模拟成功率，可用于验证
 * 故障自动切换与告警逻辑。
 */
@Component
public class SimulatorGatewayAdapter implements ICallGatewayAdapter
{
    private static final Logger log = LoggerFactory.getLogger(SimulatorGatewayAdapter.class);

    /** 模拟下发成功率(0~100)，默认 100 表示总是成功 */
    @Value("${call.gateway.simulator.successRate:100}")
    private int successRate;

    @Override
    public String getVendor()
    {
        return "SIMULATOR";
    }

    @Override
    public DialResult originate(AiCallTrunk trunk, DialRequest request)
    {
        String callUuid = UUID.randomUUID().toString();
        String callee = NumberTransformUtils.transform(request.getCalleeNumber(), trunk);

        boolean ok = successRate >= 100 || ThreadLocalRandom.current().nextInt(100) < successRate;
        if (ok)
        {
            log.info("[SIMULATOR] 模拟外呼成功 trunk={} callee={} uuid={}",
                    trunk.getTrunkCode(), NumberTransformUtils.mask(callee), callUuid);
            DialResult result = DialResult.ok(callUuid);
            result.setDialStatus(DialStatusEnum.DIALING.getCode());
            return result;
        }

        log.info("[SIMULATOR] 模拟外呼失败 trunk={} callee={}",
                trunk.getTrunkCode(), NumberTransformUtils.mask(callee));
        DialResult fail = DialResult.fail("SIMULATED_FAIL", "模拟网关随机失败");
        fail.setDialStatus(DialStatusEnum.FAILED.getCode());
        fail.setHangupCause("NORMAL_TEMPORARY_FAILURE");
        return fail;
    }

    @Override
    public boolean hangup(AiCallTrunk trunk, String callUuid)
    {
        log.info("[SIMULATOR] 模拟挂断 uuid={}", callUuid);
        return true;
    }

    @Override
    public boolean bridgeToAgent(AiCallTrunk trunk, String callUuid, String extension)
    {
        log.info("[SIMULATOR] 模拟转坐席 uuid={} ext={}", callUuid, extension);
        return true;
    }

    @Override
    public GatewayHealth checkHealth(AiCallTrunk trunk)
    {
        GatewayHealth health = GatewayHealth.up(1L);
        health.setMessage("模拟网关（无真实线路），仅用于开发联调");
        return health;
    }
}
