package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 图文会话对象 ai_chat_session
 *
 * @author ai-lawyers
 */
public class AiChatSession extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 会话ID */
    private Long sessionId;

    /** 会话编号 */
    @Excel(name = "会话编号")
    private String sessionNo;

    /** 客户姓名 */
    @Excel(name = "客户姓名")
    private String customerName;

    /** 客户电话 */
    @Excel(name = "客户电话")
    private String customerPhone;

    /** 客户头像URL */
    private String customerAvatar;

    /** 受理人ID */
    private Long userId;

    /** 受理人 */
    @Excel(name = "受理人")
    private String assignee;

    /** 渠道（1图文 2视频辅助） */
    @Excel(name = "渠道", readConverterExp = "1=图文,2=视频辅助")
    private String channel;

    /** 状态（0进行中 1已结束 2转人工） */
    @Excel(name = "状态", readConverterExp = "0=进行中,1=已结束,2=转人工")
    private String status;

    /** 最后一条消息摘要 */
    @Excel(name = "最后消息")
    private String lastMessage;

    /** 最后消息时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "最后消息时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date lastMessageTime;

    /** 未读消息数 */
    private Integer unreadCount;

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
    }

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setSessionNo(String sessionNo)
    {
        this.sessionNo = sessionNo;
    }

    public String getSessionNo()
    {
        return sessionNo;
    }

    public void setCustomerName(String customerName)
    {
        this.customerName = customerName;
    }

    public String getCustomerName()
    {
        return customerName;
    }

    public void setCustomerPhone(String customerPhone)
    {
        this.customerPhone = customerPhone;
    }

    public String getCustomerPhone()
    {
        return customerPhone;
    }

    public void setCustomerAvatar(String customerAvatar)
    {
        this.customerAvatar = customerAvatar;
    }

    public String getCustomerAvatar()
    {
        return customerAvatar;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setAssignee(String assignee)
    {
        this.assignee = assignee;
    }

    public String getAssignee()
    {
        return assignee;
    }

    public void setChannel(String channel)
    {
        this.channel = channel;
    }

    public String getChannel()
    {
        return channel;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setLastMessage(String lastMessage)
    {
        this.lastMessage = lastMessage;
    }

    public String getLastMessage()
    {
        return lastMessage;
    }

    public void setLastMessageTime(Date lastMessageTime)
    {
        this.lastMessageTime = lastMessageTime;
    }

    public Date getLastMessageTime()
    {
        return lastMessageTime;
    }

    public void setUnreadCount(Integer unreadCount)
    {
        this.unreadCount = unreadCount;
    }

    public Integer getUnreadCount()
    {
        return unreadCount;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("sessionId", getSessionId())
            .append("sessionNo", getSessionNo())
            .append("customerName", getCustomerName())
            .append("customerPhone", getCustomerPhone())
            .append("customerAvatar", getCustomerAvatar())
            .append("userId", getUserId())
            .append("assignee", getAssignee())
            .append("channel", getChannel())
            .append("status", getStatus())
            .append("lastMessage", getLastMessage())
            .append("lastMessageTime", getLastMessageTime())
            .append("unreadCount", getUnreadCount())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
