package ai.lawyers.system.domain.lawyers.ivr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IVR 流程执行请求
 *
 * 支持两种执行场景：
 *  1. 在线调试/测试：指定 flowId 与 inputs 模拟一路来电；
 *  2. 运行时执行：由外呼执行引擎或呼叫接入侧构造，携带 recordId / sessionId / 号码信息。
 */
public class IvrExecuteRequest
{
    /** 流程ID（为空时使用默认流程） */
    private Long flowId;

    /** 关联通话记录ID（执行结果会回写 ai_call_record） */
    private Long recordId;

    /** 会话ID（为空时引擎自动生成） */
    private String sessionId;

    /** 主叫号码（来电/外呼主叫） */
    private String callerNumber;

    /** 被叫号码（外呼场景） */
    private String calleeNumber;

    /** 用户输入序列：menu/dtmf 节点取首个字符，intention 节点取整段文本，按顺序消费 */
    private List<String> inputs = new ArrayList<>();

    /** 初始流程变量 */
    private Map<String, Object> variables = new HashMap<>();

    public Long getFlowId() { return flowId; }
    public void setFlowId(Long flowId) { this.flowId = flowId; }

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getCallerNumber() { return callerNumber; }
    public void setCallerNumber(String callerNumber) { this.callerNumber = callerNumber; }

    public String getCalleeNumber() { return calleeNumber; }
    public void setCalleeNumber(String calleeNumber) { this.calleeNumber = calleeNumber; }

    public List<String> getInputs() { return inputs == null ? new ArrayList<>() : inputs; }
    public void setInputs(List<String> inputs) { this.inputs = inputs == null ? new ArrayList<>() : inputs; }

    public Map<String, Object> getVariables() { return variables == null ? new HashMap<>() : variables; }
    public void setVariables(Map<String, Object> variables) { this.variables = variables == null ? new HashMap<>() : variables; }
}
