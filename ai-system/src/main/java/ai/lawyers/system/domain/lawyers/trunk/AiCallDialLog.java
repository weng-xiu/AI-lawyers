package ai.lawyers.system.domain.lawyers.trunk;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 拨号明细日志 对象 ai_call_dial_log
 *
 * 记录每次外呼所使用的运营商线路、路由决策、故障切换链路、接通情况、
 * 各阶段时长与语音质量指标，是接通率与线路质量统计的数据来源。
 */
public class AiCallDialLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 日志ID */
    private Long logId;

    /** 呼叫唯一标识 */
    @Excel(name = "呼叫UUID")
    private String callUuid;

    /** 关联通话记录ID */
    private Long recordId;

    /** 关联外呼任务ID */
    private Long taskId;

    /** 关联外呼被叫号码ID（用于事件回调精确定位被叫） */
    private Long calleeId;

    /** 发起坐席ID */
    private Long agentId;

    /** 主叫号码 */
    @Excel(name = "主叫号码")
    private String callerNumber;

    /** 被叫号码 */
    @Excel(name = "被叫号码")
    private String calleeNumber;

    /** 被叫运营商 */
    @Excel(name = "被叫运营商", readConverterExp = "CM=中国移动,CU=中国联通,CT=中国电信,CB=中国广电,VI=虚拟运营商,00=未知")
    private String calleeCarrier;

    /** 被叫归属省 */
    private String calleeProvince;

    /** 被叫归属市 */
    private String calleeCity;

    /** 实际使用线路ID */
    private Long trunkId;

    /** 实际使用线路编码 */
    @Excel(name = "线路编码")
    private String trunkCode;

    /** 线路运营商 */
    private String trunkCarrier;

    /** 线路类型 */
    private String lineType;

    /** 路由策略 */
    private String routeStrategy;

    /** 故障切换次数 */
    @Excel(name = "切换次数")
    private Integer failoverCount;

    /** 切换途经线路链 */
    private String failoverTrunks;

    /** 排队等待毫秒 */
    private Long queueWaitMs;

    /** 拨号状态 */
    @Excel(name = "拨号状态", readConverterExp = "0=排队中,1=拨号中,2=振铃,3=已接通,4=已挂断,5=失败,6=超时,7=被拒,8=占线,9=空号")
    private String dialStatus;

    /** 挂断原因 */
    private String hangupCause;

    /** SIP 响应码 */
    private Integer sipCode;

    /** 失败原因 */
    private String failReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "拨号时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date dialTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date ringTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date answerTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date hangupTime;

    /** 振铃时长(秒) */
    private Integer ringDuration;

    /** 通话时长(秒) */
    @Excel(name = "通话时长(秒)")
    private Integer talkDuration;

    /** 呼叫总时长(秒) */
    private Integer totalDuration;

    /** 计费时长(秒) */
    private Integer billDuration;

    /** 是否接通 0=否 1=是 */
    @Excel(name = "是否接通", readConverterExp = "0=否,1=是")
    private String isConnected;

    /** MOS 评分 */
    private BigDecimal mos;

    /** 丢包率(%) */
    private BigDecimal packetLoss;

    /** 抖动(ms) */
    private Integer jitter;

    /** 往返时延(ms) */
    private Integer rtt;

    /** 录音文件 */
    private String recordFile;

    /** 查询用：开始时间 */
    private String beginTime;

    /** 查询用：结束时间 */
    private String endTimeQuery;

    public void setLogId(Long logId) { this.logId = logId; }

    public Long getLogId() { return logId; }

    public void setCallUuid(String callUuid) { this.callUuid = callUuid; }

    public String getCallUuid() { return callUuid; }

    public void setRecordId(Long recordId) { this.recordId = recordId; }

    public Long getRecordId() { return recordId; }

    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public Long getTaskId() { return taskId; }

    public void setCalleeId(Long calleeId) { this.calleeId = calleeId; }

    public Long getCalleeId() { return calleeId; }

    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public Long getAgentId() { return agentId; }

    public void setCallerNumber(String callerNumber) { this.callerNumber = callerNumber; }

    public String getCallerNumber() { return callerNumber; }

    public void setCalleeNumber(String calleeNumber) { this.calleeNumber = calleeNumber; }

    public String getCalleeNumber() { return calleeNumber; }

    public void setCalleeCarrier(String calleeCarrier) { this.calleeCarrier = calleeCarrier; }

    public String getCalleeCarrier() { return calleeCarrier; }

    public void setCalleeProvince(String calleeProvince) { this.calleeProvince = calleeProvince; }

    public String getCalleeProvince() { return calleeProvince; }

    public void setCalleeCity(String calleeCity) { this.calleeCity = calleeCity; }

    public String getCalleeCity() { return calleeCity; }

    public void setTrunkId(Long trunkId) { this.trunkId = trunkId; }

    public Long getTrunkId() { return trunkId; }

    public void setTrunkCode(String trunkCode) { this.trunkCode = trunkCode; }

    public String getTrunkCode() { return trunkCode; }

    public void setTrunkCarrier(String trunkCarrier) { this.trunkCarrier = trunkCarrier; }

    public String getTrunkCarrier() { return trunkCarrier; }

    public void setLineType(String lineType) { this.lineType = lineType; }

    public String getLineType() { return lineType; }

    public void setRouteStrategy(String routeStrategy) { this.routeStrategy = routeStrategy; }

    public String getRouteStrategy() { return routeStrategy; }

    public void setFailoverCount(Integer failoverCount) { this.failoverCount = failoverCount; }

    public Integer getFailoverCount() { return failoverCount; }

    public void setFailoverTrunks(String failoverTrunks) { this.failoverTrunks = failoverTrunks; }

    public String getFailoverTrunks() { return failoverTrunks; }

    public void setQueueWaitMs(Long queueWaitMs) { this.queueWaitMs = queueWaitMs; }

    public Long getQueueWaitMs() { return queueWaitMs; }

    public void setDialStatus(String dialStatus) { this.dialStatus = dialStatus; }

    public String getDialStatus() { return dialStatus; }

    public void setHangupCause(String hangupCause) { this.hangupCause = hangupCause; }

    public String getHangupCause() { return hangupCause; }

    public void setSipCode(Integer sipCode) { this.sipCode = sipCode; }

    public Integer getSipCode() { return sipCode; }

    public void setFailReason(String failReason) { this.failReason = failReason; }

    public String getFailReason() { return failReason; }

    public void setDialTime(Date dialTime) { this.dialTime = dialTime; }

    public Date getDialTime() { return dialTime; }

    public void setRingTime(Date ringTime) { this.ringTime = ringTime; }

    public Date getRingTime() { return ringTime; }

    public void setAnswerTime(Date answerTime) { this.answerTime = answerTime; }

    public Date getAnswerTime() { return answerTime; }

    public void setHangupTime(Date hangupTime) { this.hangupTime = hangupTime; }

    public Date getHangupTime() { return hangupTime; }

    public void setRingDuration(Integer ringDuration) { this.ringDuration = ringDuration; }

    public Integer getRingDuration() { return ringDuration; }

    public void setTalkDuration(Integer talkDuration) { this.talkDuration = talkDuration; }

    public Integer getTalkDuration() { return talkDuration; }

    public void setTotalDuration(Integer totalDuration) { this.totalDuration = totalDuration; }

    public Integer getTotalDuration() { return totalDuration; }

    public void setBillDuration(Integer billDuration) { this.billDuration = billDuration; }

    public Integer getBillDuration() { return billDuration; }

    public void setIsConnected(String isConnected) { this.isConnected = isConnected; }

    public String getIsConnected() { return isConnected; }

    public void setMos(BigDecimal mos) { this.mos = mos; }

    public BigDecimal getMos() { return mos; }

    public void setPacketLoss(BigDecimal packetLoss) { this.packetLoss = packetLoss; }

    public BigDecimal getPacketLoss() { return packetLoss; }

    public void setJitter(Integer jitter) { this.jitter = jitter; }

    public Integer getJitter() { return jitter; }

    public void setRtt(Integer rtt) { this.rtt = rtt; }

    public Integer getRtt() { return rtt; }

    public void setRecordFile(String recordFile) { this.recordFile = recordFile; }

    public String getRecordFile() { return recordFile; }

    public void setBeginTime(String beginTime) { this.beginTime = beginTime; }

    public String getBeginTime() { return beginTime; }

    public void setEndTimeQuery(String endTimeQuery) { this.endTimeQuery = endTimeQuery; }

    public String getEndTimeQuery() { return endTimeQuery; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("logId", getLogId())
            .append("callUuid", getCallUuid())
            .append("calleeNumber", getCalleeNumber())
            .append("calleeCarrier", getCalleeCarrier())
            .append("trunkCode", getTrunkCode())
            .append("routeStrategy", getRouteStrategy())
            .append("failoverCount", getFailoverCount())
            .append("dialStatus", getDialStatus())
            .append("isConnected", getIsConnected())
            .append("talkDuration", getTalkDuration())
            .append("dialTime", getDialTime())
            .toString();
    }
}
