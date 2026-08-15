package ai.lawyers.system.domain.lawyers.ivr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IVR 流程执行结果
 */
public class IvrExecuteResult
{
    private boolean success;
    private String code;
    private String message;

    private Long execId;
    private String sessionId;
    private Long flowId;
    private String flowName;
    private String status;

    private Long recordId;

    private Long currentNodeId;
    private String currentNodeType;
    private String currentNodeName;

    private String matchedIntention;
    private String matchedIntentionName;
    private String matchMethod;
    private Long categoryId;
    private String categoryName;
    private String transferTarget;

    private Map<String, Object> variables = new LinkedHashMap<>();
    private List<IvrNodeStep> steps = new ArrayList<>();

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Long getExecId() { return execId; }
    public void setExecId(Long execId) { this.execId = execId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Long getFlowId() { return flowId; }
    public void setFlowId(Long flowId) { this.flowId = flowId; }

    public String getFlowName() { return flowName; }
    public void setFlowName(String flowName) { this.flowName = flowName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }

    public Long getCurrentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(Long currentNodeId) { this.currentNodeId = currentNodeId; }

    public String getCurrentNodeType() { return currentNodeType; }
    public void setCurrentNodeType(String currentNodeType) { this.currentNodeType = currentNodeType; }

    public String getCurrentNodeName() { return currentNodeName; }
    public void setCurrentNodeName(String currentNodeName) { this.currentNodeName = currentNodeName; }

    public String getMatchedIntention() { return matchedIntention; }
    public void setMatchedIntention(String matchedIntention) { this.matchedIntention = matchedIntention; }

    public String getMatchedIntentionName() { return matchedIntentionName; }
    public void setMatchedIntentionName(String matchedIntentionName) { this.matchedIntentionName = matchedIntentionName; }

    public String getMatchMethod() { return matchMethod; }
    public void setMatchMethod(String matchMethod) { this.matchMethod = matchMethod; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getTransferTarget() { return transferTarget; }
    public void setTransferTarget(String transferTarget) { this.transferTarget = transferTarget; }

    public Map<String, Object> getVariables() { return variables; }
    public void setVariables(Map<String, Object> variables) { this.variables = variables; }

    public List<IvrNodeStep> getSteps() { return steps; }
    public void setSteps(List<IvrNodeStep> steps) { this.steps = steps; }
}
