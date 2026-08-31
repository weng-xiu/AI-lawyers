package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 消息中心-站内信对象 ai_message
 *
 * <p>待办/预警/质检驳回/工单分配等事件经 message-notify 队列异步入库，
 * 按 receiverUserId（sys_user.user_id）定向，顶部铃铛展示未读数。</p>
 *
 * @author ai-lawyers
 */
public class AiMessage extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long messageId;

    /** 接收人用户ID */
    @Excel(name = "接收人ID")
    private Long receiverUserId;

    /** 消息类型（1系统通知 2待办提醒 3风险预警 4质检通知 5工单通知 9其他） */
    @Excel(name = "类型", readConverterExp = "1=系统通知,2=待办提醒,3=风险预警,4=质检通知,5=工单通知,9=其他")
    private String msgType;

    @Excel(name = "标题")
    private String title;

    @Excel(name = "内容")
    private String content;

    /** 业务类型（quality/ticket/warning/outbound），用于前端跳转 */
    private String bizType;

    /** 业务ID（跳转目标主键） */
    private Long bizId;

    /** 是否已读（0未读 1已读） */
    @Excel(name = "是否已读", readConverterExp = "0=未读,1=已读")
    private String isRead;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date readTime;

    /** 发送方 */
    private String sender;

    /** 优先级（1高 2中 3低） */
    private String priority;

    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }

    public Long getReceiverUserId() { return receiverUserId; }
    public void setReceiverUserId(Long receiverUserId) { this.receiverUserId = receiverUserId; }

    public String getMsgType() { return msgType; }
    public void setMsgType(String msgType) { this.msgType = msgType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }

    public Long getBizId() { return bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }

    public String getIsRead() { return isRead; }
    public void setIsRead(String isRead) { this.isRead = isRead; }

    public Date getReadTime() { return readTime; }
    public void setReadTime(Date readTime) { this.readTime = readTime; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("messageId", getMessageId())
            .append("receiverUserId", getReceiverUserId())
            .append("msgType", getMsgType())
            .append("title", getTitle())
            .append("content", getContent())
            .append("bizType", getBizType())
            .append("bizId", getBizId())
            .append("isRead", getIsRead())
            .append("readTime", getReadTime())
            .append("sender", getSender())
            .append("priority", getPriority())
            .append("createTime", getCreateTime())
            .toString();
    }
}
