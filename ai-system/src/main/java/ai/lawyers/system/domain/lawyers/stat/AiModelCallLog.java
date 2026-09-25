package ai.lawyers.system.domain.lawyers.stat;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 大模型调用明细日志对象 ai_model_call_log（P3-E5）
 *
 * <p>记录每次 chat/embed/rerank 调用的业务场景、模型快照、Token 用量、
 * 单价与费用快照、耗时、尝试次数与结果，是成本与质量看板的数据源。</p>
 *
 * @author ai-lawyers
 * @date 2026-09-25
 */
public class AiModelCallLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 结果常量：成功 */
    public static final String RESULT_SUCCESS = "1";
    /** 结果常量：失败 */
    public static final String RESULT_FAIL = "0";
    /** 结果常量：舱壁拒绝 */
    public static final String RESULT_REJECT = "2";

    /** 日志ID */
    private Long logId;

    /** 调用发起时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date callTime;

    /** 调用类型：chat/embed/rerank */
    private String kind;

    /** 业务场景：intention/emotion/extract/quality/summary/agent/consultation/rag/rag_index/test/other */
    private String scene;

    /** 模型配置ID */
    private Long configId;

    /** 配置名称快照 */
    private String configName;

    /** 模型类型快照 */
    private String modelType;

    /** 模型名称快照 */
    private String modelName;

    /** 输入Token数（embedding 计入此项） */
    private Integer promptTokens;

    /** 输出Token数 */
    private Integer completionTokens;

    /** 总Token数 */
    private Integer totalTokens;

    /** 输入单价快照（元/千Token） */
    private BigDecimal inputPrice;

    /** 输出单价快照（元/千Token） */
    private BigDecimal outputPrice;

    /** 估算费用（元） */
    private BigDecimal costAmount;

    /** 端到端耗时（毫秒，含重试等待） */
    private Integer elapsedMs;

    /** 实际尝试次数（含首次） */
    private Integer attempts;

    /** 结果：1成功 0失败 2舱壁拒绝 */
    private String result;

    /** 失败原因（截断500字） */
    private String failReason;

    /** 链路traceId */
    private String traceId;

    public Long getLogId()
    {
        return logId;
    }

    public void setLogId(Long logId)
    {
        this.logId = logId;
    }

    public Date getCallTime()
    {
        return callTime;
    }

    public void setCallTime(Date callTime)
    {
        this.callTime = callTime;
    }

    public String getKind()
    {
        return kind;
    }

    public void setKind(String kind)
    {
        this.kind = kind;
    }

    public String getScene()
    {
        return scene;
    }

    public void setScene(String scene)
    {
        this.scene = scene;
    }

    public Long getConfigId()
    {
        return configId;
    }

    public void setConfigId(Long configId)
    {
        this.configId = configId;
    }

    public String getConfigName()
    {
        return configName;
    }

    public void setConfigName(String configName)
    {
        this.configName = configName;
    }

    public String getModelType()
    {
        return modelType;
    }

    public void setModelType(String modelType)
    {
        this.modelType = modelType;
    }

    public String getModelName()
    {
        return modelName;
    }

    public void setModelName(String modelName)
    {
        this.modelName = modelName;
    }

    public Integer getPromptTokens()
    {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens)
    {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens()
    {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens)
    {
        this.completionTokens = completionTokens;
    }

    public Integer getTotalTokens()
    {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens)
    {
        this.totalTokens = totalTokens;
    }

    public BigDecimal getInputPrice()
    {
        return inputPrice;
    }

    public void setInputPrice(BigDecimal inputPrice)
    {
        this.inputPrice = inputPrice;
    }

    public BigDecimal getOutputPrice()
    {
        return outputPrice;
    }

    public void setOutputPrice(BigDecimal outputPrice)
    {
        this.outputPrice = outputPrice;
    }

    public BigDecimal getCostAmount()
    {
        return costAmount;
    }

    public void setCostAmount(BigDecimal costAmount)
    {
        this.costAmount = costAmount;
    }

    public Integer getElapsedMs()
    {
        return elapsedMs;
    }

    public void setElapsedMs(Integer elapsedMs)
    {
        this.elapsedMs = elapsedMs;
    }

    public Integer getAttempts()
    {
        return attempts;
    }

    public void setAttempts(Integer attempts)
    {
        this.attempts = attempts;
    }

    public String getResult()
    {
        return result;
    }

    public void setResult(String result)
    {
        this.result = result;
    }

    public String getFailReason()
    {
        return failReason;
    }

    public void setFailReason(String failReason)
    {
        this.failReason = failReason;
    }

    public String getTraceId()
    {
        return traceId;
    }

    public void setTraceId(String traceId)
    {
        this.traceId = traceId;
    }
}
