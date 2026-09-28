package ai.lawyers.system.service.lawyers.trunk.gateway.ami;

import java.util.Map;

/**
 * B1：解析后的 AMI 事件（一个以空行结束的事件块）。
 *
 * <p>AMI 头部大小写在不同 Asterisk 版本/事件上不一致（{@code Uniqueid} /
 * {@code UniqueID} / {@code CallerIDNum} / {@code CallerIdNum} 等），
 * {@link #get(String)} 做忽略大小写匹配，调用方按惯例驼峰键名取值即可。</p>
 *
 * @author ai-lawyers
 */
public class AmiEvent
{
    private final String name;
    private final Map<String, String> headers;

    public AmiEvent(String name, Map<String, String> headers)
    {
        this.name = name;
        this.headers = headers == null ? java.util.Collections.emptyMap() : headers;
    }

    /** 事件名（Event 头的值），如 Newchannel / Dial / Hangup */
    public String getName()
    {
        return name;
    }

    /**
     * 按键取头部值（忽略大小写）。
     *
     * @param key 头部键（如 Uniqueid / CallerIDNum / AI_CALL_UUID）
     * @return 头部值；不存在返回 null
     */
    public String get(String key)
    {
        if (key == null)
        {
            return null;
        }
        for (Map.Entry<String, String> e : headers.entrySet())
        {
            if (key.equalsIgnoreCase(e.getKey()))
            {
                return e.getValue();
            }
        }
        return null;
    }

    /** 全部头部（键为原始大小写），供日志与调试 */
    public Map<String, String> headers()
    {
        return headers;
    }

    @Override
    public String toString()
    {
        return "AmiEvent{" + name + ", " + headers + '}';
    }
}
