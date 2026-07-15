package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.annotation.Excel.ColumnType;
import ai.lawyers.common.core.domain.BaseEntity;

public class AiCallTransfer extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long transferId;

    @Excel(name = "来电记录ID", cellType = ColumnType.NUMERIC)
    private Long recordId;

    @Excel(name = "转出坐席ID", cellType = ColumnType.NUMERIC)
    private Long fromAgentId;

    @Excel(name = "转出坐席名称")
    private String fromAgentName;

    @Excel(name = "转入坐席ID", cellType = ColumnType.NUMERIC)
    private Long toAgentId;

    @Excel(name = "转入坐席名称")
    private String toAgentName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "转接时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date transferTime;

    @Excel(name = "转接原因")
    private String reason;

    public void setTransferId(Long transferId) 
    {
        this.transferId = transferId;
    }

    public Long getTransferId() 
    {
        return transferId;
    }
    public void setRecordId(Long recordId) 
    {
        this.recordId = recordId;
    }

    public Long getRecordId() 
    {
        return recordId;
    }
    public void setFromAgentId(Long fromAgentId) 
    {
        this.fromAgentId = fromAgentId;
    }

    public Long getFromAgentId() 
    {
        return fromAgentId;
    }
    public void setFromAgentName(String fromAgentName) 
    {
        this.fromAgentName = fromAgentName;
    }

    public String getFromAgentName() 
    {
        return fromAgentName;
    }
    public void setToAgentId(Long toAgentId) 
    {
        this.toAgentId = toAgentId;
    }

    public Long getToAgentId() 
    {
        return toAgentId;
    }
    public void setToAgentName(String toAgentName) 
    {
        this.toAgentName = toAgentName;
    }

    public String getToAgentName() 
    {
        return toAgentName;
    }
    public void setTransferTime(Date transferTime) 
    {
        this.transferTime = transferTime;
    }

    public Date getTransferTime() 
    {
        return transferTime;
    }
    public void setReason(String reason) 
    {
        this.reason = reason;
    }

    public String getReason() 
    {
        return reason;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("transferId", getTransferId())
            .append("recordId", getRecordId())
            .append("fromAgentId", getFromAgentId())
            .append("fromAgentName", getFromAgentName())
            .append("toAgentId", getToAgentId())
            .append("toAgentName", getToAgentName())
            .append("transferTime", getTransferTime())
            .append("reason", getReason())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("remark", getRemark())
            .toString();
    }
}
