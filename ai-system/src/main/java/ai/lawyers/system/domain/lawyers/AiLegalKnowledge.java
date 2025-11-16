package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.annotation.Excel.ColumnType;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 法律知识库表 ai_legal_knowledge
 * 
 * @author AI Lawyers
 */
public class AiLegalKnowledge extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 知识ID */
    @Excel(name = "知识ID", cellType = ColumnType.NUMERIC)
    private Long knowledgeId;

    /** 知识标题 */
    @Excel(name = "知识标题")
    private String title;

    /** 分类ID */
    @Excel(name = "分类ID", cellType = ColumnType.NUMERIC)
    private Long categoryId;

    /** 知识内容 */
    @Excel(name = "知识内容")
    private String content;

    /** 法律条文 */
    @Excel(name = "法律条文")
    private String lawArticle;

    /** 来源 */
    @Excel(name = "来源")
    private String source;

    /** 关键词 */
    @Excel(name = "关键词")
    private String keywords;

    /** 浏览次数 */
    @Excel(name = "浏览次数", cellType = ColumnType.NUMERIC)
    private Long viewCount;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 审核状态（0待审核 1审核通过 2审核不通过） */
    @Excel(name = "审核状态", readConverterExp = "0=待审核,1=审核通过,2=审核不通过")
    private String auditStatus;

    /** 审核人 */
    @Excel(name = "审核人")
    private String auditBy;

    /** 审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "审核时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date auditTime;

    /** 审核备注 */
    @Excel(name = "审核备注")
    private String auditRemark;

    public Long getKnowledgeId()
    {
        return knowledgeId;
    }

    public void setKnowledgeId(Long knowledgeId)
    {
        this.knowledgeId = knowledgeId;
    }

    @NotBlank(message = "知识标题不能为空")
    @Size(min = 0, max = 200, message = "知识标题不能超过200个字符")
    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public Long getCategoryId()
    {
        return categoryId;
    }

    public void setCategoryId(Long categoryId)
    {
        this.categoryId = categoryId;
    }

    @NotBlank(message = "知识内容不能为空")
    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getLawArticle()
    {
        return lawArticle;
    }

    public void setLawArticle(String lawArticle)
    {
        this.lawArticle = lawArticle;
    }

    @Size(min = 0, max = 200, message = "来源不能超过200个字符")
    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    @Size(min = 0, max = 500, message = "关键词不能超过500个字符")
    public String getKeywords()
    {
        return keywords;
    }

    public void setKeywords(String keywords)
    {
        this.keywords = keywords;
    }

    public Long getViewCount()
    {
        return viewCount;
    }

    public void setViewCount(Long viewCount)
    {
        this.viewCount = viewCount;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getAuditStatus()
    {
        return auditStatus;
    }

    public void setAuditStatus(String auditStatus)
    {
        this.auditStatus = auditStatus;
    }

    public String getAuditBy()
    {
        return auditBy;
    }

    public void setAuditBy(String auditBy)
    {
        this.auditBy = auditBy;
    }

    public Date getAuditTime()
    {
        return auditTime;
    }

    public void setAuditTime(Date auditTime)
    {
        this.auditTime = auditTime;
    }

    public String getAuditRemark()
    {
        return auditRemark;
    }

    public void setAuditRemark(String auditRemark)
    {
        this.auditRemark = auditRemark;
    }
    
    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("knowledgeId", getKnowledgeId())
            .append("title", getTitle())
            .append("categoryId", getCategoryId())
            .append("content", getContent())
            .append("lawArticle", getLawArticle())
            .append("source", getSource())
            .append("keywords", getKeywords())
            .append("viewCount", getViewCount())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}