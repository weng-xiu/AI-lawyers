package ai.lawyers.system.domain.lawyers.quality;

import ai.lawyers.common.core.domain.BaseEntity;

/**
 * P1-7：质检评分模板 ai_quality_template（维度/权重/评分 prompt 自定义）。
 *
 * @author ai-lawyers
 */
public class AiQualityTemplate extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long templateId;
    private String templateName;
    /** 维度权重JSON：[{"key":"serviceNorm","name":"服务规范","weight":1.0}] */
    private String dimensions;
    /** 自定义评分系统提示词，空则用内置默认 */
    private String scorePrompt;
    /** 1默认生效模板 */
    private String isDefault;
    /** 0启用 1停用 */
    private String status;

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public String getDimensions() { return dimensions; }
    public void setDimensions(String dimensions) { this.dimensions = dimensions; }

    public String getScorePrompt() { return scorePrompt; }
    public void setScorePrompt(String scorePrompt) { this.scorePrompt = scorePrompt; }

    public String getIsDefault() { return isDefault; }
    public void setIsDefault(String isDefault) { this.isDefault = isDefault; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
