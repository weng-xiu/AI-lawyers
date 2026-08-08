package ai.lawyers.system.domain.lawyers.trunk;

import java.io.Serializable;
import java.util.Date;

/**
 * 外呼结果（网关无关的统一返回）
 */
public class DialResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 是否成功下发到网关（不代表已接通） */
    private boolean success;

    /** 呼叫唯一标识 */
    private String callUuid;

    /** 拨号状态（DialStatusEnum） */
    private String dialStatus;

    /** 使用的线路ID */
    private Long trunkId;

    /** 使用的线路编码 */
    private String trunkCode;

    /** 使用的线路运营商 */
    private String trunkCarrier;

    /** 被叫号码识别出的运营商 */
    private String calleeCarrier;

    /** 路由策略 */
    private String routeStrategy;

    /** 故障切换次数 */
    private int failoverCount;

    /** 切换途经线路 */
    private String failoverTrunks;

    /** 排队等待毫秒 */
    private long queueWaitMs;

    /** SIP 响应码 */
    private Integer sipCode;

    /** 挂断原因 */
    private String hangupCause;

    /** 错误码 */
    private String errorCode;

    /** 错误信息 */
    private String message;

    /** 关联的拨号日志ID */
    private Long logId;

    /** 发起时间 */
    private Date dialTime;

    public static DialResult ok(String callUuid)
    {
        DialResult r = new DialResult();
        r.success = true;
        r.callUuid = callUuid;
        r.dialTime = new Date();
        return r;
    }

    public static DialResult fail(String errorCode, String message)
    {
        DialResult r = new DialResult();
        r.success = false;
        r.errorCode = errorCode;
        r.message = message;
        r.dialTime = new Date();
        return r;
    }

    public boolean isSuccess() { return success; }

    public void setSuccess(boolean success) { this.success = success; }

    public String getCallUuid() { return callUuid; }

    public void setCallUuid(String callUuid) { this.callUuid = callUuid; }

    public String getDialStatus() { return dialStatus; }

    public void setDialStatus(String dialStatus) { this.dialStatus = dialStatus; }

    public Long getTrunkId() { return trunkId; }

    public void setTrunkId(Long trunkId) { this.trunkId = trunkId; }

    public String getTrunkCode() { return trunkCode; }

    public void setTrunkCode(String trunkCode) { this.trunkCode = trunkCode; }

    public String getTrunkCarrier() { return trunkCarrier; }

    public void setTrunkCarrier(String trunkCarrier) { this.trunkCarrier = trunkCarrier; }

    public String getCalleeCarrier() { return calleeCarrier; }

    public void setCalleeCarrier(String calleeCarrier) { this.calleeCarrier = calleeCarrier; }

    public String getRouteStrategy() { return routeStrategy; }

    public void setRouteStrategy(String routeStrategy) { this.routeStrategy = routeStrategy; }

    public int getFailoverCount() { return failoverCount; }

    public void setFailoverCount(int failoverCount) { this.failoverCount = failoverCount; }

    public String getFailoverTrunks() { return failoverTrunks; }

    public void setFailoverTrunks(String failoverTrunks) { this.failoverTrunks = failoverTrunks; }

    public long getQueueWaitMs() { return queueWaitMs; }

    public void setQueueWaitMs(long queueWaitMs) { this.queueWaitMs = queueWaitMs; }

    public Integer getSipCode() { return sipCode; }

    public void setSipCode(Integer sipCode) { this.sipCode = sipCode; }

    public String getHangupCause() { return hangupCause; }

    public void setHangupCause(String hangupCause) { this.hangupCause = hangupCause; }

    public String getErrorCode() { return errorCode; }

    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getMessage() { return message; }

    public void setMessage(String message) { this.message = message; }

    public Long getLogId() { return logId; }

    public void setLogId(Long logId) { this.logId = logId; }

    public Date getDialTime() { return dialTime; }

    public void setDialTime(Date dialTime) { this.dialTime = dialTime; }
}
