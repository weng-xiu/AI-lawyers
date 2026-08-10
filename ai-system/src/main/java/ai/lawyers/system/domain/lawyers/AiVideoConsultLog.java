package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 视频咨询事件日志对象 ai_video_consult_log
 *
 * @author ai-lawyers
 */
public class AiVideoConsultLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long logId;
    private Long consultId;
    private String eventType;
    private String eventContent;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date eventTime;

    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }

    public Long getConsultId() { return consultId; }
    public void setConsultId(Long consultId) { this.consultId = consultId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getEventContent() { return eventContent; }
    public void setEventContent(String eventContent) { this.eventContent = eventContent; }

    public Date getEventTime() { return eventTime; }
    public void setEventTime(Date eventTime) { this.eventTime = eventTime; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("logId", getLogId())
            .append("consultId", getConsultId())
            .append("eventType", getEventType())
            .append("eventContent", getEventContent())
            .append("eventTime", getEventTime())
            .toString();
    }
}
