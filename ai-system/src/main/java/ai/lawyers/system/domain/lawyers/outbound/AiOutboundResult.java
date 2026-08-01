package ai.lawyers.system.domain.lawyers.outbound;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import ai.lawyers.common.core.domain.BaseEntity;

public class AiOutboundResult extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long resultId;
    private Long taskId;
    private Long calleeId;
    private String calleeNumber;
    private String calleeName;
    private Long recordId;
    private String callResult;
    private Integer callDuration;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    private Long agentId;
    private String agentName;
    private String intentionCode;
    private String intentionName;
    private String keywords;
    private String satisfaction;
    private String needCallback;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date callbackTime;

    private String transcript;
    private String summary;
    private String recordingUrl;
    private String flowData;

    public void setResultId(Long resultId) { this.resultId = resultId; }
    public Long getResultId() { return resultId; }

    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getTaskId() { return taskId; }

    public void setCalleeId(Long calleeId) { this.calleeId = calleeId; }
    public Long getCalleeId() { return calleeId; }

    public void setCalleeNumber(String calleeNumber) { this.calleeNumber = calleeNumber; }
    public String getCalleeNumber() { return calleeNumber; }

    public void setCalleeName(String calleeName) { this.calleeName = calleeName; }
    public String getCalleeName() { return calleeName; }

    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Long getRecordId() { return recordId; }

    public void setCallResult(String callResult) { this.callResult = callResult; }
    public String getCallResult() { return callResult; }

    public void setCallDuration(Integer callDuration) { this.callDuration = callDuration; }
    public Integer getCallDuration() { return callDuration; }

    public void setStartTime(Date startTime) { this.startTime = startTime; }
    public Date getStartTime() { return startTime; }

    public void setEndTime(Date endTime) { this.endTime = endTime; }
    public Date getEndTime() { return endTime; }

    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public Long getAgentId() { return agentId; }

    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getAgentName() { return agentName; }

    public void setIntentionCode(String intentionCode) { this.intentionCode = intentionCode; }
    public String getIntentionCode() { return intentionCode; }

    public void setIntentionName(String intentionName) { this.intentionName = intentionName; }
    public String getIntentionName() { return intentionName; }

    public void setKeywords(String keywords) { this.keywords = keywords; }
    public String getKeywords() { return keywords; }

    public void setSatisfaction(String satisfaction) { this.satisfaction = satisfaction; }
    public String getSatisfaction() { return satisfaction; }

    public void setNeedCallback(String needCallback) { this.needCallback = needCallback; }
    public String getNeedCallback() { return needCallback; }

    public void setCallbackTime(Date callbackTime) { this.callbackTime = callbackTime; }
    public Date getCallbackTime() { return callbackTime; }

    public void setTranscript(String transcript) { this.transcript = transcript; }
    public String getTranscript() { return transcript; }

    public void setSummary(String summary) { this.summary = summary; }
    public String getSummary() { return summary; }

    public void setRecordingUrl(String recordingUrl) { this.recordingUrl = recordingUrl; }
    public String getRecordingUrl() { return recordingUrl; }

    public void setFlowData(String flowData) { this.flowData = flowData; }
    public String getFlowData() { return flowData; }
}
