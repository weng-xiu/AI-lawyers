package ai.lawyers.system.domain.lawyers.sms;

import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 短信模板对象 ai_sms_template
 *
 * @author ai-lawyers
 */
public class AiSmsTemplate extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 模板ID */
    private Long templateId;

    /** 模板名称 */
    @Excel(name = "模板名称")
    private String templateName;

    /** 供应商模板CODE */
    @Excel(name = "供应商模板CODE")
    private String providerTemplateCode;

    /** 模板内容，含 ${变量} 占位 */
    @Excel(name = "模板内容")
    private String content;

    /** 场景（queue/welcome/ticket/visit/generic） */
    @Excel(name = "场景")
    private String sceneType;

    /** 所属短信通道ID */
    private Long configId;

    /** 状态（0停用 1启用） */
    @Excel(name = "状态", readConverterExp = "0=停用,1=启用")
    private String status;

    /** 通道名称（关联查询） */
    @Excel(name = "通道")
    private String configName;

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public String getProviderTemplateCode() { return providerTemplateCode; }
    public void setProviderTemplateCode(String providerTemplateCode) { this.providerTemplateCode = providerTemplateCode; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSceneType() { return sceneType; }
    public void setSceneType(String sceneType) { this.sceneType = sceneType; }

    public Long getConfigId() { return configId; }
    public void setConfigId(Long configId) { this.configId = configId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getConfigName() { return configName; }
    public void setConfigName(String configName) { this.configName = configName; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("templateId", getTemplateId())
            .append("templateName", getTemplateName())
            .append("sceneType", getSceneType())
            .append("configId", getConfigId())
            .append("status", getStatus())
            .toString();
    }
}
