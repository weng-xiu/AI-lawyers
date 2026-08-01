package ai.lawyers.system.domain.lawyers.ivr;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import ai.lawyers.common.core.domain.BaseEntity;

public class AiIvrIntentionLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long logId;
    private Long recordId;
    private String sessionId;
    private Long flowId;
    private Long nodeId;
    private String inputText;
    private String matchedIntention;
    private String matchedIntentionName;
    private BigDecimal confidence;
    private String matchMethod;
    private String allResults;

    public void setLogId(Long logId) { this.logId = logId; }
    public Long getLogId() { return logId; }

    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Long getRecordId() { return recordId; }

    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getSessionId() { return sessionId; }

    public void setFlowId(Long flowId) { this.flowId = flowId; }
    public Long getFlowId() { return flowId; }

    public void setNodeId(Long nodeId) { this.nodeId = nodeId; }
    public Long getNodeId() { return nodeId; }

    public void setInputText(String inputText) { this.inputText = inputText; }
    public String getInputText() { return inputText; }

    public void setMatchedIntention(String matchedIntention) { this.matchedIntention = matchedIntention; }
    public String getMatchedIntention() { return matchedIntention; }

    public void setMatchedIntentionName(String matchedIntentionName) { this.matchedIntentionName = matchedIntentionName; }
    public String getMatchedIntentionName() { return matchedIntentionName; }

    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public BigDecimal getConfidence() { return confidence; }

    public void setMatchMethod(String matchMethod) { this.matchMethod = matchMethod; }
    public String getMatchMethod() { return matchMethod; }

    public void setAllResults(String allResults) { this.allResults = allResults; }
    public String getAllResults() { return allResults; }
}
