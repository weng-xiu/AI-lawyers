package ai.lawyers.system.service.impl.lawyers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.DesensitizedUtil;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.common.utils.sign.CallbackSignUtils;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiExternalOrg;
import ai.lawyers.system.domain.lawyers.AiTicketTransfer;
import ai.lawyers.system.domain.lawyers.AiUnifiedSession;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.mapper.lawyers.AiExternalOrgMapper;
import ai.lawyers.system.mapper.lawyers.AiTicketTransferMapper;
import ai.lawyers.system.mapper.lawyers.AiUnifiedSessionMapper;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.IAiTicketTransferService;

/**
 * 工单跨域转办 Service 实现（F3）
 *
 * <p>安全与合规要点：</p>
 * <ul>
 *   <li>幂等：转出按 ticketId+orgId 生成稳定幂等键；转入/回调按 idempotentKey 或 条线+外部工单号 去重；</li>
 *   <li>PII 最小必要：推送报文仅含工单号、事项摘要（截断）、脱敏来电号码，不含身份证/地址等；</li>
 *   <li>签名：API 推送携带 HMAC-SHA256（X-Callback-Timestamp / X-Callback-Sign，指纹为幂等键）；</li>
 *   <li>失败重试：指数退避（5min 起，上限 60min），超过 {@value #MAX_RETRY} 次转人工处理。</li>
 * </ul>
 *
 * @author ai-lawyers
 */
@Service
public class AiTicketTransferServiceImpl implements IAiTicketTransferService
{
    private static final Logger log = LoggerFactory.getLogger(AiTicketTransferServiceImpl.class);

    /** 最大推送次数（超过后转人工） */
    private static final int MAX_RETRY = 5;

    /** 重试基础间隔（毫秒）：5 分钟 */
    private static final long RETRY_BASE_MILLIS = 5 * 60 * 1000L;

    /** 重试间隔上限：60 分钟 */
    private static final long RETRY_MAX_MILLIS = 60 * 60 * 1000L;

    /** 推送超时（毫秒） */
    private static final int PUSH_TIMEOUT_MILLIS = 8000;

    /** 推送报文内容摘要最大长度（PII 最小必要） */
    private static final int CONTENT_MAX_LENGTH = 500;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private AiTicketTransferMapper transferMapper;

    @Autowired
    private AiCallTicketMapper ticketMapper;

    @Autowired
    private AiExternalOrgMapper orgMapper;

    @Autowired
    private AiUnifiedSessionMapper sessionMapper;

    @Autowired
    private IAiCallTicketService ticketService;

