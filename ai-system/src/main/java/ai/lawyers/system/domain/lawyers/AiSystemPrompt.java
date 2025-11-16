package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 系统提示词配置对象 ai_system_prompt
 * 
 * @author ai-lawyers
 * @date 2025-07-15
 */
public class AiSystemPrompt extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 提示词ID */
    private Long promptId;

    /** 提示词名称 */
    @Excel(name = "提示词名称")
    private String promptName;

    /** 提示词类型 */
    @Excel(name = "提示词类型")
    private String promptType;

    /** 场景类型 */
    @Excel(name = "场景类型")
    private String sceneType;

    /** 提示词内容 */
    @Excel(name = "提示词内容")
    private String content;

    /** 变量定义(JSON格式) */
    @Excel(name = "变量定义")
    private String variables;

    /** 是否默认（0否 1是） */
    @Excel(name = "是否默认", readConverterExp = "0=否,1=是")
    private String isDefault;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    public void setPromptId(Long promptId) 
    {
        this.promptId = promptId;
    }

    public Long getPromptId() 
    {
        return promptId;
    }
    public void setPromptName(String promptName) 
    {
        this.promptName = promptName;
    }

    public String getPromptName() 
    {
        return promptName;
    }
    public void setPromptType(String promptType) 
    {
        this.promptType = promptType;
    }

    public String getPromptType() 
    {
        return promptType;
    }
    public void setSceneType(String sceneType) 
    {
        this.sceneType = sceneType;
    }

    public String getSceneType() 
    {
        return sceneType;
    }
    public void setContent(String content) 
    {
        this.content = content;
    }

    public String getContent() 
    {
        return content;
    }
    public void setVariables(String variables) 
    {
        this.variables = variables;
    }

    public String getVariables() 
    {
        return variables;
    }
    public void setIsDefault(String isDefault) 
    {
        this.isDefault = isDefault;
    }

    public String getIsDefault() 
    {
        return isDefault;
    }
    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("promptId", getPromptId())
            .append("promptName", getPromptName())
            .append("promptType", getPromptType())
            .append("sceneType", getSceneType())
            .append("content", getContent())
            .append("variables", getVariables())
            .append("isDefault", getIsDefault())
            .append("status", getStatus())
            .append("remark", getRemark())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}