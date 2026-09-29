package ai.lawyers.system.service.lawyers.trunk.event;

import java.util.Map;
import java.util.UUID;

import ai.lawyers.common.utils.StringUtils;

/**
 * P3-B2：统一 PBX 呼叫事件模型（文档第十四部分 4.2 节 B2 定义）。
 *
 * <p>ESL（FreeSWITCH）/ AMI（Asterisk）两侧桥接服务把各自 PBX 原始事件归一为本模型后，
 * 统一投递 {@code stream:call-event}（经 {@link CallEventBus}），由消费侧
 * {@link CallEventProcessor} 驱动 B4 状态机、坐席推送、指标与自动化工单。
 * B5 只读模型 / 大屏消费组另行排期。</p>
 *
 * <p>归一事件名集合（{@code eventName} 取值）：</p>
 * <ul>
 *   <li>{@link #INBOUND}：入站来话（ESL CHANNEL_CREATE[inbound] / AMI Newchannel[非外呼]）；</li>
 *   <li>{@link #RINGING}：外呼振铃（AMI Dial[Begin]；ESL 侧拨号日志由调度服务自身写）；</li>
 *   <li>{@link #ANSWERED}：接通（ESL CHANNEL_ANSWER / AMI Newstate[Up]）；</li>
 *   <li>{@link #BRIDGED}：桥接（ESL CHANNEL_BRIDGE / AMI BridgeEnter）；</li>
 *   <li>{@link #HANGUP}：挂断（ESL CHANNEL_HANGUP[_COMPLETE] / AMI Hangup）；</li>
 *   <li>{@link #DTMF}：按键（ESL DTMF）；</li>
 *   <li>{@link #RECORD_STOP}：录音停止（ESL RECORD_STOP）；</li>
 *   <li>{@link #AGENT_STATUS}：队列成员设备态同步坐席状态（AMI QueueMember[Status]）。</li>
 * </ul>
 *
 * @author ai-lawyers
 */
public class CallEvent
{
    /** 归一事件名：入站来话 */
    public static final String INBOUND = "INBOUND";
    /** 归一事件名：外呼振铃 */
    public static final String RINGING = "RINGING";
    /** 归一事件名：接通 */
    public static final String ANSWERED = "ANSWERED";
    /** 归一事件名：桥接 */
    public static final String BRIDGED = "BRIDGED";
    /** 归一事件名：挂断（终态） */
    public static final String HANGUP = "HANGUP";
    /** 归一事件名：DTMF 按键 */
    public static final String DTMF = "DTMF";
    /** 归一事件名：录音停止 */
    public static final String RECORD_STOP = "RECORD_STOP";
    /** 归一事件名：队列成员设备态 → 坐席状态同步 */
    public static final String AGENT_STATUS = "AGENT_STATUS";

    /** 事件唯一 ID（生产侧生成 UUID，供追踪/对账） */
    private String eventId = UUID.randomUUID().toString();

    /** 事件来源："ESL:host" / "AMI:host"（HTTP 预留） */
    private String source;

    /** 归一事件名（见常量） */
    private String eventName;

    /** 会话 ID = 解析后的业务 callUuid（AMI 侧经 leg 关联表解析；入站可为 PBX 通道号） */
    private String sessionId;

    /** 关联话单 ID（生产侧能解析到时透传，消费侧可再反查补全） */
    private Long recordId;

    /** PBX 通道标识：ESL=通道 UUID；AMI=Asterisk UniqueID（当前 leg） */
    private String channel;

    /** 关联腿标识：AMI Linkedid / 被叫 DestUniqueid；ESL 可空 */
    private String linkedid;

    /** 主叫号码 */
    private String caller;

    /** 被叫号码 */
    private String callee;

    /** 事件发生时间（生产侧本机毫秒） */
    private long timestamp = System.currentTimeMillis();

    /** 扩展负载：rawEventName、挂断原因、通话时长、DTMF 按键、录音路径等派生参数 */
    private Map<String, Object> payload;

    public String getEventId()
    {
        return eventId;
    }

    public void setEventId(String eventId)
    {
        this.eventId = eventId;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getEventName()
    {
        return eventName;
    }

    public void setEventName(String eventName)
    {
        this.eventName = eventName;
    }

    public String getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(String sessionId)
    {
        this.sessionId = sessionId;
    }

    public Long getRecordId()
    {
        return recordId;
    }

    public void setRecordId(Long recordId)
    {
        this.recordId = recordId;
    }

    public String getChannel()
    {
        return channel;
    }

    public void setChannel(String channel)
    {
        this.channel = channel;
    }

    public String getLinkedid()
    {
        return linkedid;
    }

    public void setLinkedid(String linkedid)
    {
        this.linkedid = linkedid;
    }

    public String getCaller()
    {
        return caller;
    }

    public void setCaller(String caller)
    {
        this.caller = caller;
    }

    public String getCallee()
    {
        return callee;
    }

    public void setCallee(String callee)
    {
        this.callee = callee;
    }

    public long getTimestamp()
    {
        return timestamp;
    }

    public void setTimestamp(long timestamp)
    {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getPayload()
    {
        return payload;
    }

    public void setPayload(Map<String, Object> payload)
    {
        this.payload = payload;
    }

    /** 从 payload 取 String 值（不存在/非字符串返回 null） */
    public String payloadString(String key)
    {
        if (payload == null || key == null)
        {
            return null;
        }
        Object v = payload.get(key);
        return v == null ? null : String.valueOf(v);
    }

    /** 从 payload 取 int 值（不存在/不可解析返回 def） */
    public int payloadInt(String key, int def)
    {
        if (payload == null || key == null)
        {
            return def;
        }
        Object v = payload.get(key);
        if (v instanceof Number)
        {
            return ((Number) v).intValue();
        }
        if (v != null)
        {
            try
            {
                return Integer.parseInt(String.valueOf(v));
            }
            catch (NumberFormatException ignored)
            {
            }
        }
        return def;
    }

    /** 从 payload 取布尔旗标（不存在返回 false） */
    public boolean payloadBool(String key)
    {
        if (payload == null || key == null)
        {
            return false;
        }
        Object v = payload.get(key);
        if (v instanceof Boolean)
        {
            return (Boolean) v;
        }
        return "true".equalsIgnoreCase(String.valueOf(v));
    }

    /** 便捷构造：来源 + 归一事件名 + 会话 ID */
    public static CallEvent of(String source, String eventName, String sessionId)
    {
        CallEvent e = new CallEvent();
        e.setSource(source);
        e.setEventName(eventName);
        e.setSessionId(sessionId);
        return e;
    }

    /** 判空辅助：会话 ID 是否有值（供两侧桥与消费侧复用） */
    public boolean hasSession()
    {
        return StringUtils.isNotEmpty(sessionId);
    }
}
