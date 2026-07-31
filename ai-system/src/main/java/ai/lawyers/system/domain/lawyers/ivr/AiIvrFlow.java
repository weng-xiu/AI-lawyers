package ai.lawyers.system.domain.lawyers.ivr;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

public class AiIvrFlow extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long flowId;

    @Excel(name = "流程名称")
    private String flowName;

    @Excel(name = "流程编码")
    private String flowCode;

    @Excel(name = "流程描述")
    private String description;

    private String flowData;

    @Excel(name = "状态", readConverterExp = "0=草稿,1=已发布,2=停用")
    private String status;

    @Excel(name = "版本号")
    private Integer version;

    @Excel(name = "流程分类")
    private String category;

    @Excel(name = "是否默认流程", readConverterExp = "0=否,1=是")
    private String isDefault;

    public void setFlowId(Long flowId)
    {
        this.flowId = flowId;
    }

    public Long getFlowId()
    {
        return flowId;
    }

    public void setFlowName(String flowName)
    {
        this.flowName = flowName;
    }

    public String getFlowName()
    {
        return flowName;
    }

    public void setFlowCode(String flowCode)
    {
        this.flowCode = flowCode;
    }

    public String getFlowCode()
    {
        return flowCode;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getDescription()
    {
        return description;
    }

    public void setFlowData(String flowData)
    {
        this.flowData = flowData;
    }

    public String getFlowData()
    {
        return flowData;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
    }

    public Integer getVersion()
    {
        return version;
    }

    public void setCategory(String category)
    {
        this.category = category;
    }

    public String getCategory()
    {
        return category;
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
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("flowId", getFlowId())
            .append("flowName", getFlowName())
            .append("flowCode", getFlowCode())
            .append("description", getDescription())
            .append("status", getStatus())
            .append("version", getVersion())
            .append("category", getCategory())
            .append("isDefault", getIsDefault())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
