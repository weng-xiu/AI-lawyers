package ai.lawyers.system.domain.lawyers.trunk;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 线路告警 对象 ai_trunk_alarm
 */
public class AiTrunkAlarm extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long alarmId;

    private Long trunkId;

    @Excel(name = "线路编码")
    private String trunkCode;

    @Excel(name = "运营商", readConverterExp = "CM=中国移动,CU=中国联通,CT=中国电信,CB=中国广电,VI=虚拟运营商,00=未知")
    private String carrier;

    /** 告警类型 CONNECT_RATE_LOW/TRUNK_DOWN/CIRCUIT_OPEN/CONCURRENT_FULL/MOS_LOW/QUEUE_OVERFLOW */
    @Excel(name = "告警类型")
    private String alarmType;

    /** 告警级别 1=提示 2=一般 3=严重 4=紧急 */
    @Excel(name = "级别", readConverterExp = "1=提示,2=一般,3=严重,4=紧急")
    private String alarmLevel;

    @Excel(name = "告警标题")
    private String alarmTitle;

    private String alarmContent;

    private String metricValue;

    private String thresholdValue;

    /** 状态 0=未处理 1=已确认 2=已恢复 3=已忽略 */
    @Excel(name = "状态", readConverterExp = "0=未处理,1=已确认,2=已恢复,3=已忽略")
    private String alarmStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "告警时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date alarmTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date recoverTime;

    private String handleBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date handleTime;

    private String handleRemark;

    public void setAlarmId(Long alarmId) { this.alarmId = alarmId; }

    public Long getAlarmId() { return alarmId; }

    public void setTrunkId(Long trunkId) { this.trunkId = trunkId; }

    public Long getTrunkId() { return trunkId; }

    public void setTrunkCode(String trunkCode) { this.trunkCode = trunkCode; }

    public String getTrunkCode() { return trunkCode; }

    public void setCarrier(String carrier) { this.carrier = carrier; }

    public String getCarrier() { return carrier; }

    public void setAlarmType(String alarmType) { this.alarmType = alarmType; }

    public String getAlarmType() { return alarmType; }

    public void setAlarmLevel(String alarmLevel) { this.alarmLevel = alarmLevel; }

    public String getAlarmLevel() { return alarmLevel; }

    public void setAlarmTitle(String alarmTitle) { this.alarmTitle = alarmTitle; }

    public String getAlarmTitle() { return alarmTitle; }

    public void setAlarmContent(String alarmContent) { this.alarmContent = alarmContent; }

    public String getAlarmContent() { return alarmContent; }

    public void setMetricValue(String metricValue) { this.metricValue = metricValue; }

    public String getMetricValue() { return metricValue; }

    public void setThresholdValue(String thresholdValue) { this.thresholdValue = thresholdValue; }

    public String getThresholdValue() { return thresholdValue; }

    public void setAlarmStatus(String alarmStatus) { this.alarmStatus = alarmStatus; }

    public String getAlarmStatus() { return alarmStatus; }

    public void setAlarmTime(Date alarmTime) { this.alarmTime = alarmTime; }

    public Date getAlarmTime() { return alarmTime; }

    public void setRecoverTime(Date recoverTime) { this.recoverTime = recoverTime; }

    public Date getRecoverTime() { return recoverTime; }

    public void setHandleBy(String handleBy) { this.handleBy = handleBy; }

    public String getHandleBy() { return handleBy; }

    public void setHandleTime(Date handleTime) { this.handleTime = handleTime; }

    public Date getHandleTime() { return handleTime; }

    public void setHandleRemark(String handleRemark) { this.handleRemark = handleRemark; }

    public String getHandleRemark() { return handleRemark; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("alarmId", getAlarmId())
            .append("trunkCode", getTrunkCode())
            .append("alarmType", getAlarmType())
            .append("alarmLevel", getAlarmLevel())
            .append("alarmTitle", getAlarmTitle())
            .append("alarmStatus", getAlarmStatus())
            .append("alarmTime", getAlarmTime())
            .toString();
    }
}
