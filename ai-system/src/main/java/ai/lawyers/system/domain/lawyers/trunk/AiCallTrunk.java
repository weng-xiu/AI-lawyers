package ai.lawyers.system.domain.lawyers.trunk;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 运营商中继线路 对象 ai_call_trunk
 *
 * 承载三大运营商（移动/联通/电信）SIP 或 PSTN 线路的接入配置、容量限制、
 * 健康状态与质量指标。选路引擎依据 carrier + priority + weight + healthStatus
 * 决定外呼走哪条线路。
 */
public class AiCallTrunk extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 线路ID */
    private Long trunkId;

    /** 线路编码(唯一) */
    @Excel(name = "线路编码")
    private String trunkCode;

    /** 线路名称 */
    @Excel(name = "线路名称")
    private String trunkName;

    /** 运营商 CM=移动 CU=联通 CT=电信 CB=广电 VI=虚商 00=未知 */
    @Excel(name = "运营商", readConverterExp = "CM=中国移动,CU=中国联通,CT=中国电信,CB=中国广电,VI=虚拟运营商,00=未知")
    private String carrier;

    /** 线路类型 1=SIP 2=PSTN 3=SIP中继网关 4=WebRTC */
    @Excel(name = "线路类型", readConverterExp = "1=SIP,2=PSTN,3=SIP中继网关,4=WebRTC")
    private String lineType;

    /** 网关厂商/中间件 */
    @Excel(name = "网关厂商")
    private String vendor;

    /** 信令协议 SIP/SS7/H323/HTTP */
    private String protocol;

    /** 网关/SBC 地址 */
    @Excel(name = "网关地址")
    private String gatewayHost;

    /** 网关端口 */
    private Integer gatewayPort;

    /** 传输层 UDP/TCP/TLS */
    private String transport;

    /** CTI中间件 HTTP API 基地址 */
    private String apiBaseUrl;

    /** 注册/鉴权用户名 */
    private String authUser;

    /** 注册/鉴权密码 */
    private String authPassword;

    /** SIP realm */
    private String realm;

    /** 外呼主叫前缀(出局字冠) */
    private String callerPrefix;

    /** 主叫显号 */
    private String callerDisplay;

    /** 被叫号码去除前N位 */
    private Integer stripDigits;

    /** 被叫号码添加前缀 */
    private String addPrefix;

    /** 最大并发呼叫数 */
    @Excel(name = "最大并发")
    private Integer maxConcurrent;

    /** 当前并发数 */
    @Excel(name = "当前并发")
    private Integer currentConcurrent;

    /** 每秒最大呼叫数(CPS) */
    private Integer cpsLimit;

    /** 优先级(越小越优先) */
    private Integer priority;

    /** 负载权重 */
    private Integer weight;

    /** 是否备份线路 0=主用 1=备用 */
    private String isBackup;

    /** 健康状态 0=未知 1=正常 2=亚健康 3=故障 4=熔断 */
    @Excel(name = "健康状态", readConverterExp = "0=未知,1=正常,2=亚健康,3=故障,4=熔断")
    private String healthStatus;

    /** 启用状态 0=停用 1=启用 */
    @Excel(name = "启用状态", readConverterExp = "0=停用,1=启用")
    private String enableFlag;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastCheckTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastSuccessTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastFailTime;

    /** 连续失败次数 */
    private Integer failCount;

    /** 熔断开启时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date circuitOpenTime;

    /** 接通率(%) */
    @Excel(name = "接通率(%)")
    private BigDecimal connectRate;

    /** ASR 应答率(%) */
    private BigDecimal asr;

    /** ACD 平均通话时长(秒) */
    private Integer acd;

    /** MOS 语音质量评分 */
    private BigDecimal mos;

    /** 累计呼叫次数 */
    private Long totalCalls;

    /** 累计接通次数 */
    private Long successCalls;

    /** 累计通话时长(秒) */
    private Long totalDuration;

    /** 接通率告警阈值(%) */
    private BigDecimal alarmConnectRate;

    /** 连续失败告警/熔断阈值 */
    private Integer alarmFailTimes;

    /** 删除标志 */
    private String delFlag;

    public void setTrunkId(Long trunkId) { this.trunkId = trunkId; }

    public Long getTrunkId() { return trunkId; }

    public void setTrunkCode(String trunkCode) { this.trunkCode = trunkCode; }

    public String getTrunkCode() { return trunkCode; }

    public void setTrunkName(String trunkName) { this.trunkName = trunkName; }

    public String getTrunkName() { return trunkName; }

    public void setCarrier(String carrier) { this.carrier = carrier; }

    public String getCarrier() { return carrier; }

    public void setLineType(String lineType) { this.lineType = lineType; }

    public String getLineType() { return lineType; }

    public void setVendor(String vendor) { this.vendor = vendor; }

    public String getVendor() { return vendor; }

    public void setProtocol(String protocol) { this.protocol = protocol; }

    public String getProtocol() { return protocol; }

    public void setGatewayHost(String gatewayHost) { this.gatewayHost = gatewayHost; }

    public String getGatewayHost() { return gatewayHost; }

    public void setGatewayPort(Integer gatewayPort) { this.gatewayPort = gatewayPort; }

    public Integer getGatewayPort() { return gatewayPort; }

    public void setTransport(String transport) { this.transport = transport; }

    public String getTransport() { return transport; }

    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }

    public String getApiBaseUrl() { return apiBaseUrl; }

    public void setAuthUser(String authUser) { this.authUser = authUser; }

    public String getAuthUser() { return authUser; }

    public void setAuthPassword(String authPassword) { this.authPassword = authPassword; }

    public String getAuthPassword() { return authPassword; }

    public void setRealm(String realm) { this.realm = realm; }

    public String getRealm() { return realm; }

    public void setCallerPrefix(String callerPrefix) { this.callerPrefix = callerPrefix; }

    public String getCallerPrefix() { return callerPrefix; }

    public void setCallerDisplay(String callerDisplay) { this.callerDisplay = callerDisplay; }

    public String getCallerDisplay() { return callerDisplay; }

    public void setStripDigits(Integer stripDigits) { this.stripDigits = stripDigits; }

    public Integer getStripDigits() { return stripDigits; }

    public void setAddPrefix(String addPrefix) { this.addPrefix = addPrefix; }

    public String getAddPrefix() { return addPrefix; }

    public void setMaxConcurrent(Integer maxConcurrent) { this.maxConcurrent = maxConcurrent; }

    public Integer getMaxConcurrent() { return maxConcurrent; }

    public void setCurrentConcurrent(Integer currentConcurrent) { this.currentConcurrent = currentConcurrent; }

    public Integer getCurrentConcurrent() { return currentConcurrent; }

    public void setCpsLimit(Integer cpsLimit) { this.cpsLimit = cpsLimit; }

    public Integer getCpsLimit() { return cpsLimit; }

    public void setPriority(Integer priority) { this.priority = priority; }

    public Integer getPriority() { return priority; }

    public void setWeight(Integer weight) { this.weight = weight; }

    public Integer getWeight() { return weight; }

    public void setIsBackup(String isBackup) { this.isBackup = isBackup; }

    public String getIsBackup() { return isBackup; }

    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }

    public String getHealthStatus() { return healthStatus; }

    public void setEnableFlag(String enableFlag) { this.enableFlag = enableFlag; }

    public String getEnableFlag() { return enableFlag; }

    public void setLastCheckTime(Date lastCheckTime) { this.lastCheckTime = lastCheckTime; }

    public Date getLastCheckTime() { return lastCheckTime; }

    public void setLastSuccessTime(Date lastSuccessTime) { this.lastSuccessTime = lastSuccessTime; }

    public Date getLastSuccessTime() { return lastSuccessTime; }

    public void setLastFailTime(Date lastFailTime) { this.lastFailTime = lastFailTime; }

    public Date getLastFailTime() { return lastFailTime; }

    public void setFailCount(Integer failCount) { this.failCount = failCount; }

    public Integer getFailCount() { return failCount; }

    public void setCircuitOpenTime(Date circuitOpenTime) { this.circuitOpenTime = circuitOpenTime; }

    public Date getCircuitOpenTime() { return circuitOpenTime; }

    public void setConnectRate(BigDecimal connectRate) { this.connectRate = connectRate; }

    public BigDecimal getConnectRate() { return connectRate; }

    public void setAsr(BigDecimal asr) { this.asr = asr; }

    public BigDecimal getAsr() { return asr; }

    public void setAcd(Integer acd) { this.acd = acd; }

    public Integer getAcd() { return acd; }

    public void setMos(BigDecimal mos) { this.mos = mos; }

    public BigDecimal getMos() { return mos; }

    public void setTotalCalls(Long totalCalls) { this.totalCalls = totalCalls; }

    public Long getTotalCalls() { return totalCalls; }

    public void setSuccessCalls(Long successCalls) { this.successCalls = successCalls; }

    public Long getSuccessCalls() { return successCalls; }

    public void setTotalDuration(Long totalDuration) { this.totalDuration = totalDuration; }

    public Long getTotalDuration() { return totalDuration; }

    public void setAlarmConnectRate(BigDecimal alarmConnectRate) { this.alarmConnectRate = alarmConnectRate; }

    public BigDecimal getAlarmConnectRate() { return alarmConnectRate; }

    public void setAlarmFailTimes(Integer alarmFailTimes) { this.alarmFailTimes = alarmFailTimes; }

    public Integer getAlarmFailTimes() { return alarmFailTimes; }

    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }

    public String getDelFlag() { return delFlag; }

    /** 剩余可用并发数 */
    public int getAvailableConcurrent()
    {
        int max = maxConcurrent == null ? 0 : maxConcurrent;
        int cur = currentConcurrent == null ? 0 : currentConcurrent;
        return Math.max(0, max - cur);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("trunkId", getTrunkId())
            .append("trunkCode", getTrunkCode())
            .append("trunkName", getTrunkName())
            .append("carrier", getCarrier())
            .append("lineType", getLineType())
            .append("vendor", getVendor())
            .append("gatewayHost", getGatewayHost())
            .append("gatewayPort", getGatewayPort())
            .append("maxConcurrent", getMaxConcurrent())
            .append("currentConcurrent", getCurrentConcurrent())
            .append("priority", getPriority())
            .append("weight", getWeight())
            .append("healthStatus", getHealthStatus())
            .append("enableFlag", getEnableFlag())
            .append("connectRate", getConnectRate())
            .toString();
    }
}
