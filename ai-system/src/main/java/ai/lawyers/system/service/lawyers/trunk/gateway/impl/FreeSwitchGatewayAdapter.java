package ai.lawyers.system.service.lawyers.trunk.gateway.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.domain.lawyers.trunk.DialRequest;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.enums.DialStatusEnum;
import ai.lawyers.system.service.lawyers.trunk.gateway.GatewayHealth;
import ai.lawyers.system.service.lawyers.trunk.gateway.ICallGatewayAdapter;
import ai.lawyers.system.utils.trunk.NumberTransformUtils;

/**
 * FreeSWITCH 网关适配器（ESL - Event Socket Layer 内联模式）
 *
 * 通过 ESL 明文协议下发 originate / uuid_kill / uuid_bridge 命令，
 * 不依赖第三方 ESL 客户端库，仅用 JDK Socket 即可，便于在受限环境部署。
 *
 * 典型 originate 命令：
 * <pre>
 * originate {origination_uuid=xxx,origination_caller_id_number=025xxx,ignore_early_media=true}
 *   sofia/gateway/TRUNK_CM_01/13800138000 &user/1001 XML default
 * </pre>
 */
@Component
public class FreeSwitchGatewayAdapter implements ICallGatewayAdapter
{
    private static final Logger log = LoggerFactory.getLogger(FreeSwitchGatewayAdapter.class);

    /** ESL 端口，FreeSWITCH 默认 8021 */
    @Value("${call.gateway.freeswitch.eslPort:8021}")
    private int eslPort;

    /** ESL 密码，FreeSWITCH 默认 ClueCon */
    @Value("${call.gateway.freeswitch.eslPassword:ClueCon}")
    private String eslPassword;

    /** Socket 连接与读取超时(ms) */
    @Value("${call.gateway.freeswitch.timeout:5000}")
    private int timeout;

    /** 呼叫落地的 dialplan context */
    @Value("${call.gateway.freeswitch.context:default}")
    private String context;

    @Override
    public String getVendor()
    {
        return "FREESWITCH";
    }

