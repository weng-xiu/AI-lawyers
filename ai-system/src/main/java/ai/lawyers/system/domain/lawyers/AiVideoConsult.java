package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 视频咨询对象 ai_video_consult
 *
 * @author ai-lawyers
 */
public class AiVideoConsult extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 视频咨询ID */
    private Long consultId;

    /** 咨询编号 */
    @Excel(name = "咨询编号")
    private String consultNo;

    /** 客户姓名 */
    @Excel(name = "客户姓名")
    private String customerName;

    /** 客户电话 */
    @Excel(name = "客户电话")
    private String customerPhone;

    /** 坐席ID */
    private Long agentId;

    /** 坐席名称 */
    @Excel(name = "坐席")
    private String agentName;

    /** 房间号 */
    @Excel(name = "房间号")
    private String roomNo;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "开始时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "结束时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    /** 通话时长(秒) */
    @Excel(name = "通话时长")
    private Integer duration;

    /** 状态（0等待中 1进行中 2已结束 3已取消） */
    @Excel(name = "状态", readConverterExp = "0=等待中,1=进行中,2=已结束,3=已取消")
    private String status;

    /** 满意度(1-5) */
    @Excel(name = "满意度")
    private Integer satisfaction;

    /** 录像地址 */
    private String recordingUrl;

    /** 是否已身份核验（0否 1是） */
    @Excel(name = "身份核验", readConverterExp = "0=否,1=是")
    private String identityVerify;

    public Long getConsultId() { return consultId; }
    public void setConsultId(Long consultId) { this.consultId = consultId; }

    public String getConsultNo() { return consultNo; }
    public void setConsultNo(String consultNo) { this.consultNo = consultNo; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }

    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }

    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }

    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getSatisfaction() { return satisfaction; }
    public void setSatisfaction(Integer satisfaction) { this.satisfaction = satisfaction; }

    public String getRecordingUrl() { return recordingUrl; }
    public void setRecordingUrl(String recordingUrl) { this.recordingUrl = recordingUrl; }

    public String getIdentityVerify() { return identityVerify; }
    public void setIdentityVerify(String identityVerify) { this.identityVerify = identityVerify; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("consultId", getConsultId())
            .append("consultNo", getConsultNo())
            .append("customerName", getCustomerName())
            .append("customerPhone", getCustomerPhone())
            .append("agentId", getAgentId())
            .append("agentName", getAgentName())
            .append("roomNo", getRoomNo())
            .append("startTime", getStartTime())
            .append("endTime", getEndTime())
            .append("duration", getDuration())
            .append("status", getStatus())
            .append("satisfaction", getSatisfaction())
            .append("recordingUrl", getRecordingUrl())
            .append("identityVerify", getIdentityVerify())
            .toString();
    }
}
