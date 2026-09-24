package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.annotation.Excel.ColumnType;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 未接来电对象 ai_missed_call
 *
 * @author ai-lawyers
 */
public class AiMissedCall extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 未接来电ID */
    private Long missedCallId;

    /** 来电号码 */
    @Excel(name = "来电号码")
    private String callerNumber;

    /** 来电人姓名 */
    @Excel(name = "来电人姓名")
    private String callerName;

    /** 来电时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "来电时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date callTime;

    /** 状态（0未回拨 1已回拨） */
    @Excel(name = "状态", readConverterExp = "0=未回拨,1=已回拨")
    private String status;

    /** 回拨时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "回拨时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date callbackTime;

    /** 回拨人 */
    @Excel(name = "回拨人")
    private String callbackBy;

    /** 关联来电记录ID */
    private Long recordId;

    /** 语音留言内容 */
    private String voiceContent;

    /** 语音留言时长(秒) */
    private Integer voiceDuration;

    /** 语音留言录音文件 URL/路径 */
    private String voiceFileUrl;

    /** 漏话通知状态（0未通知 1已通知） */
    private String noticeStatus;

    /** 通知方式（1短信 2微信 3邮件） */
    private String noticeChannel;

    public void setMissedCallId(Long missedCallId)
    {
        this.missedCallId = missedCallId;
    }

    public Long getMissedCallId()
    {
        return missedCallId;
    }

    public void setCallerNumber(String callerNumber)
    {
        this.callerNumber = callerNumber;
    }

    public String getCallerNumber()
    {
        return callerNumber;
    }

    public void setCallerName(String callerName)
    {
        this.callerName = callerName;
    }

    public String getCallerName()
    {
        return callerName;
    }

    public void setCallTime(Date callTime)
    {
        this.callTime = callTime;
    }

    public Date getCallTime()
    {
        return callTime;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setCallbackTime(Date callbackTime)
    {
        this.callbackTime = callbackTime;
    }

    public Date getCallbackTime()
    {
        return callbackTime;
    }

    public void setCallbackBy(String callbackBy)
    {
        this.callbackBy = callbackBy;
    }

    public String getCallbackBy()
    {
        return callbackBy;
    }

    public void setRecordId(Long recordId)
    {
        this.recordId = recordId;
    }

    public Long getRecordId()
    {
        return recordId;
    }

    public void setVoiceContent(String voiceContent)
    {
        this.voiceContent = voiceContent;
    }

    public String getVoiceContent()
    {
        return voiceContent;
    }

    public void setVoiceDuration(Integer voiceDuration)
    {
        this.voiceDuration = voiceDuration;
    }

    public Integer getVoiceDuration()
    {
        return voiceDuration;
    }

    public void setVoiceFileUrl(String voiceFileUrl)
    {
        this.voiceFileUrl = voiceFileUrl;
    }

    public String getVoiceFileUrl()
    {
        return voiceFileUrl;
    }

    public void setNoticeStatus(String noticeStatus)
    {
        this.noticeStatus = noticeStatus;
    }

    public String getNoticeStatus()
    {
        return noticeStatus;
    }

    public void setNoticeChannel(String noticeChannel)
    {
        this.noticeChannel = noticeChannel;
    }

    public String getNoticeChannel()
    {
        return noticeChannel;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("missedCallId", getMissedCallId())
            .append("callerNumber", getCallerNumber())
            .append("callerName", getCallerName())
            .append("callTime", getCallTime())
            .append("status", getStatus())
            .append("callbackTime", getCallbackTime())
            .append("callbackBy", getCallbackBy())
            .append("recordId", getRecordId())
            .append("voiceContent", getVoiceContent())
            .append("voiceDuration", getVoiceDuration())
            .append("noticeStatus", getNoticeStatus())
            .append("noticeChannel", getNoticeChannel())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
