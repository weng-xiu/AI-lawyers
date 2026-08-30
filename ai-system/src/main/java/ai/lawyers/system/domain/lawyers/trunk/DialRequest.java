package ai.lawyers.system.domain.lawyers.trunk;

import java.io.Serializable;

/**
 * 外呼请求（网关无关的统一入参）
 *
 * 上层业务（坐席点击外呼、批量外呼任务、回访任务）统一构造此对象提交给
 * 呼叫调度器 ICallDispatchService，由调度器完成号码归属识别、线路选择、
 * 并发控制、排队、故障切换后，交给具体网关适配器执行。
 */
public class DialRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 被叫号码（必填） */
    private String calleeNumber;

    /** 主叫显号（可空，为空则用线路配置的 callerDisplay） */
    private String callerNumber;

    /** 发起坐席ID（人工外呼时必填，坐席分机将被桥接） */
    private Long agentId;

    /** 坐席分机号（SIP 分机，用于 originate 后 bridge 到坐席） */
    private String agentExtension;

    /** 关联通话记录ID */
    private Long recordId;

    /** 关联外呼任务ID（批量外呼场景） */
    private Long taskId;

    /** 关联外呼被叫号码ID（批量外呼场景，用于事件回调精确定位被叫） */
    private Long calleeId;

    /** 指定线路编码（人工强制指定，为空则自动选路） */
    private String assignTrunkCode;

    /** 指定运营商（为空则按号码前缀自动识别） */
    private String assignCarrier;

    /** 呼叫优先级，数值越小越先出队（默认 100） */
    private Integer priority = 100;

    /** 最大振铃超时（秒），默认 45 */
    private Integer ringTimeout = 45;

    /** 是否允许故障切换（默认 true） */
    private boolean allowFailover = true;

    /** 最大切换次数（默认 2） */
    private Integer maxFailover = 2;

    /** 是否录音 */
    private boolean enableRecord = true;

    /** 接通后需要执行的动作 BRIDGE_AGENT=转坐席 IVR=进IVR流程 PLAYBACK=放音 */
    private String answerAction = "BRIDGE_AGENT";

    /** IVR 流程ID（answerAction=IVR 时使用） */
    private Long ivrFlowId;

    /** 业务侧发起人 */
    private String createBy;

    /** 备注 */
    private String remark;

    public DialRequest() {}

    public DialRequest(String calleeNumber, Long agentId)
    {
        this.calleeNumber = calleeNumber;
        this.agentId = agentId;
    }

    public String getCalleeNumber() { return calleeNumber; }

    public void setCalleeNumber(String calleeNumber) { this.calleeNumber = calleeNumber; }

    public String getCallerNumber() { return callerNumber; }

    public void setCallerNumber(String callerNumber) { this.callerNumber = callerNumber; }

    public Long getAgentId() { return agentId; }

    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getAgentExtension() { return agentExtension; }

    public void setAgentExtension(String agentExtension) { this.agentExtension = agentExtension; }

    public Long getRecordId() { return recordId; }

    public void setRecordId(Long recordId) { this.recordId = recordId; }

    public Long getTaskId() { return taskId; }

    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public Long getCalleeId() { return calleeId; }

    public void setCalleeId(Long calleeId) { this.calleeId = calleeId; }

    public String getAssignTrunkCode() { return assignTrunkCode; }

    public void setAssignTrunkCode(String assignTrunkCode) { this.assignTrunkCode = assignTrunkCode; }

    public String getAssignCarrier() { return assignCarrier; }

    public void setAssignCarrier(String assignCarrier) { this.assignCarrier = assignCarrier; }

    public Integer getPriority() { return priority; }

    public void setPriority(Integer priority) { this.priority = priority; }

    public Integer getRingTimeout() { return ringTimeout; }

    public void setRingTimeout(Integer ringTimeout) { this.ringTimeout = ringTimeout; }

    public boolean isAllowFailover() { return allowFailover; }

    public void setAllowFailover(boolean allowFailover) { this.allowFailover = allowFailover; }

    public Integer getMaxFailover() { return maxFailover; }

    public void setMaxFailover(Integer maxFailover) { this.maxFailover = maxFailover; }

    public boolean isEnableRecord() { return enableRecord; }

    public void setEnableRecord(boolean enableRecord) { this.enableRecord = enableRecord; }

    public String getAnswerAction() { return answerAction; }

    public void setAnswerAction(String answerAction) { this.answerAction = answerAction; }

    public Long getIvrFlowId() { return ivrFlowId; }

    public void setIvrFlowId(Long ivrFlowId) { this.ivrFlowId = ivrFlowId; }

    public String getCreateBy() { return createBy; }

    public void setCreateBy(String createBy) { this.createBy = createBy; }

    public String getRemark() { return remark; }

    public void setRemark(String remark) { this.remark = remark; }
}
