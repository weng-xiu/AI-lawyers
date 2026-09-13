package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiTicketTransfer;

/**
 * 工单跨域转办 Service（F3）
 *
 * <p>双向协同：12348 一键转出法援/调解/公证/鉴定/仲裁/12345；外部渠道（如 12345）转来法律诉求来单。
 * 所有外部交互按 idempotentKey 幂等，报文遵循 PII 最小必要原则。</p>
 *
 * @author ai-lawyers
 */
public interface IAiTicketTransferService
{
    /**
     * 坐席发起转办：回写工单外部字段 + 写转办流水；API 机构立即推送（失败入重试队列），
     * MANUAL/FILE 机构置人工处理。
     *
     * @param ticketId 本系统工单ID
     * @param orgId    目标机构ID
     * @param remark   转办备注（最小必要）
     * @param operator 操作人（坐席账号）
     * @return 转办流水
     */
    public AiTicketTransfer transferOut(Long ticketId, Long orgId, String remark, String operator);

    /**
     * 外部系统受理结果回写（幂等）。
     *
     * @param idempotentKey    调用方带回的幂等键（优先定位）
     * @param externalType     外部条线
     * @param externalTicketNo 外部工单号（idempotentKey 缺失时与 externalType 组合定位）
     * @param externalStatus   外部受理态 PENDING/ACCEPTED/PROCESSING/DONE/REJECTED/FAILED
     * @param callbackPayload  回调原文（留痕）
     * @return 更新后的流水，定位不到返回 null
     */
    public AiTicketTransfer handleCallback(String idempotentKey, String externalType, String externalTicketNo,
                                           String externalStatus, String callbackPayload);

    /**
     * 外部渠道转入来单（如 12345 转来法律诉求），幂等创建工单+IN 流水+会话索引。
     *
     * @return 转办流水
     */
    public AiTicketTransfer receiveInbound(String externalType, String externalTicketNo, String idempotentKey,
                                           String callerNumber, String callerName, String title, String content,
                                           String priority, String payload);

    public List<AiTicketTransfer> selectTransferList(AiTicketTransfer query);

    public AiTicketTransfer selectTransferById(Long transferId);

    /**
     * 手动重新推送失败的转办单（仅 API 机构、流水状态失败/处理中可重试）。
     */
    public int retryTransfer(Long transferId);

    /**
     * 定时任务调用：推送到期重试队列（指数退避，超过最大次数转人工）。
     *
     * @return 本轮处理条数
     */
    public int processRetryQueue();
}
