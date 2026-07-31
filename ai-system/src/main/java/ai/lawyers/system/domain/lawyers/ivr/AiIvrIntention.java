package ai.lawyers.system.domain.lawyers.ivr;

import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class AiIvrIntention extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long intentionId;

    @Excel(name = "意图名称")
    private String intentionName;

    @Excel(name = "意图编码")
    private String intentionCode;

    @Excel(name = "描述")
    private String description;

    @Excel(name = "意图分类")
    private String category;

    @Excel(name = "正则匹配模式")
    private String regexPattern;

    private String promptTemplate;

    private String exampleUtterances;

    private Long modelId;

    @Excel(name = "优先级")
    private Integer priority;

    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    public void setIntentionId(Long intentionId) { this.intentionId = intentionId; }
    public Long getIntentionId() { return intentionId; }

    public void setIntentionName(String intentionName) { this.intentionName = intentionName; }
    public String getIntentionName() { return intentionName; }

    public void setIntentionCode(String intentionCode) { this.intentionCode = intentionCode; }
    public String getIntentionCode() { return intentionCode; }

    public void setDescription(String description) { this.description = description; }
    public String getDescription() { return description; }

    public void setCategory(String category) { this.category = category; }
    public String getCategory() { return category; }

    public void setRegexPattern(String regexPattern) { this.regexPattern = regexPattern; }
    public String getRegexPattern() { return regexPattern; }

    public void setPromptTemplate(String promptTemplate) { this.promptTemplate = promptTemplate; }
    public String getPromptTemplate() { return promptTemplate; }

    public void setExampleUtterances(String exampleUtterances) { this.exampleUtterances = exampleUtterances; }
    public String getExampleUtterances() { return exampleUtterances; }

    public void setModelId(Long modelId) { this.modelId = modelId; }
    public Long getModelId() { return modelId; }

    public void setPriority(Integer priority) { this.priority = priority; }
    public Integer getPriority() { return priority; }

    public void setStatus(String status) { this.status = status; }
    public String getStatus() { return status; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("intentionId", getIntentionId())
            .append("intentionName", getIntentionName())
            .append("intentionCode", getIntentionCode())
            .append("category", getCategory())
            .append("priority", getPriority())
            .append("status", getStatus())
            .toString();
    }
}
