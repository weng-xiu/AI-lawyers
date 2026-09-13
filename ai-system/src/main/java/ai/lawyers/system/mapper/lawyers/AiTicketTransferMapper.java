package ai.lawyers.system.mapper.lawyers;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.AiTicketTransfer;

/**
 * 工单跨域转办流水 Mapper（F3）
 *
 * @author ai-lawyers
 */
public interface AiTicketTransferMapper
{
    public AiTicketTransfer selectAiTicketTransferByTransferId(Long transferId);

    /** 幂等查询：按调用方幂等键 */
    public AiTicketTransfer selectByIdempotentKey(String idempotentKey);

    /** 按外部条线+外部工单号查询（回调定位） */
    public AiTicketTransfer selectByExternalTicketNo(@Param("externalType") String externalType,
                                                     @Param("externalTicketNo") String externalTicketNo);

    public List<AiTicketTransfer> selectAiTicketTransferList(AiTicketTransfer aiTicketTransfer);

    public int insertAiTicketTransfer(AiTicketTransfer aiTicketTransfer);

    /** 回调/来单回写外部状态 */
    public int updateCallbackResult(@Param("transferId") Long transferId,
                                    @Param("externalTicketNo") String externalTicketNo,
                                    @Param("externalStatus") String externalStatus,
                                    @Param("transferStatus") String transferStatus,
                                    @Param("callbackPayload") String callbackPayload,
                                    @Param("callbackTime") Date callbackTime);

    /** 推送结果更新（失败记录原因与重试计划） */
    public int updatePushResult(@Param("transferId") Long transferId,
                                @Param("transferStatus") String transferStatus,
                                @Param("failReason") String failReason,
                                @Param("retryCount") Integer retryCount,
                                @Param("nextRetryTime") Date nextRetryTime);

    /** 到达重试时间且仍处处理中/失败的转办流水 */
    public List<AiTicketTransfer> selectRetryPending(@Param("now") Date now,
                                                     @Param("maxRetry") int maxRetry);
}
