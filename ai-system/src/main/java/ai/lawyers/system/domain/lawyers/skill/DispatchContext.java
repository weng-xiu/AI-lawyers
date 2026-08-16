package ai.lawyers.system.domain.lawyers.skill;

/**
 * 坐席分配上下文
 *
 * @author ai-lawyers
 */
public class DispatchContext
{
    /** IVR会话ID */
    private String sessionId;

    /** 通话记录ID */
    private Long recordId;

    /** 主叫号码 */
    private String callerNumber;

    /** 来电优先级（数值越大越优先；VIP/情绪激动可提升） */
    private Integer priority;

    /** 无可用坐席时是否排队（false 则直接返回失败） */
    private boolean enqueueIfNoAgent = true;

    /** 是否允许溢出到 overflow_group_id */
    private boolean allowOverflow = true;

    public static DispatchContext of(String sessionId, Long recordId, String callerNumber)
    {
        DispatchContext ctx = new DispatchContext();
        ctx.sessionId = sessionId;
        ctx.recordId = recordId;
        ctx.callerNumber = callerNumber;
        ctx.priority = 0;
        return ctx;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public String getCallerNumber() { return callerNumber; }
    public void setCallerNumber(String callerNumber) { this.callerNumber = callerNumber; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public boolean isEnqueueIfNoAgent() { return enqueueIfNoAgent; }
    public void setEnqueueIfNoAgent(boolean enqueueIfNoAgent) { this.enqueueIfNoAgent = enqueueIfNoAgent; }
    public boolean isAllowOverflow() { return allowOverflow; }
    public void setAllowOverflow(boolean allowOverflow) { this.allowOverflow = allowOverflow; }
}
