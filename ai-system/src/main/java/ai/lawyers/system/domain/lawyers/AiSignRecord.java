package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 签署记录对象 ai_sign_record
 * 
 * @author ai-lawyers
 * @date 2026-08-10
 */
public class AiSignRecord extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 签署记录ID */
    private Long signId;

    /** 业务流水号 */
    @Excel(name = "业务流水号")
    private String businessNo;

    /** 签署模板 */
    @Excel(name = "签署模板")
    private String signTemplate;

    /** 签署方姓名 */
    @Excel(name = "签署方姓名")
    private String signParty;

    /** 签署方电话 */
    @Excel(name = "签署方电话")
    private String signPartyPhone;

    /** 签署状态（0待签署 1签署中 2已完成 3已作废） */
    @Excel(name = "签署状态", readConverterExp = "0=待签署,1=签署中,2=已完成,3=已作废")
    private String signStatus;

    /** 签署完成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "签署完成时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date signTime;

    /** 电子签章ID */
    @Excel(name = "电子签章ID")
    private String eSignId;

    /** 存证编号 */
    @Excel(name = "存证编号")
    private String certificateNo;

    /** 签署文件地址 */
    @Excel(name = "签署文件地址")
    private String signFileUrl;

    public void setSignId(Long signId) 
    {
        this.signId = signId;
    }

    public Long getSignId() 
    {
        return signId;
    }
    public void setBusinessNo(String businessNo) 
    {
        this.businessNo = businessNo;
    }

    public String getBusinessNo() 
    {
        return businessNo;
    }
    public void setSignTemplate(String signTemplate) 
    {
        this.signTemplate = signTemplate;
    }

    public String getSignTemplate() 
    {
        return signTemplate;
    }
    public void setSignParty(String signParty) 
    {
        this.signParty = signParty;
    }

    public String getSignParty() 
    {
        return signParty;
    }
    public void setSignPartyPhone(String signPartyPhone) 
    {
        this.signPartyPhone = signPartyPhone;
    }

    public String getSignPartyPhone() 
    {
        return signPartyPhone;
    }
    public void setSignStatus(String signStatus) 
    {
        this.signStatus = signStatus;
    }

    public String getSignStatus() 
    {
        return signStatus;
    }
    public void setSignTime(Date signTime) 
    {
        this.signTime = signTime;
    }

    public Date getSignTime() 
    {
        return signTime;
    }
    public void setESignId(String eSignId) 
    {
        this.eSignId = eSignId;
    }

    public String getESignId() 
    {
        return eSignId;
    }
    public void setCertificateNo(String certificateNo) 
    {
        this.certificateNo = certificateNo;
    }

    public String getCertificateNo() 
    {
        return certificateNo;
    }
    public void setSignFileUrl(String signFileUrl) 
    {
        this.signFileUrl = signFileUrl;
    }

    public String getSignFileUrl() 
    {
        return signFileUrl;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("signId", getSignId())
            .append("businessNo", getBusinessNo())
            .append("signTemplate", getSignTemplate())
            .append("signParty", getSignParty())
            .append("signPartyPhone", getSignPartyPhone())
            .append("signStatus", getSignStatus())
            .append("signTime", getSignTime())
            .append("eSignId", getESignId())
            .append("certificateNo", getCertificateNo())
            .append("signFileUrl", getSignFileUrl())
            .append("remark", getRemark())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
