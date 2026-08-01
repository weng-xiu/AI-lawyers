package ai.lawyers.system.domain.lawyers.ivr;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import ai.lawyers.common.core.domain.BaseEntity;

public class AiIvrExecutionLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long execId;
    private Long recordId;
    private String sessionId;
    private Long flowId;
    private String flowName;
    private Long currentNodeId;
    private String currentNodeType;
    private String currentNodeName;
    private String executeResult;
    private String variables;
    private String status;
    private String errorMsg;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    public void setExecId(Long execId) { this.execId = execId; }
    public Long getExecId() { return execId; }

    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Long getRecordId() { return recordId; }

    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getSessionId() { return sessionId; }

    public void setFlowId(Long flowId) { this.flowId = flowId; }
    public Long getFlowId() { return flowId; }

    public void setFlowName(String flowName) { this.flowName = flowName; }
    public String getFlowName() { return flowName; }

    public void setCurrentNodeId(Long currentNodeId) { this.currentNodeId = currentNodeId; }
    public Long getCurrentNodeId() { return currentNodeId; }

    public void setCurrentNodeType(String currentNodeType) { this.currentNodeType = currentNodeType; }
    public String getCurrentNodeType() { return currentNodeType; }

    public void setCurrentNodeName(String currentNodeName) { this.currentNodeName = currentNodeName; }
    public String getCurrentNodeName() { return currentNodeName; }

    public void setExecuteResult(String executeResult) { this.executeResult = executeResult; }
    public String getExecuteResult() { return executeResult; }

    public void setVariables(String variables) { this.variables = variables; }
    public String getVariables() { return variables; }

    public void setStatus(String status) { this.status = status; }
    public String getStatus() { return status; }

    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public String getErrorMsg() { return errorMsg; }

    public void setStartTime(Date startTime) { this.startTime = startTime; }
    public Date getStartTime() { return startTime; }

    public void setEndTime(Date endTime) { this.endTime = endTime; }
    public Date getEndTime() { return endTime; }
}
