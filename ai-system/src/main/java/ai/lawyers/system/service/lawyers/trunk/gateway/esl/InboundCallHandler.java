package ai.lawyers.system.service.lawyers.trunk.gateway.esl;

import java.util.Map;

/**
 * 入站呼叫处理器
 *
 * <p>当 FreeSWITCH ESL 事件桥接服务检测到 {@code Call-Direction=inbound} 的
 * CHANNEL_CREATE 事件时，将主叫/被叫/UUID 等信息交给本接口，实现来电弹屏、
 * 排队分配、转 IVR 等入站业务逻辑。</p>
 *
 * @author ai-lawyers
 */
public interface InboundCallHandler
{
    /**
     * 处理一路入站来话。
     *
     * @param ctx 至少包含：
     *   <ul>
     *     <li>host - FreeSWITCH 主机</li>
     *     <li>uuid - 通话唯一标识</li>
     *     <li>caller - 主叫号码</li>
     *     <li>callee/dnis - 被叫号码（对外号码或 IVR 入口号）</li>
     *   </ul>
     */
    void handleIncomingCall(Map<String, Object> ctx);
}
