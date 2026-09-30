package ai.lawyers.system.service.lawyers.trunk.gateway.ami.pjsip;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.trunk.PjsipRegistration;
import ai.lawyers.system.service.lawyers.trunk.gateway.ami.PooledAmiClient;

/**
 * P3-B5（V2.53）：PJSIP 端点实时注册态查询服务。
 *
 * <p>数据源为 AMI 命令面动作 {@code PJSIPShowEndpoints}（响应块 + N × EndpointList
 * 事件块 + EndpointListComplete 事件），经 {@link PooledAmiClient#sendActionCollectEvents}
 * 多事件收集后解析为注册态视图，与 {@link PjsipConfigQueryService} 的静态配置视图互补。
 * 平台<b>只查询、只展示，不下发任何配置</b>。</p>
 *
 * <p>可靠性（监控接口不 500）：</p>
 * <ul>
 *   <li>命令面池未启用 / AMI 无响应（Asterisk 未部署、断连、熔断打开、超时）→
 *       返回 {@code available=false} + 原因，不抛异常；</li>
 *   <li>AMI 返回 {@code Response: Error} → {@code available=false} + Message；</li>
 *   <li>解析异常吞掉并标注 {@code error}。</li>
 * </ul>
 *
 * @author ai-lawyers
 */
@Service
public class PjsipRegistrationService
{
    private static final Logger log = LoggerFactory.getLogger(PjsipRegistrationService.class);

    /** PJSIPShowEndpoints 的 Complete 事件名（Asterisk ami_show_endpoints 固定输出） */
    static final String COMPLETE_EVENT = "EndpointListComplete";

    /** 多事件收集超时：端点较多时列表跨多个 TCP 包，给足余量 */
    private static final long COLLECT_TIMEOUT_MS = 15_000L;

    /**
     * 命令面实时查询目标主机（与 Asterisk 同机部署默认 127.0.0.1；
     * 命令面池按 host:amiPort 维护长连接）。
     */
    @Value("${call.gateway.asterisk.host:127.0.0.1}")
    private String amiHost;

    @Autowired
    private PooledAmiClient pooledAmiClient;

    /**
     * 实时注册态查询：{host, queriedAt, available, endpointCount, registrations[]} 或
     * {host, queriedAt, available=false, error}
     */
    public Map<String, Object> listRegistrations()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("host", amiHost);
        result.put("queriedAt", System.currentTimeMillis());
        if (!pooledAmiClient.isEnabled())
        {
            result.put("available", false);
            result.put("error", "命令面连接池未启用（call.gateway.pool.ami-enabled=false）");
            return result;
        }
        String collected;
        try
        {
            collected = pooledAmiClient.sendActionCollectEvents(amiHost,
                    "Action: PJSIPShowEndpoints" + "\r\n" + "\r\n", COMPLETE_EVENT, COLLECT_TIMEOUT_MS);
        }
        catch (Exception e)
        {
            collected = null;
            log.warn("[PJSIP-REG] 实时注册态查询异常 host={} err={}", amiHost, e.getMessage());
        }
        if (collected == null)
        {
            result.put("available", false);
            result.put("error", "AMI 无响应（Asterisk 未部署/命令面断连/熔断打开/超时）");
            return result;
        }
        if (collected.contains("Response: Error"))
        {
            result.put("available", false);
            result.put("error", "AMI 返回错误：" + firstHeaderValue(collected, "Message"));
            return result;
        }
        try
        {
            List<PjsipRegistration> list = parseEndpointList(collected);
            result.put("available", true);
            result.put("endpointCount", list.size());
            result.put("registrations", list);
        }
        catch (Exception e)
        {
            log.warn("[PJSIP-REG] 注册态解析失败 err={}", e.getMessage());
            result.put("available", false);
            result.put("error", "注册态解析异常：" + e.getMessage());
        }
        return result;
    }

    /**
     * 解析多事件收集文本：按空行分块，取 {@code Event: EndpointList} 块组装注册态。
     * 同键多值时首值优先（Contacts 单行输出，无同键场景，防御性处理）。
     */
    static List<PjsipRegistration> parseEndpointList(String collected)
    {
        List<PjsipRegistration> list = new ArrayList<>();
        if (collected == null || collected.isEmpty())
        {
            return list;
        }
        Map<String, String> current = null;
        for (String rawLine : collected.split("\n", -1))
        {
            String line = rawLine.trim();
            if (line.isEmpty())
            {
                if (current != null)
                {
                    list.add(from(current));
                    current = null;
                }
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0)
            {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if ("Event".equals(key))
            {
                if (current != null)
                {
                    list.add(from(current));
                    current = null;
                }
                if ("EndpointList".equals(value))
                {
                    current = new LinkedHashMap<>();
                }
            }
            else if (current != null && !current.containsKey(key))
            {
                current.put(key, value);
            }
        }
        if (current != null)
        {
            list.add(from(current));
        }
        return list;
    }

    /** EndpointList 事件键值 → 注册态视图（Contacts 行格式：多条以空格分隔，每条 "URI,Status"） */
    private static PjsipRegistration from(Map<String, String> event)
    {
        PjsipRegistration reg = new PjsipRegistration();
        reg.setName(event.get("ObjectName"));
        reg.setDeviceState(event.get("DeviceState"));
        reg.setActiveChannels(parseInt(event.get("ActiveChannels")));
        String raw = event.containsKey("Contacts") ? event.get("Contacts") : "";
        reg.setRawContacts(raw);
        List<PjsipRegistration.Contact> contacts = new ArrayList<>();
        for (String token : raw.split("\\s+"))
        {
            if (token.isEmpty())
            {
                continue;
            }
            PjsipRegistration.Contact c = new PjsipRegistration.Contact();
            int comma = token.lastIndexOf(',');
            if (comma > 0)
            {
                c.setUri(token.substring(0, comma));
                c.setStatus(token.substring(comma + 1));
            }
            else
            {
                // 无状态后缀的裸 URI：状态未知，URI 整体保留
                c.setUri(token);
            }
            contacts.add(c);
        }
        reg.setContacts(contacts);
        return reg;
    }

    /** 取指定头的首行值（如 Message），无则返回空串 */
    private static String firstHeaderValue(String text, String header)
    {
        for (String line : text.split("\n"))
        {
            String t = line.trim();
            if (t.startsWith(header + ":"))
            {
                return t.substring(header.length() + 1).trim();
            }
        }
        return "";
    }

    private static Integer parseInt(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return null;
        }
        try
        {
            return Integer.valueOf(value.trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
