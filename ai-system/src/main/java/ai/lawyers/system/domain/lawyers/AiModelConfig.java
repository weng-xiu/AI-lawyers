package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * AI模型参数配置对象 ai_model_config
 * 
 * @author ai-lawyers
 * @date 2025-07-15
 */
public class AiModelConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 配置ID */
    private Long configId;

    /** 配置名称 */
    @Excel(name = "配置名称")
    private String configName;

    /** 模型类型 */
    @Excel(name = "模型类型")
    private String modelType;

    /** 模型名称 */
    @Excel(name = "模型名称")
    private String modelName;

    /** API密钥 */
    @Excel(name = "API密钥")
    private String apiKey;

    /** API地址 */
    @Excel(name = "API地址")
    private String apiUrl;

    /** 最大令牌数 */
    @Excel(name = "最大令牌数")
    private Integer maxTokens;

    /** 温度参数(0.0-1.0) */
    @Excel(name = "温度参数")
    private Double temperature;

    /** Top-p参数(0.0-1.0) */
    @Excel(name = "Top-p参数")
    private Double topP;

    /** 频率惩罚(-2.0-2.0) */
    @Excel(name = "频率惩罚")
    private Double frequencyPenalty;

    /** 存在惩罚(-2.0-2.0) */
    @Excel(name = "存在惩罚")
    private Double presencePenalty;

    /** 请求超时时间(秒) */
    @Excel(name = "请求超时时间")
    private Integer timeout;

    /** 重试次数 */
    @Excel(name = "重试次数")
    private Integer retryCount;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 是否默认（0否 1是） */
    @Excel(name = "是否默认", readConverterExp = "0=否,1=是")
    private String isDefault;

    public void setConfigId(Long configId) 
    {
        this.configId = configId;
    }

    public Long getConfigId() 
    {
        return configId;
    }
    public void setConfigName(String configName) 
    {
        this.configName = configName;
    }

    public String getConfigName() 
    {
        return configName;
    }
    public void setModelType(String modelType) 
    {
        this.modelType = modelType;
    }

    public String getModelType() 
    {
        return modelType;
    }
    public void setModelName(String modelName) 
    {
        this.modelName = modelName;
    }

    public String getModelName() 
    {
        return modelName;
    }
    public void setApiKey(String apiKey) 
    {
        this.apiKey = apiKey;
    }

    public String getApiKey() 
    {
        return apiKey;
    }
    public void setApiUrl(String apiUrl) 
    {
        this.apiUrl = apiUrl;
    }

    public String getApiUrl() 
    {
        return apiUrl;
    }
    public void setMaxTokens(Integer maxTokens) 
    {
        this.maxTokens = maxTokens;
    }

    public Integer getMaxTokens() 
    {
        return maxTokens;
    }
    public void setTemperature(Double temperature) 
    {
        this.temperature = temperature;
    }

    public Double getTemperature() 
    {
        return temperature;
    }
    public void setTopP(Double topP) 
    {
        this.topP = topP;
    }

    public Double getTopP() 
    {
        return topP;
    }
    public void setFrequencyPenalty(Double frequencyPenalty) 
    {
        this.frequencyPenalty = frequencyPenalty;
    }

    public Double getFrequencyPenalty() 
    {
        return frequencyPenalty;
    }
    public void setPresencePenalty(Double presencePenalty) 
    {
        this.presencePenalty = presencePenalty;
    }

    public Double getPresencePenalty() 
    {
        return presencePenalty;
    }
    public void setTimeout(Integer timeout) 
    {
        this.timeout = timeout;
    }

    public Integer getTimeout() 
    {
        return timeout;
    }
    public void setRetryCount(Integer retryCount) 
    {
        this.retryCount = retryCount;
    }

    public Integer getRetryCount() 
    {
        return retryCount;
    }
    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }
    public void setIsDefault(String isDefault) 
    {
        this.isDefault = isDefault;
    }

    public String getIsDefault() 
    {
        return isDefault;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("configId", getConfigId())
            .append("configName", getConfigName())
            .append("modelType", getModelType())
            .append("modelName", getModelName())
            .append("apiKey", getApiKey())
            .append("apiUrl", getApiUrl())
            .append("maxTokens", getMaxTokens())
            .append("temperature", getTemperature())
            .append("topP", getTopP())
            .append("frequencyPenalty", getFrequencyPenalty())
            .append("presencePenalty", getPresencePenalty())
            .append("timeout", getTimeout())
            .append("retryCount", getRetryCount())
            .append("status", getStatus())
            .append("isDefault", getIsDefault())
            .append("remark", getRemark())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}