package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 跨渠道统一会话索引对象 ai_unified_session（F6）
 *
 * <p>各业务会话（CALL/CHAT/VIDEO/IVR/MESSAGE/TICKET）创建时写索引，
 * 用于聚合同一来电人/同一档案的跨渠道咨询时间线。</p>
 *
 * @author ai-lawyers
 */
public class AiUnifiedSession extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long sessionId;

    private Long profileId;

    /** 手机号（时间线聚合兜底键） */
    private String callerNumber;

    /** 渠道 PHONE/WECHAT_MP/WECHAT_MINI/H5/WEB */
    private String channelType;

    /** 业务类型 CALL/CHAT/VIDEO/IVR/MESSAGE/TICKET */
    private String bizType;

    /** 业务主键（各业务表ID/会话号） */
    private String bizId;

    /** 业务摘要 */
    private String bizTitle;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public Long getProfileId() { return profileId; }
    public void setProfileId(Long profileId) { this.profileId = profileId; }

    public String getCallerNumber() { return callerNumber; }
    public void setCallerNumber(String callerNumber) { this.callerNumber = callerNumber; }

    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }

    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }

    public String getBizId() { return bizId; }
    public void setBizId(String bizId) { this.bizId = bizId; }

    public String getBizTitle() { return bizTitle; }
    public void setBizTitle(String bizTitle) { this.bizTitle = bizTitle; }

    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }

    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }
}
