package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 工单跨域转办流水对象 ai_ticket_transfer（F3）
 *
 * <p>双向：OUT=12348 转出（法援/调解/公证/鉴定/仲裁/12345），IN=外部转入（如 12345 转来法律诉求）。
 * idempotentKey 唯一约束保证回调/重试幂等。</p>
 *
 * @author ai-lawyers
 */
public class AiTicketTransfer extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long transferId;

    /** 本系统工单ID */
    private Long ticketId;

    /** 本系统工单号（冗余） */
    @Excel(name = "工单号")
    private String ticketNo;

    /** 方向 OUT/IN */
    @Excel(name = "方向", readConverterExp = "OUT=转出,IN=转入")
    private String direction;

    /** 外部条线 */
    @Excel(name = "外部条线")
    private String externalType;

    private Long orgId;

    @Excel(name = "协同机构")
    private String orgName;

    @Excel(name = "外部工单号")
    private String externalTicketNo;

    @Excel(name = "外部状态")
    private String externalStatus;

    /** 流水状态 0处理中 1成功 2失败 3人工处理 */
    @Excel(name = "流水状态", readConverterExp = "0=处理中,1=成功,2=失败,3=人工处理")
    private String transferStatus;

    /** 请求报文（最小必要字段） */
    private String requestPayload;

    /** 最近回调报文留痕 */
    private String callbackPayload;

    /** 幂等键（唯一） */
    private String idempotentKey;

    private String failReason;

    private Integer retryCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date nextRetryTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "发起时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date transferTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "回调时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date callbackTime;

    public Long getTransferId() { return transferId; }
    public void setTransferId(Long transferId) { this.transferId = transferId; }

    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }

    public String getTicketNo() { return ticketNo; }
    public void setTicketNo(String ticketNo) { this.ticketNo = ticketNo; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getExternalType() { return externalType; }
    public void setExternalType(String externalType) { this.externalType = externalType; }

    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }

    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }

    public String getExternalTicketNo() { return externalTicketNo; }
    public void setExternalTicketNo(String externalTicketNo) { this.externalTicketNo = externalTicketNo; }

    public String getExternalStatus() { return externalStatus; }
    public void setExternalStatus(String externalStatus) { this.externalStatus = externalStatus; }

    public String getTransferStatus() { return transferStatus; }
    public void setTransferStatus(String transferStatus) { this.transferStatus = transferStatus; }

    public String getRequestPayload() { return requestPayload; }
    public void setRequestPayload(String requestPayload) { this.requestPayload = requestPayload; }

    public String getCallbackPayload() { return callbackPayload; }
    public void setCallbackPayload(String callbackPayload) { this.callbackPayload = callbackPayload; }

    public String getIdempotentKey() { return idempotentKey; }
    public void setIdempotentKey(String idempotentKey) { this.idempotentKey = idempotentKey; }

    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public Date getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(Date nextRetryTime) { this.nextRetryTime = nextRetryTime; }

    public Date getTransferTime() { return transferTime; }
    public void setTransferTime(Date transferTime) { this.transferTime = transferTime; }

    public Date getCallbackTime() { return callbackTime; }
    public void setCallbackTime(Date callbackTime) { this.callbackTime = callbackTime; }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("transferId", getTransferId())
                .append("ticketNo", getTicketNo())
                .append("direction", getDirection())
                .append("externalType", getExternalType())
                .append("orgName", getOrgName())
                .append("externalTicketNo", getExternalTicketNo())
                .append("externalStatus", getExternalStatus())
                .append("transferStatus", getTransferStatus())
                .append("idempotentKey", getIdempotentKey())
                .append("retryCount", getRetryCount())
                .toString();
    }
}
