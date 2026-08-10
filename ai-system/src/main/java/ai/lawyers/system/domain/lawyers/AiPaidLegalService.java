package ai.lawyers.system.domain.lawyers;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.annotation.Excel.ColumnType;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 有偿法律服务表 ai_paid_legal_service
 * 
 * @author AI Lawyers
 */
public class AiPaidLegalService extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 付费服务ID */
    @Excel(name = "付费服务ID", cellType = ColumnType.NUMERIC)
    private Long paidId;

    /** 服务编号 */
    @Excel(name = "服务编号")
    private String serviceNo;

    /** 客户姓名 */
    @Excel(name = "客户姓名")
    private String customerName;

    /** 客户电话 */
    @Excel(name = "客户电话")
    private String customerPhone;

    /** 服务类型（代书/调解/诉讼代理/法律顾问） */
    @Excel(name = "服务类型", readConverterExp = "代书=代书,调解=调解,诉讼代理=诉讼代理,法律顾问=法律顾问")
    private String serviceType;

    /** 服务金额 */
    @Excel(name = "服务金额")
    private Double amount;

    /** 支付状态（0未付 1已付 2部分付） */
    @Excel(name = "支付状态", readConverterExp = "0=未付,1=已付,2=部分付")
    private String paymentStatus;

    /** 服务状态（0待受理 1处理中 2已完成 3已关闭） */
    @Excel(name = "服务状态", readConverterExp = "0=待受理,1=处理中,2=已完成,3=已关闭")
    private String serviceStatus;

    /** 处理人 */
    @Excel(name = "处理人")
    private String handlerName;

    public Long getPaidId()
    {
        return paidId;
    }

    public void setPaidId(Long paidId)
    {
        this.paidId = paidId;
    }

    public String getServiceNo()
    {
        return serviceNo;
    }

    public void setServiceNo(String serviceNo)
    {
        this.serviceNo = serviceNo;
    }

    public String getCustomerName()
    {
        return customerName;
    }

    public void setCustomerName(String customerName)
    {
        this.customerName = customerName;
    }

    public String getCustomerPhone()
    {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone)
    {
        this.customerPhone = customerPhone;
    }

    public String getServiceType()
    {
        return serviceType;
    }

    public void setServiceType(String serviceType)
    {
        this.serviceType = serviceType;
    }

    public Double getAmount()
    {
        return amount;
    }

    public void setAmount(Double amount)
    {
        this.amount = amount;
    }

    public String getPaymentStatus()
    {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus)
    {
        this.paymentStatus = paymentStatus;
    }

    public String getServiceStatus()
    {
        return serviceStatus;
    }

    public void setServiceStatus(String serviceStatus)
    {
        this.serviceStatus = serviceStatus;
    }

    public String getHandlerName()
    {
        return handlerName;
    }

    public void setHandlerName(String handlerName)
    {
        this.handlerName = handlerName;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("paidId", getPaidId())
            .append("serviceNo", getServiceNo())
            .append("customerName", getCustomerName())
            .append("customerPhone", getCustomerPhone())
            .append("serviceType", getServiceType())
            .append("amount", getAmount())
            .append("paymentStatus", getPaymentStatus())
            .append("serviceStatus", getServiceStatus())
            .append("handlerName", getHandlerName())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
