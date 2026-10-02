package ai.lawyers.system.domain.lawyers.stat;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 预测性满意度行（P1-11，非持久化，接口出参）
 *
 * <p>不返回号码（PII 最小披露），以记录ID关联话单详情。</p>
 *
 * @author ai-lawyers
 */
public class AiSatisfactionForecast
{
    /** 通话记录ID */
    private Long recordId;

    /** 来电时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date callTime;

    /** 通话时长（秒） */
    private Integer duration;

    /** 状态 1接通 2转接 3未接 */
    private String status;

    /** 转写情绪 URGENT/NEGATIVE/NEUTRAL */
    private String emotion;

    /** 质检总分（申诉后为调整分，null=无质检） */
    private Double qcTotal;

    /** 质检情绪态度分（null=无质检） */
    private Double qcEmotion;

    /** 预测满意度 1非常满意~4不满意 */
    private Integer predicted;

    /** 评分构成说明 */
    private String scoreDetail;

    /** 实际满意度（回访/台账回收，null=未回收） */
    private Integer actual;

    public Long getRecordId()
    {
        return recordId;
    }

    public void setRecordId(Long recordId)
    {
        this.recordId = recordId;
    }

    public Date getCallTime()
    {
        return callTime;
    }

    public void setCallTime(Date callTime)
    {
        this.callTime = callTime;
    }

    public Integer getDuration()
    {
        return duration;
    }

    public void setDuration(Integer duration)
    {
        this.duration = duration;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getEmotion()
    {
        return emotion;
    }

    public void setEmotion(String emotion)
    {
        this.emotion = emotion;
    }

    public Double getQcTotal()
    {
        return qcTotal;
    }

    public void setQcTotal(Double qcTotal)
    {
        this.qcTotal = qcTotal;
    }

    public Double getQcEmotion()
    {
        return qcEmotion;
    }

    public void setQcEmotion(Double qcEmotion)
    {
        this.qcEmotion = qcEmotion;
    }

    public Integer getPredicted()
    {
        return predicted;
    }

    public void setPredicted(Integer predicted)
    {
        this.predicted = predicted;
    }

    public String getScoreDetail()
    {
        return scoreDetail;
    }

    public void setScoreDetail(String scoreDetail)
    {
        this.scoreDetail = scoreDetail;
    }

    public Integer getActual()
    {
        return actual;
    }

    public void setActual(Integer actual)
    {
        this.actual = actual;
    }
}
