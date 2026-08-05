package ai.lawyers.system.domain.lawyers;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.annotation.Excel.ColumnType;
import ai.lawyers.common.core.domain.BaseEntity;

public class AiCallLedger extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long ledgerId;

    @Excel(name = "台账编号")
    private String ledgerNo;

    @Excel(name = "关联来电记录ID", cellType = ColumnType.NUMERIC)
    private Long recordId;

    @Excel(name = "关联工单ID", cellType = ColumnType.NUMERIC)
    private Long ticketId;

    @Excel(name = "咨询人姓名")
    private String callerName;

    @Excel(name = "联系电话")
    private String callerPhone;

    @Excel(name = "身份证号")
    private String callerIdCard;

    @Excel(name = "性别", readConverterExp = "0=男,1=女,2=未知")
    private String callerGender;

    @Excel(name = "年龄", cellType = ColumnType.NUMERIC)
    private Integer callerAge;

    @Excel(name = "职业")
    private String callerJob;

    @Excel(name = "工作单位")
    private String callerCompany;

    @Excel(name = "联系地址")
    private String callerAddress;

    @Excel(name = "咨询分类ID", cellType = ColumnType.NUMERIC)
    private Long categoryId;

    @Excel(name = "咨询分类名称")
    private String categoryName;

    @Excel(name = "问题分类")
    private String subCategory;

    @Excel(name = "服务方式", readConverterExp = "1=电话,2=现场,3=网络,4=视频")
    private String serviceType;

    @Excel(name = "来源渠道")
    private String sourceChannel;

    @Excel(name = "承办律师ID", cellType = ColumnType.NUMERIC)
    private Long lawyerId;

    @Excel(name = "承办律师姓名")
    private String lawyerName;

    @Excel(name = "咨询摘要")
    private String consultContent;

    @Excel(name = "涉及金额", cellType = ColumnType.NUMERIC)
    private BigDecimal involveAmount;

    @Excel(name = "律师解答意见")
    private String lawyerAnswer;

    @Excel(name = "咨询时长(分钟)", cellType = ColumnType.NUMERIC)
    private Integer consultDuration;

    @Excel(name = "满意度", readConverterExp = "1=非常满意,2=满意,3=一般,4=不满意")
    private String satisfaction;

    @Excel(name = "是否已回访", readConverterExp = "0=否,1=是")
    private String isVisit;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "回访时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date visitTime;

    @Excel(name = "回访人")
    private String visitBy;

    @Excel(name = "回访意见")
    private String visitOpinion;

    private String delFlag;

    public void setLedgerId(Long ledgerId)
    {
        this.ledgerId = ledgerId;
    }

    public Long getLedgerId()
    {
        return ledgerId;
    }

    public void setLedgerNo(String ledgerNo)
    {
        this.ledgerNo = ledgerNo;
    }

    public String getLedgerNo()
    {
        return ledgerNo;
    }

    public void setRecordId(Long recordId)
    {
        this.recordId = recordId;
    }

    public Long getRecordId()
    {
        return recordId;
    }

    public void setTicketId(Long ticketId)
    {
        this.ticketId = ticketId;
    }

    public Long getTicketId()
    {
        return ticketId;
    }

    public void setCallerName(String callerName)
    {
        this.callerName = callerName;
    }

    public String getCallerName()
    {
        return callerName;
    }

    public void setCallerPhone(String callerPhone)
    {
        this.callerPhone = callerPhone;
    }

    public String getCallerPhone()
    {
        return callerPhone;
    }

    public void setCallerIdCard(String callerIdCard)
    {
        this.callerIdCard = callerIdCard;
    }

    public String getCallerIdCard()
    {
        return callerIdCard;
    }

    public void setCallerGender(String callerGender)
    {
        this.callerGender = callerGender;
    }

    public String getCallerGender()
    {
        return callerGender;
    }

    public void setCallerAge(Integer callerAge)
    {
        this.callerAge = callerAge;
    }

    public Integer getCallerAge()
    {
        return callerAge;
    }

    public void setCallerJob(String callerJob)
    {
        this.callerJob = callerJob;
    }

    public String getCallerJob()
    {
        return callerJob;
    }

    public void setCallerCompany(String callerCompany)
    {
        this.callerCompany = callerCompany;
    }

    public String getCallerCompany()
    {
        return callerCompany;
    }

    public void setCallerAddress(String callerAddress)
    {
        this.callerAddress = callerAddress;
    }

    public String getCallerAddress()
    {
        return callerAddress;
    }

    public void setCategoryId(Long categoryId)
    {
        this.categoryId = categoryId;
    }

    public Long getCategoryId()
    {
        return categoryId;
    }

    public void setCategoryName(String categoryName)
    {
        this.categoryName = categoryName;
    }

    public String getCategoryName()
    {
        return categoryName;
    }

    public void setSubCategory(String subCategory)
    {
        this.subCategory = subCategory;
    }

    public String getSubCategory()
    {
        return subCategory;
    }

    public void setServiceType(String serviceType)
    {
        this.serviceType = serviceType;
    }

    public String getServiceType()
    {
        return serviceType;
    }

    public void setSourceChannel(String sourceChannel)
    {
        this.sourceChannel = sourceChannel;
    }

    public String getSourceChannel()
    {
        return sourceChannel;
    }

    public void setLawyerId(Long lawyerId)
    {
        this.lawyerId = lawyerId;
    }

    public Long getLawyerId()
    {
        return lawyerId;
    }

    public void setLawyerName(String lawyerName)
    {
        this.lawyerName = lawyerName;
    }

    public String getLawyerName()
    {
        return lawyerName;
    }

    public void setConsultContent(String consultContent)
    {
        this.consultContent = consultContent;
    }

    public String getConsultContent()
    {
        return consultContent;
    }

    public void setInvolveAmount(BigDecimal involveAmount)
    {
        this.involveAmount = involveAmount;
    }

    public BigDecimal getInvolveAmount()
    {
        return involveAmount;
    }

    public void setLawyerAnswer(String lawyerAnswer)
    {
        this.lawyerAnswer = lawyerAnswer;
    }

    public String getLawyerAnswer()
    {
        return lawyerAnswer;
    }

    public void setConsultDuration(Integer consultDuration)
    {
        this.consultDuration = consultDuration;
    }

    public Integer getConsultDuration()
    {
        return consultDuration;
    }

    public void setSatisfaction(String satisfaction)
    {
        this.satisfaction = satisfaction;
    }

    public String getSatisfaction()
    {
        return satisfaction;
    }

    public void setIsVisit(String isVisit)
    {
        this.isVisit = isVisit;
    }

    public String getIsVisit()
    {
        return isVisit;
    }

    public void setVisitTime(Date visitTime)
    {
        this.visitTime = visitTime;
    }

    public Date getVisitTime()
    {
        return visitTime;
    }

    public void setVisitBy(String visitBy)
    {
        this.visitBy = visitBy;
    }

    public String getVisitBy()
    {
        return visitBy;
    }

    public void setVisitOpinion(String visitOpinion)
    {
        this.visitOpinion = visitOpinion;
    }

    public String getVisitOpinion()
    {
        return visitOpinion;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("ledgerId", getLedgerId())
            .append("ledgerNo", getLedgerNo())
            .append("recordId", getRecordId())
            .append("ticketId", getTicketId())
            .append("callerName", getCallerName())
            .append("callerPhone", getCallerPhone())
            .append("callerIdCard", getCallerIdCard())
            .append("callerGender", getCallerGender())
            .append("callerAge", getCallerAge())
            .append("callerJob", getCallerJob())
            .append("callerCompany", getCallerCompany())
            .append("callerAddress", getCallerAddress())
            .append("categoryId", getCategoryId())
            .append("categoryName", getCategoryName())
            .append("subCategory", getSubCategory())
            .append("serviceType", getServiceType())
            .append("sourceChannel", getSourceChannel())
            .append("lawyerId", getLawyerId())
            .append("lawyerName", getLawyerName())
            .append("consultContent", getConsultContent())
            .append("involveAmount", getInvolveAmount())
            .append("lawyerAnswer", getLawyerAnswer())
            .append("consultDuration", getConsultDuration())
            .append("satisfaction", getSatisfaction())
            .append("isVisit", getIsVisit())
            .append("visitTime", getVisitTime())
            .append("visitBy", getVisitBy())
            .append("visitOpinion", getVisitOpinion())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .append("delFlag", getDelFlag())
            .toString();
    }
}
