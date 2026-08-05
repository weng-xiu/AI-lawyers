package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 聊天消息对象 ai_chat_message
 *
 * @author ai-lawyers
 */
public class AiChatMessage extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 消息ID */
    private Long messageId;

    /** 会话ID */
    private Long sessionId;

    /** 发送者类型（1客户 2客服 3系统） */
    @Excel(name = "发送者类型", readConverterExp = "1=客户,2=客服,3=系统")
    private String senderType;

    /** 发送人名称 */
    @Excel(name = "发送人")
    private String senderName;

    /** 消息内容 */
    @Excel(name = "消息内容")
    private String content;

    /** 消息类型（1文字 2图片 3文件） */
    @Excel(name = "消息类型", readConverterExp = "1=文字,2=图片,3=文件")
    private String msgType;

    /** 附件URL */
    private String attachmentUrl;

    /** 是否已读（0未读 1已读） */
    @Excel(name = "是否已读", readConverterExp = "0=未读,1=已读")
    private String isRead;

    /** 发送时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "发送时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date sendTime;

    public void setMessageId(Long messageId)
    {
        this.messageId = messageId;
    }

    public Long getMessageId()
    {
        return messageId;
    }

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
    }

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setSenderType(String senderType)
    {
        this.senderType = senderType;
    }

    public String getSenderType()
    {
        return senderType;
    }

    public void setSenderName(String senderName)
    {
        this.senderName = senderName;
    }

    public String getSenderName()
    {
        return senderName;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getContent()
    {
        return content;
    }

    public void setMsgType(String msgType)
    {
        this.msgType = msgType;
    }

    public String getMsgType()
    {
        return msgType;
    }

    public void setAttachmentUrl(String attachmentUrl)
    {
        this.attachmentUrl = attachmentUrl;
    }

    public String getAttachmentUrl()
    {
        return attachmentUrl;
    }

    public void setIsRead(String isRead)
    {
        this.isRead = isRead;
    }

    public String getIsRead()
    {
        return isRead;
    }

    public void setSendTime(Date sendTime)
    {
        this.sendTime = sendTime;
    }

    public Date getSendTime()
    {
        return sendTime;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("messageId", getMessageId())
            .append("sessionId", getSessionId())
            .append("senderType", getSenderType())
            .append("senderName", getSenderName())
            .append("content", getContent())
            .append("msgType", getMsgType())
            .append("attachmentUrl", getAttachmentUrl())
            .append("isRead", getIsRead())
            .append("sendTime", getSendTime())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("remark", getRemark())
            .toString();
    }
}
