package ai.lawyers.system.domain.lawyers.trunk;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 线路质量统计 对象 ai_trunk_metric
 *
 * 由定时任务按分钟聚合 ai_call_dial_log 生成，用于线路质量趋势展示与告警判定。
 */
public class AiTrunkMetric extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long metricId;

    private Long trunkId;

    private String trunkCode;

    private String carrier;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date statTime;

    /** 统计维度 1=分钟 2=小时 3=天 */
    private String statDimension;

    private Integer totalCalls;

    private Integer connectedCalls;

    private Integer failedCalls;

    private BigDecimal connectRate;

    private BigDecimal asr;

    private Integer acd;

    private Long totalDuration;

    private Integer avgRingDuration;

    private Integer maxConcurrent;

    private BigDecimal avgMos;

    private BigDecimal avgPacketLoss;

    public void setMetricId(Long metricId) { this.metricId = metricId; }

    public Long getMetricId() { return metricId; }

    public void setTrunkId(Long trunkId) { this.trunkId = trunkId; }

    public Long getTrunkId() { return trunkId; }

    public void setTrunkCode(String trunkCode) { this.trunkCode = trunkCode; }

    public String getTrunkCode() { return trunkCode; }

    public void setCarrier(String carrier) { this.carrier = carrier; }

    public String getCarrier() { return carrier; }

    public void setStatTime(Date statTime) { this.statTime = statTime; }

    public Date getStatTime() { return statTime; }

    public void setStatDimension(String statDimension) { this.statDimension = statDimension; }

    public String getStatDimension() { return statDimension; }

    public void setTotalCalls(Integer totalCalls) { this.totalCalls = totalCalls; }

    public Integer getTotalCalls() { return totalCalls; }

    public void setConnectedCalls(Integer connectedCalls) { this.connectedCalls = connectedCalls; }

    public Integer getConnectedCalls() { return connectedCalls; }

    public void setFailedCalls(Integer failedCalls) { this.failedCalls = failedCalls; }

    public Integer getFailedCalls() { return failedCalls; }

    public void setConnectRate(BigDecimal connectRate) { this.connectRate = connectRate; }

    public BigDecimal getConnectRate() { return connectRate; }

    public void setAsr(BigDecimal asr) { this.asr = asr; }

    public BigDecimal getAsr() { return asr; }

    public void setAcd(Integer acd) { this.acd = acd; }

    public Integer getAcd() { return acd; }

    public void setTotalDuration(Long totalDuration) { this.totalDuration = totalDuration; }

    public Long getTotalDuration() { return totalDuration; }

    public void setAvgRingDuration(Integer avgRingDuration) { this.avgRingDuration = avgRingDuration; }

    public Integer getAvgRingDuration() { return avgRingDuration; }

    public void setMaxConcurrent(Integer maxConcurrent) { this.maxConcurrent = maxConcurrent; }

    public Integer getMaxConcurrent() { return maxConcurrent; }

    public void setAvgMos(BigDecimal avgMos) { this.avgMos = avgMos; }

    public BigDecimal getAvgMos() { return avgMos; }

    public void setAvgPacketLoss(BigDecimal avgPacketLoss) { this.avgPacketLoss = avgPacketLoss; }

    public BigDecimal getAvgPacketLoss() { return avgPacketLoss; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("metricId", getMetricId())
            .append("trunkCode", getTrunkCode())
            .append("statTime", getStatTime())
            .append("totalCalls", getTotalCalls())
            .append("connectedCalls", getConnectedCalls())
            .append("connectRate", getConnectRate())
            .append("acd", getAcd())
            .toString();
    }
}
