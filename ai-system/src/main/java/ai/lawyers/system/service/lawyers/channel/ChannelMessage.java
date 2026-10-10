package ai.lawyers.system.service.lawyers.channel;

import java.util.Date;

/**
 * P3-#16（全渠道 G9）：跨渠道归一化入站消息。
 *
 * <p>各渠道回调经 {@link ChannelAdapter} 验签/解密/归一后产出本对象，
 * 下游统一按 channelType + channelUid 路由（复用咨询/工单/ACD 体系），
 * 与 F6 渠道字典（PHONE/WECHAT_MP/WECHAT_MINI/H5/WEB）对齐。</p>
 *
 * @author ai-lawyers
 */
public class ChannelMessage
{
    /** 渠道编码（WECHAT_MP/WECHAT_MINI/...，对齐 F6 渠道字典） */
    private String channelType;

    /** 渠道身份标识（openid/unionid/账号） */
    private String channelUid;

    /** 渠道昵称（可空） */
    private String channelNickname;

    /** 消息类型：TEXT/IMAGE/VOICE/EVENT */
    private String msgType;

    /** 文本内容；EVENT 时为事件 key（如 subscribe） */
    private String content;

    /** 渠道消息ID（幂等键；EVENT 类可能为空） */
    private String msgId;

    /** 渠道侧创建时间（秒级时间戳） */
    private Long createTimeSec;

    /** 归一时间 */
    private Date receivedTime;

    /** 原始明文报文（审计留存；不含加密层） */
    private String rawPayload;

    public String getChannelType() { return channelType; }

    public void setChannelType(String channelType) { this.channelType = channelType; }

    public String getChannelUid() { return channelUid; }

    public void setChannelUid(String channelUid) { this.channelUid = channelUid; }

    public String getChannelNickname() { return channelNickname; }

    public void setChannelNickname(String channelNickname) { this.channelNickname = channelNickname; }

    public String getMsgType() { return msgType; }

    public void setMsgType(String msgType) { this.msgType = msgType; }

    public String getContent() { return content; }

    public void setContent(String content) { this.content = content; }

    public String getMsgId() { return msgId; }

    public void setMsgId(String msgId) { this.msgId = msgId; }

    public Long getCreateTimeSec() { return createTimeSec; }

    public void setCreateTimeSec(Long createTimeSec) { this.createTimeSec = createTimeSec; }

    public Date getReceivedTime() { return receivedTime; }

    public void setReceivedTime(Date receivedTime) { this.receivedTime = receivedTime; }

    public String getRawPayload() { return rawPayload; }

    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
}