    /** 对外回调地址根（推送报文里回传给外部系统，可为空表示不回传） */
    @Value("${call.collab.callback-base-url:}")
    private String callbackBaseUrl;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTicketTransfer transferOut(Long ticketId, Long orgId, String remark, String operator)
    {
        if (ticketId == null || orgId == null)
        {
            throw new ServiceException("工单ID与机构ID不能为空");
        }
        AiCallTicket ticket = ticketMapper.selectAiCallTicketByTicketId(ticketId);
        if (ticket == null)
        {
            throw new ServiceException("工单不存在");
        }
        AiExternalOrg org = orgMapper.selectAiExternalOrgByOrgId(orgId);
        if (org == null)
        {
            throw new ServiceException("协同机构不存在");
        }
        if ("1".equals(org.getStatus()))
        {
            throw new ServiceException("协同机构已停用，无法转办");
        }
        String idempotentKey = "OUT:" + ticketId + ":" + orgId;
        AiTicketTransfer existing = transferMapper.selectByIdempotentKey(idempotentKey);
        if (existing != null)
        {
            // 同一工单对同一机构重复发起：幂等返回既有流水，不重复推送
            log.info("[F3] 转办幂等命中，返回既有流水 transferId={}", existing.getTransferId());
            return existing;
        }

        Date now = new Date();
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTicketId(ticketId);
        transfer.setTicketNo(ticket.getTicketNo());
        transfer.setDirection("OUT");
        transfer.setExternalType(org.getExternalType());
        transfer.setOrgId(orgId);
        transfer.setOrgName(org.getOrgName());
        transfer.setExternalStatus("PENDING");
        transfer.setIdempotentKey(idempotentKey);
        transfer.setTransferTime(now);
        transfer.setRetryCount(0);
        transfer.setRemark(StringUtils.isNotEmpty(remark) ? truncate(remark, 200) : null);
        transfer.setCreateBy(operator);

        // 回写工单外部协同字段
        ticketMapper.updateExternalInfo(ticketId, org.getExternalType(), orgId, null,
                "PENDING", now, now, "OUT");

        if ("API".equals(org.getAccessMode()))
        {
            String payload = buildOutboundPayload(ticket, org, idempotentKey);
            transfer.setRequestPayload(payload);
            transferMapper.insertAiTicketTransfer(transfer);
            pushToExternal(org, transfer);
        }
        else
        {
            // FILE / MANUAL：线下流转，置人工处理
            transfer.setTransferStatus("3");
            transfer.setRequestPayload(buildOutboundPayload(ticket, org, idempotentKey));
            transferMapper.insertAiTicketTransfer(transfer);
            log.info("[F3] 工单 {} 转 {}（{}方式），转人工处理", ticket.getTicketNo(),
                    org.getOrgName(), org.getAccessMode());
        }
        return transfer;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTicketTransfer handleCallback(String idempotentKey, String externalType, String externalTicketNo,
                                           String externalStatus, String callbackPayload)
    {
        if (StringUtils.isEmpty(externalStatus))
        {
            throw new ServiceException("外部状态不能为空");
        }
        AiTicketTransfer transfer = null;
        if (StringUtils.isNotEmpty(idempotentKey))
        {
            transfer = transferMapper.selectByIdempotentKey(idempotentKey);
        }
        if (transfer == null && StringUtils.isNotEmpty(externalType)
                && StringUtils.isNotEmpty(externalTicketNo))
        {
            transfer = transferMapper.selectByExternalTicketNo(externalType, externalTicketNo);
        }
        if (transfer == null)
        {
            log.warn("[F3] 回调定位不到转办流水 idempotentKey={} type={} extNo={}",
                    idempotentKey, externalType, externalTicketNo);
            return null;
        }
        // 已终态成功流水重复回调：幂等直接返回，不重复落库
        if ("1".equals(transfer.getTransferStatus()) && "DONE".equals(transfer.getExternalStatus()))
        {
            return transfer;
        }
        String transferStatus = mapExternalToTransferStatus(externalStatus);
        Date now = new Date();
        String payload = truncate(callbackPayload, 2000);
        transferMapper.updateCallbackResult(transfer.getTransferId(),
                StringUtils.isNotEmpty(externalTicketNo) ? externalTicketNo : transfer.getExternalTicketNo(),
                externalStatus, transferStatus, payload, now);

        ticketMapper.updateExternalCallback(transfer.getTicketId(), externalTicketNo, externalStatus, now);
        transfer.setExternalStatus(externalStatus);
        transfer.setTransferStatus(transferStatus);
        log.info("[F3] 转办回调处理完成 transferId={} status={}/{}",
                transfer.getTransferId(), externalStatus, transferStatus);
        return transfer;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTicketTransfer receiveInbound(String externalType, String externalTicketNo, String idempotentKey,
                                           String callerNumber, String callerName, String title, String content,
                                           String priority, String payload)
    {
        if (StringUtils.isEmpty(externalType) || StringUtils.isEmpty(externalTicketNo))
        {
            throw new ServiceException("外部条线与外部工单号不能为空");
        }
        if (StringUtils.isEmpty(idempotentKey))
        {
            idempotentKey = "IN:" + externalType + ":" + externalTicketNo;
        }
        AiTicketTransfer existing = transferMapper.selectByIdempotentKey(idempotentKey);
        if (existing != null)
        {
            return existing;
        }
        AiTicketTransfer dup = transferMapper.selectByExternalTicketNo(externalType, externalTicketNo);
        if (dup != null)
        {
            return dup;
        }

        Date now = new Date();
        // 1. 建工单（方向 IN）
        AiCallTicket ticket = new AiCallTicket();
        ticket.setTicketNo(ticketService.generateTicketNo());
        ticket.setTitle(StringUtils.isNotEmpty(title) ? truncate(title, 100) : externalType + "转入诉求");
        ticket.setContent(truncate(content, CONTENT_MAX_LENGTH));
        ticket.setPriority(StringUtils.isNotEmpty(priority) ? priority : "2");
        ticket.setStatus("0");
        ticket.setOvertimeFlag(0);
        ticket.setDirection("IN");
        ticket.setExternalType(externalType);
        ticket.setExternalTicketNo(externalTicketNo);
        ticket.setExternalStatus("ACCEPTED");
        ticket.setExternalUpdateTime(now);
        ticket.setTransferTime(now);
        ticket.setCreateBy("external:" + externalType);
        ticketService.insertAiCallTicket(ticket);

        // 2. 写转入流水（本方已接收即视为成功受理）
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTicketId(ticket.getTicketId());
        transfer.setTicketNo(ticket.getTicketNo());
        transfer.setDirection("IN");
        transfer.setExternalType(externalType);
        transfer.setExternalTicketNo(externalTicketNo);
        transfer.setExternalStatus("ACCEPTED");
        transfer.setTransferStatus("1");
        transfer.setIdempotentKey(idempotentKey);
        transfer.setRequestPayload(truncate(payload, 2000));
        transfer.setCallbackTime(now);
        transfer.setTransferTime(now);
        transfer.setCreateBy("external:" + externalType);
        transferMapper.insertAiTicketTransfer(transfer);

        // 3. 写跨渠道会话索引（F6 时间线）
        if (StringUtils.isNotEmpty(callerNumber))
        {
            AiUnifiedSession session = new AiUnifiedSession();
            session.setCallerNumber(callerNumber);
            session.setChannelType("HOTLINE_12345".equals(externalType) ? "PHONE" : "H5");
            session.setBizType("TICKET");
            session.setBizId(String.valueOf(ticket.getTicketId()));
            session.setBizTitle(ticket.getTitle());
            session.setStartTime(now);
            session.setCreateBy("external:" + externalType);
            sessionMapper.insertIgnoreAiUnifiedSession(session);
        }
        log.info("[F3] 外部来单已受理 type={} extNo={} ticketNo={} caller={}",
                externalType, externalTicketNo, ticket.getTicketNo(),
                StringUtils.isNotEmpty(callerNumber) ? DesensitizedUtil.mobilePhone(callerNumber) : "-");
        return transfer;
    }

    @Override
    public List<AiTicketTransfer> selectTransferList(AiTicketTransfer query)
    {
        return transferMapper.selectAiTicketTransferList(query);
    }

    @Override
    public AiTicketTransfer selectTransferById(Long transferId)
    {
        return transferMapper.selectAiTicketTransferByTransferId(transferId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int retryTransfer(Long transferId)
    {
        AiTicketTransfer transfer = transferMapper.selectAiTicketTransferByTransferId(transferId);
        if (transfer == null)
        {
            throw new ServiceException("转办流水不存在");
        }
        if (!"OUT".equals(transfer.getDirection()))
        {
            throw new ServiceException("仅转出流水支持重新推送");
        }
        AiExternalOrg org = resolveOrg(transfer);
        if (org == null || !"API".equals(org.getAccessMode()))
        {
            throw new ServiceException("仅 API 对接机构支持重新推送");
        }
        pushToExternal(org, transfer);
        return 1;
    }

    @Override
    public int processRetryQueue()
    {
        List<AiTicketTransfer> pending = transferMapper.selectRetryPending(new Date(), MAX_RETRY);
        if (pending == null || pending.isEmpty())
        {
            return 0;
        }
        int count = 0;
        for (AiTicketTransfer transfer : pending)
        {
            try
            {
                AiExternalOrg org = resolveOrg(transfer);
                if (org == null)
                {
                    markManual(transfer, "协同机构不存在或已删除");
                    continue;
                }
                if (!"API".equals(org.getAccessMode()))
                {
                    markManual(transfer, "机构对接方式已变更为非API");
                    continue;
                }
                pushToExternal(org, transfer);
                count++;
            }
            catch (Exception e)
            {
                log.error("[F3] 重试转办推送异常 transferId={}", transfer.getTransferId(), e);
            }
        }
        return count;
    }

    /**
     * 执行一次 API 推送并按结果落库（成功终态/失败重试/超次数转人工）。
     */
    private void pushToExternal(AiExternalOrg org, AiTicketTransfer transfer)
    {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String sign = CallbackSignUtils.sign(org.getAppSecret(), transfer.getIdempotentKey(), timestamp);
        String url = org.getApiUrl();
        String payload = transfer.getRequestPayload();
        if (StringUtils.isEmpty(payload))
        {
            AiCallTicket ticket = ticketMapper.selectAiCallTicketByTicketId(transfer.getTicketId());
            payload = buildOutboundPayload(ticket, org, transfer.getIdempotentKey());
            transfer.setRequestPayload(payload);
        }
        int retryCount = transfer.getRetryCount() == null ? 0 : transfer.getRetryCount();
        try
        {
            String resp = doJsonPost(url, payload, timestamp, sign, org.getAppId());
            log.info("[F3] 转办推送成功 transferId={} url={} resp={}", transfer.getTransferId(), url, resp);
            // 推送成功仅表示送达，外部受理态仍以回调为准；保留处理中等待回调
            transferMapper.updatePushResult(transfer.getTransferId(), "0", null,
                    retryCount, new Date(System.currentTimeMillis() + RETRY_BASE_MILLIS));
            // 兜底：若 5 分钟内无回调将进入重试式状态查询通道（P3 按机构协议扩展）
        }
        catch (Exception e)
        {
            int next = retryCount + 1;
            if (next >= MAX_RETRY)
            {
                log.error("[F3] 转办推送超过最大重试次数，转人工 transferId={}", transfer.getTransferId(), e);
                transferMapper.updatePushResult(transfer.getTransferId(), "3",
                        truncate("推送失败转人工：" + e.getMessage(), 500), next, null);
            }
            else
            {
                long backoff = Math.min(RETRY_BASE_MILLIS * (1L << (next - 1)), RETRY_MAX_MILLIS);
                log.warn("[F3] 转办推送失败，{}ms 后重试 transferId={} retry={} error={}",
                        backoff, transfer.getTransferId(), next, e.getMessage());
                transferMapper.updatePushResult(transfer.getTransferId(), "2",
                        truncate(e.getMessage(), 500), next, new Date(System.currentTimeMillis() + backoff));
            }
        }
    }

    private void markManual(AiTicketTransfer transfer, String reason)
    {
        transferMapper.updatePushResult(transfer.getTransferId(), "3", reason,
                transfer.getRetryCount() == null ? 0 : transfer.getRetryCount(), null);
    }

    private AiExternalOrg resolveOrg(AiTicketTransfer transfer)
    {
        if (transfer.getOrgId() != null)
        {
            return orgMapper.selectAiExternalOrgByOrgId(transfer.getOrgId());
        }
        return null;
    }

    /**
     * 构造推送报文（PII 最小必要：仅工单号、脱敏号码、标题、摘要、幂等键、时间戳）。
     */
    private String buildOutboundPayload(AiCallTicket ticket, AiExternalOrg org, String idempotentKey)
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotentKey", idempotentKey);
        body.put("ticketNo", ticket.getTicketNo());
        body.put("externalType", org.getExternalType());
        body.put("title", ticket.getTitle());
        body.put("content", truncate(ticket.getContent(), CONTENT_MAX_LENGTH));
        body.put("callerNumber", StringUtils.isNotEmpty(ticket.getCallerNumber())
                ? DesensitizedUtil.mobilePhone(ticket.getCallerNumber()) : null);
        body.put("priority", ticket.getPriority());
        body.put("timestamp", System.currentTimeMillis());
        if (StringUtils.isNotEmpty(callbackBaseUrl))
        {
            body.put("callbackUrl", callbackBaseUrl + "/lawyers/external/ticket/callback");
        }
        try
        {
            return OBJECT_MAPPER.writeValueAsString(body);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("构造转办报文失败", e);
        }
    }

    /**
     * 外部受理态 → 流水状态：DONE 成功；REJECTED/FAILED 失败；其余处理中。
     */
    private String mapExternalToTransferStatus(String externalStatus)
    {
        if ("DONE".equals(externalStatus))
        {
            return "1";
        }
        if ("REJECTED".equals(externalStatus) || "FAILED".equals(externalStatus))
        {
            return "2";
        }
        return "0";
    }

    /**
     * 发送 JSON POST，携带 HMAC 签名头；非 2xx 抛异常由调用方进入重试。
     */
    private String doJsonPost(String urlStr, String json, String timestamp, String sign, String appId)
            throws IOException
    {
        HttpURLConnection conn = null;
        try
        {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(PUSH_TIMEOUT_MILLIS);
            conn.setReadTimeout(PUSH_TIMEOUT_MILLIS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-Callback-Timestamp", timestamp);
            conn.setRequestProperty("X-Callback-Sign", sign);
            if (StringUtils.isNotEmpty(appId))
            {
                conn.setRequestProperty("X-App-Id", appId);
            }
            try (OutputStream os = conn.getOutputStream())
            {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            String resp = readBody(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300)
            {
                throw new IOException("外部接口返回 HTTP " + code + ": " + truncate(resp, 200));
            }
            return resp;
        }
        finally
        {
            if (conn != null)
            {
                conn.disconnect();
            }
        }
    }

    private String readBody(InputStream is) throws IOException
    {
        if (is == null)
        {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private String truncate(String text, int max)
    {
        if (text == null)
        {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
