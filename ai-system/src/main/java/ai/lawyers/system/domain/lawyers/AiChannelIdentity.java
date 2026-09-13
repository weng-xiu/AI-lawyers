package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 多渠道身份绑定对象 ai_channel_identity（F6）
 *
 * <p>渠道类型 PHONE/WECHAT_MP/WECHAT_MINI/H5/WEB；channel_uid 为 openid/unionid/账号，
 * 绑定须短信验证码或微信授权二次确认，支持解绑（手机号过户/多人共用时可撤销合并）。</p>
 *
 * @author ai-lawyers
 */
public class AiChannelIdentity extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 来电档案ID */
    private Long profileId;

    /** 渠道类型 */
    @Excel(name = "渠道类型")
    private String channelType;

    /** 渠道身份标识（openid/unionid/账号，P3-G1 加密存储） */
    @Excel(name = "渠道标识")
    private String channelUid;

    @Excel(name = "渠道昵称")
    private String channelNickname;

    /** 绑定状态 0已绑定 1已解绑 */
    @Excel(name = "绑定状态", readConverterExp = "0=已绑定,1=已解绑")
    private String bindStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "绑定时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date bindConfirmTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date unbindTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProfileId() { return profileId; }
    public void setProfileId(Long profileId) { this.profileId = profileId; }

    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }

    public String getChannelUid() { return channelUid; }
    public void setChannelUid(String channelUid) { this.channelUid = channelUid; }

    public String getChannelNickname() { return channelNickname; }
    public void setChannelNickname(String channelNickname) { this.channelNickname = channelNickname; }

    public String getBindStatus() { return bindStatus; }
    public void setBindStatus(String bindStatus) { this.bindStatus = bindStatus; }

    public Date getBindConfirmTime() { return bindConfirmTime; }
    public void setBindConfirmTime(Date bindConfirmTime) { this.bindConfirmTime = bindConfirmTime; }

    public Date getUnbindTime() { return unbindTime; }
    public void setUnbindTime(Date unbindTime) { this.unbindTime = unbindTime; }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("profileId", getProfileId())
                .append("channelType", getChannelType())
                .append("channelUid", getChannelUid())
                .append("bindStatus", getBindStatus())
                .append("bindConfirmTime", getBindConfirmTime())
                .toString();
    }
}