    @Override
    public DialResult originate(AiCallTrunk trunk, DialRequest request)
    {
        String callUuid = UUID.randomUUID().toString();
        String callee = NumberTransformUtils.transform(request.getCalleeNumber(), trunk);
        String caller = NumberTransformUtils.resolveCaller(request.getCallerNumber(), trunk);

        StringBuilder vars = new StringBuilder();
        vars.append("origination_uuid=").append(callUuid);
        vars.append(",origination_caller_id_number=").append(caller);
        vars.append(",origination_caller_id_name=").append(caller);
        vars.append(",ignore_early_media=true");
        vars.append(",call_timeout=").append(request.getRingTimeout() == null ? 45 : request.getRingTimeout());
        vars.append(",hangup_after_bridge=true");
        if (request.isEnableRecord())
        {
            vars.append(",execute_on_answer='record_session $${recordings_dir}/").append(callUuid).append(".wav'");
        }
        // 业务侧标识透传，便于 CDR 回写时关联
        if (request.getRecordId() != null)
        {
            vars.append(",ai_record_id=").append(request.getRecordId());
        }
        if (request.getTaskId() != null)
        {
            vars.append(",ai_task_id=").append(request.getTaskId());
        }
        vars.append(",ai_trunk_code=").append(trunk.getTrunkCode());

        boolean mediaEcho = "PORTAUDIO".equalsIgnoreCase(trunk.getProtocol());

        // 被叫腿：PORTAUDIO 走本机声卡回音，否则走该线路对应的 sofia gateway
        String aLeg = mediaEcho ? "portaudio/auto_answer" : "sofia/gateway/" + trunk.getTrunkCode() + "/" + callee;

        // 接通后的动作
        String bLeg;
        if (mediaEcho)
        {
            bLeg = "&echo()";
        }
        else if ("BRIDGE_AGENT".equals(request.getAnswerAction()) && request.getAgentExtension() != null
                && !request.getAgentExtension().isEmpty())
        {
            bLeg = "&bridge(user/" + request.getAgentExtension() + "@" + context + ")";
        }
        else if ("IVR".equals(request.getAnswerAction()) && request.getIvrFlowId() != null)
        {
            bLeg = "&transfer(ivr_" + request.getIvrFlowId() + " XML " + context + ")";
        }
        else
        {
            bLeg = "&park()";
        }

        String command = "originate {" + vars + "}" + aLeg + " " + bLeg;

        try
        {
            String response = sendEslCommand(trunk, command, "bgapi ");
            if (!mediaEcho && isInvalidGateway(response) && isLoopbackHost(trunk))
            {
                String loopbackCommand = "originate {" + vars + "}loopback/" + callee + "/default " + bLeg;
                response = sendEslCommand(trunk, loopbackCommand, "bgapi ");
            }
            if (response != null && response.contains("+OK"))
            {
                DialResult result = DialResult.ok(callUuid);
                result.setDialStatus(DialStatusEnum.DIALING.getCode());
                log.info("[FreeSWITCH] originate 成功 trunk={} callee={} uuid={}",
                        trunk.getTrunkCode(), callee, callUuid);
                return result;
            }
            String reason = response == null ? "网关无响应" : response.trim();
            log.warn("[FreeSWITCH] originate 失败 trunk={} callee={} resp={}",
                    trunk.getTrunkCode(), callee, reason);
            DialResult fail = DialResult.fail("GATEWAY_REJECT", reason);
            fail.setDialStatus(mapFailStatus(reason));
            fail.setHangupCause(extractCause(reason));
            return fail;
        }
        catch (Exception e)
        {
            log.error("[FreeSWITCH] originate 异常 trunk={} callee={}", trunk.getTrunkCode(), callee, e);
            DialResult fail = DialResult.fail("GATEWAY_ERROR", e.getMessage());
            fail.setDialStatus(DialStatusEnum.FAILED.getCode());
            return fail;
        }
    }

    @Override
    public boolean hangup(AiCallTrunk trunk, String callUuid)
    {
        try
        {
            String resp = sendEslCommand(trunk, "uuid_kill " + callUuid);
            return resp != null && resp.contains("+OK");
        }
        catch (Exception e)
        {
            log.error("[FreeSWITCH] hangup 异常 uuid={}", callUuid, e);
            return false;
        }
    }

    @Override
    public boolean bridgeToAgent(AiCallTrunk trunk, String callUuid, String extension)
    {
        try
        {
            String resp = sendEslCommand(trunk,
                    "uuid_transfer " + callUuid + " -both user/" + extension + " XML " + context);
            return resp != null && resp.contains("+OK");
        }
        catch (Exception e)
        {
            log.error("[FreeSWITCH] bridge 异常 uuid={} ext={}", callUuid, extension, e);
            return false;
        }
    }

    @Override
    public GatewayHealth checkHealth(AiCallTrunk trunk)
    {
        long start = System.currentTimeMillis();
        try
        {
            String resp = sendEslCommand(trunk, "sofia status gateway " + trunk.getTrunkCode());
            long cost = System.currentTimeMillis() - start;
            if (resp == null)
            {
                return GatewayHealth.down("ESL 无响应");
            }
            // FreeSWITCH 返回的 gateway 状态里包含 State  REGED / NOREG / DOWN
            if (resp.contains("REGED") || resp.contains("NOREG"))
            {
                GatewayHealth h = GatewayHealth.up(cost);
                h.setRegisterState(resp.contains("REGED") ? "REGED" : "NOREG");
                // 静态 IP 对接的中继常年 NOREG 但可用，故仍判定可达
                return h;
            }
            if (resp.contains("Invalid Gateway"))
            {
                if (isLoopbackHost(trunk))
                {
                    GatewayHealth h = GatewayHealth.up(cost);
                    h.setRegisterState("LOOPBACK");
                    h.setMessage("local loopback mode");
                    return h;
                }
                return GatewayHealth.down("网关未配置: " + trunk.getTrunkCode());
            }
            return GatewayHealth.up(cost);
        }
        catch (Exception e)
        {
            return GatewayHealth.down(e.getMessage());
        }
    }

