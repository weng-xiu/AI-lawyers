package ai.lawyers.system.service.lawyers.trunk.gateway.ami;

import java.util.HashMap;
import java.util.Map;

/**
 * B1：AMI 事件块解析器。
 *
 * <p>AMI 块为若干行 {@code Key: Value}，以空行结束。仅当块中存在
 * {@code Event: <name>} 头时才认定为事件；{@code Response: ...} 动作响应块、
 * banner、空块一律返回 null（调用方忽略）。</p>
 *
 * <p>无冒号的行（协议噪声）跳过；重复键保留首值。纯函数、无状态，可安全并发。</p>
 *
 * @author ai-lawyers
 */
public final class AmiEventParser
{
    private AmiEventParser() {}

    /**
     * 解析一个 AMI 块。
     *
     * @param block 以空行结束的块文本（各行 \n 分隔，可含结尾空行）
     * @return 事件对象；非事件块返回 null
     */
    public static AmiEvent parse(String block)
    {
        if (block == null || block.trim().isEmpty())
        {
            return null;
        }
        String name = null;
        Map<String, String> headers = new HashMap<>();
        for (String raw : block.split("\n"))
        {
            String line = raw.trim();
            if (line.isEmpty())
            {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0)
            {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = colon + 1 <= line.length() ? line.substring(colon + 1).trim() : "";
            if ("Event".equalsIgnoreCase(key))
            {
                if (value.isEmpty())
                {
                    // Event: 空值（如 Response 附属的 EventList 结构），不作为可路由事件
                    return null;
                }
                name = value;
                continue;
            }
            headers.putIfAbsent(key, value);
        }
        return name == null ? null : new AmiEvent(name, headers);
    }
}
