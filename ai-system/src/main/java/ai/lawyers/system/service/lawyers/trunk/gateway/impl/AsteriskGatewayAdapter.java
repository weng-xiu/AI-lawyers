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
 * Asterisk 网关适配器（AMI - Asterisk Manager Interface）
 *
 * 通过 AMI 文本协议下发 Originate / Hangup / Redirect / SIPshowpeer 动作。
 */
@Component
public class AsteriskGatewayAdapter implements ICallGatewayAdapter
{
    private static final Logger log = LoggerFactory.getLogger(AsteriskGatewayAdapter.class);

    private static final String CRLF = "\r\n";

    @Value("${call.gateway.asterisk.amiPort:5038}")
    private int amiPort;

    @Value("${call.gateway.asterisk.amiUser:admin}")
    private String amiUser;

    @Value("${call.gateway.asterisk.amiPassword:amp111}")
    private String amiPassword;

    @Value("${call.gateway.asterisk.timeout:5000}")
    private int timeout;

    @Value("${call.gateway.asterisk.context:from-internal}")
    private String context;

    @Override
    public String getVendor()
    {
        return "ASTERISK";
    }

    @Override
    public DialResult originate(AiCallTrunk trunk, DialRequest request)
    {
        String callUuid = UUID.randomUUID().toString();
        String callee = NumberTransformUtils.transform(request.getCalleeNumber(), trunk);
        String caller = NumberTransformUtils.resolveCaller(request.getCallerNumber(), trunk);

        StringBuilder action = new StringBuilder();
        action.append("Action: Originate").append(CRLF);
        action.append("Channel: SIP/").append(trunk.getTrunkCode()).append("/").append(callee).append(CRLF);
        action.append("CallerID: ").append(caller).append(CRLF);
        action.append("Timeout: ")
              .append((request.getRingTimeout() == null ? 45 : request.getRingTimeout()) * 1000).append(CRLF);
        action.append("Async: true").append(CRLF);
        action.append("ActionID: ").append(callUuid).append(CRLF);
        action.append("Variable: AI_CALL_UUID=").append(callUuid).append(CRLF);
        action.append("Variable: AI_TRUNK_CODE=").append(trunk.getTrunkCode()).append(CRLF);
        if (request.getRecordId() != null)
        {
            action.append("Variable: AI_RECORD_ID=").append(request.getRecordId()).append(CRLF);
        }

        if ("BRIDGE_AGENT".equals(request.getAnswerAction()) && request.getAgentExtension() != null
                && !request.getAgentExtension().isEmpty())
        {
            action.append("Exten: ").append(request.getAgentExtension()).append(CRLF);
            action.append("Context: ").append(context).append(CRLF);
            action.append("Priority: 1").append(CRLF);
        }
        else
        {
            action.append("Application: Playback").append(CRLF);
            action.append("Data: silence/1").append(CRLF);
        }
        action.append(CRLF);

        try
        {
            String response = sendAmiAction(trunk, action.toString());
            if (response != null && response.contains("Response: Success"))
            {
                DialResult result = DialResult.ok(callUuid);
                result.setDialStatus(DialStatusEnum.DIALING.getCode());
                log.info("[Asterisk] Originate 成功 trunk={} callee={} uuid={}",
                        trunk.getTrunkCode(), callee, callUuid);
                return result;
            }
            String reason = response == null ? "AMI 无响应" : response.trim();
            log.warn("[Asterisk] Originate 失败 trunk={} resp={}", trunk.getTrunkCode(), reason);
            DialResult fail = DialResult.fail("GATEWAY_REJECT", reason);
            fail.setDialStatus(DialStatusEnum.FAILED.getCode());
            return fail;
        }
        catch (Exception e)
        {
            log.error("[Asterisk] Originate 异常 trunk={}", trunk.getTrunkCode(), e);
            DialResult fail = DialResult.fail("GATEWAY_ERROR", e.getMessage());
            fail.setDialStatus(DialStatusEnum.FAILED.getCode());
            return fail;
        }
    }

    @Override
    public boolean hangup(AiCallTrunk trunk, String callUuid)
    {
        String action = "Action: Hangup" + CRLF + "Channel: " + callUuid + CRLF + CRLF;
        try
        {
            String resp = sendAmiAction(trunk, action);
            return resp != null && resp.contains("Response: Success");
        }
        catch (Exception e)
        {
            log.error("[Asterisk] Hangup 异常 uuid={}", callUuid, e);
            return false;
        }
    }

    @Override
    public boolean bridgeToAgent(AiCallTrunk trunk, String callUuid, String extension)
    {
        String action = "Action: Redirect" + CRLF
                + "Channel: " + callUuid + CRLF
                + "Exten: " + extension + CRLF
                + "Context: " + context + CRLF
                + "Priority: 1" + CRLF + CRLF;
        try
        {
            String resp = sendAmiAction(trunk, action);
            return resp != null && resp.contains("Response: Success");
        }
        catch (Exception e)
        {
            log.error("[Asterisk] Redirect 异常 uuid={}", callUuid, e);
            return false;
        }
    }

    @Override
    public GatewayHealth checkHealth(AiCallTrunk trunk)
    {
        long start = System.currentTimeMillis();
        String action = "Action: SIPshowpeer" + CRLF + "Peer: " + trunk.getTrunkCode() + CRLF + CRLF;
        try
        {
            String resp = sendAmiAction(trunk, action);
            long cost = System.currentTimeMillis() - start;
            if (resp == null)
            {
                return GatewayHealth.down("AMI 无响应");
            }
            if (resp.contains("Status: OK") || resp.contains("Response: Success"))
            {
                return GatewayHealth.up(cost);
            }
            return GatewayHealth.down("Peer 状态异常: " + trunk.getTrunkCode());
        }
        catch (Exception e)
        {
            return GatewayHealth.down(e.getMessage());
        }
    }

    private String sendAmiAction(AiCallTrunk trunk, String action) throws IOException
    {
        try (Socket socket = new Socket())
        {
            socket.connect(new InetSocketAddress(trunk.getGatewayHost(), amiPort), timeout);
            socket.setSoTimeout(timeout);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();

            // 读取 banner: Asterisk Call Manager/x.x.x
            reader.readLine();

            String login = "Action: Login" + CRLF
                    + "Username: " + amiUser + CRLF
                    + "Secret: " + amiPassword + CRLF + CRLF;
            out.write(login.getBytes(StandardCharsets.UTF_8));
            out.flush();
            String loginResp = readResponse(reader);
            if (loginResp == null || !loginResp.contains("Response: Success"))
            {
                throw new IOException("AMI 登录失败: " + loginResp);
            }

            out.write(action.getBytes(StandardCharsets.UTF_8));
            out.flush();
            String resp = readResponse(reader);

            out.write(("Action: Logoff" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
            out.flush();
            return resp;
        }
    }

    /** AMI 响应以空行分隔 */
    private String readResponse(BufferedReader reader) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null)
        {
            if (line.trim().isEmpty())
            {
                if (sb.length() > 0)
                {
                    break;
                }
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