    /**
     * 通过 ESL 明文协议发送一条 api 命令并读取响应。
     */
    private String sendEslCommand(AiCallTrunk trunk, String command) throws IOException
    {
        return sendEslCommand(trunk, command, "api ");
    }

    private String sendEslCommand(AiCallTrunk trunk, String command, String prefix) throws IOException
    {
        String host = trunk.getGatewayHost();
        try (Socket socket = new Socket())
        {
            socket.connect(new InetSocketAddress(host, eslPort), timeout);
            socket.setSoTimeout(timeout);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();

            // 1. 等待 auth/request
            readBlock(reader);

            // 2. 鉴权
            write(out, "auth " + eslPassword + "\n\n");
            String authResp = readBlock(reader);
            if (authResp == null || !authResp.contains("+OK"))
            {
                throw new IOException("ESL 鉴权失败: " + authResp);
            }

            // 3. 执行命令
            write(out, prefix + command + "\n\n");
            return readBlock(reader);
        }
    }

    private void write(OutputStream out, String content) throws IOException
    {
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /**
     * 读取一个 ESL 响应块：头部以空行结束，若含 Content-Length 则继续读取正文。
     */
    private String readBlock(BufferedReader reader) throws IOException
    {
        StringBuilder header = new StringBuilder();
        int contentLength = 0;
        String line;
        while ((line = reader.readLine()) != null)
        {
            if (line.isEmpty())
            {
                break;
            }
            header.append(line).append('\n');
            if (line.toLowerCase().startsWith("content-length:"))
            {
                try
                {
                    contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                }
                catch (NumberFormatException ignored)
                {
                    contentLength = 0;
                }
            }
        }
        if (contentLength > 0)
        {
            char[] body = new char[contentLength];
            int read = 0;
            while (read < contentLength)
            {
                int n = reader.read(body, read, contentLength - read);
                if (n < 0)
                {
                    break;
                }
                read += n;
            }
            return header + new String(body, 0, Math.max(read, 0));
        }
        return header.toString();
    }

    private boolean isInvalidGateway(String response)
    {
        return response != null
                && (response.contains("INVALID_GATEWAY") || response.contains("Invalid Gateway"));
    }

    private boolean isLoopbackHost(AiCallTrunk trunk)
    {
        String host = trunk.getGatewayHost();
        return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
    }

    /** 将 FreeSWITCH 挂断原因映射为内部拨号状态 */
    private String mapFailStatus(String resp)
    {
        String r = resp.toUpperCase();
        if (r.contains("USER_BUSY"))
        {
            return DialStatusEnum.BUSY.getCode();
        }
        if (r.contains("NO_ANSWER") || r.contains("NO_USER_RESPONSE") || r.contains("ALLOTTED_TIMEOUT"))
        {
            return DialStatusEnum.TIMEOUT.getCode();
        }
        if (r.contains("CALL_REJECTED"))
        {
            return DialStatusEnum.REJECTED.getCode();
        }
        if (r.contains("UNALLOCATED_NUMBER") || r.contains("NO_ROUTE_DESTINATION")
                || r.contains("INVALID_NUMBER_FORMAT"))
        {
            return DialStatusEnum.INVALID_NUMBER.getCode();
        }
        return DialStatusEnum.FAILED.getCode();
    }

    private String extractCause(String resp)
    {
        if (resp == null)
        {
            return "";
        }
        int idx = resp.indexOf("-ERR");
        if (idx >= 0)
        {
            return resp.substring(idx + 4).trim();
        }
        return resp.length() > 64 ? resp.substring(0, 64) : resp.trim();
    }
}
