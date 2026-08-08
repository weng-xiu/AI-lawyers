package ai.lawyers.system.service.lawyers.trunk.gateway;

import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.domain.lawyers.trunk.DialRequest;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;

/**
 * 语音网关 / CTI 中间件适配器
 *
 * 屏蔽 FreeSWITCH(ESL)、Asterisk(AMI)、OpenSIPS、华为/中兴 SBC、以及各类
 * 提供 HTTP OpenAPI 的云 CTI 平台之间的差异。上层调度器只依赖本接口。
 *
 * 新增一种网关只需：
 *   1. 实现本接口；
 *   2. {@link #getVendor()} 返回与 ai_call_trunk.vendor 一致的厂商标识；
 *   3. 声明为 Spring Bean，会被 {@code CallGatewayFactory} 自动注册。
 */
public interface ICallGatewayAdapter
{
    /**
     * 厂商标识，需与 ai_call_trunk.vendor 字段值一致。
     * 例如：FREESWITCH / ASTERISK / OPENSIPS / HTTP_API / SIMULATOR
     */
    String getVendor();

    /**
     * 发起外呼。
     *
     * 实现方只负责"把呼叫下发到网关"，不负责选路、并发控制与日志落库，
     * 这些由 CallDispatchServiceImpl 统一处理。
     *
     * @param trunk   已选定的线路（含网关地址、鉴权、号码变换规则）
     * @param request 外呼请求
     * @return 下发结果，success=true 表示网关已受理
     */
    DialResult originate(AiCallTrunk trunk, DialRequest request);

    /**
     * 挂断指定呼叫。
     *
     * @param trunk    线路
     * @param callUuid 呼叫唯一标识
     * @return 是否成功
     */
    boolean hangup(AiCallTrunk trunk, String callUuid);

    /**
     * 将已接通的呼叫桥接到坐席分机。
     *
     * @param trunk     线路
     * @param callUuid  呼叫唯一标识
     * @param extension 坐席分机号
     * @return 是否成功
     */
    boolean bridgeToAgent(AiCallTrunk trunk, String callUuid, String extension);

    /**
     * 线路健康探测（SIP OPTIONS ping / 网关状态查询 / TCP 连通性）。
     *
     * @param trunk 线路
     * @return 探测结果
     */
    GatewayHealth checkHealth(AiCallTrunk trunk);
}
