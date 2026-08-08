package ai.lawyers.system.service.lawyers.trunk.gateway.impl;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
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
 * 通用 HTTP OpenAPI 网关适配器
 *
 * 适配绝大多数提供 RESTful 接口的云 CTI 平台与硬件语音网关管理接口
 * （如中通天鸿、容联七陌、阿里云语音、天翼云 CTI、以及各厂商 SBC 北向接口）。
 *
 * 约定的接口路径（可通过配置覆盖）：
 *   POST {apiBaseUrl}/call/originate  发起外呼
 *   POST {apiBaseUrl}/call/hangup     挂断
 *   POST {apiBaseUrl}/call/transfer   转接
 *   GET  {apiBaseUrl}/trunk/status    线路状态
 *
 * 鉴权采用 HTTP Basic（authUser / authPassword）。若目标平台使用
 * AppKey+Token，可将 token 填入 authPassword，并把 authUser 留空，
 * 此时使用 Bearer 认证。
 */
@Component
public class HttpApiGatewayAdapter implements ICallGatewayAdapter
{
    private static final Logger log = LoggerFactory.getLogger(HttpApiGatewayAdapter.class);

    @Value("${call.gateway.http.originatePath:/call/originate}")
    private String originatePath;

    @Value("${call.gateway.http.hangupPath:/call/hangup}")
    private String hangupPath;

    @Value("${call.gateway.http.transferPath:/call/transfer}")
    private String transferPath;

    @Value("${call.gateway.http.statusPath:/trunk/status}")
    private String statusPath;

    @Value("${call.gateway.http.timeout:5000}")
    private int timeout;

    @Override
    public String getVendor()
    {
        return "HTTP_API";
    }

    @Override
    public DialResult originate(AiCallTrunk trunk, DialRequest request)
    {
        String callUuid = UUID.randomUUID().toString();
        String callee = NumberTransformUtils.transform(request.getCalleeNumber(), trunk);
        String caller = NumberTransformUtils.resolveCaller(request.getCallerNumber(), trunk);

        JSONObject body = new JSONObject();
        body.put("callId", callUuid);
        body.put("trunkCode", trunk.getTrunkCode());
        body.put("caller", caller);
        body.put("callee", callee);
        body.put("timeout", request.getRingTimeout() == null ? 45 : request.getRingTimeout());
        body.put("record", request.isEnableRecord());
        body.put("answerAction", request.getAnswerAction());
        if (request.getAgentExtension() != null)
        {
            body.put("agentExtension", request.getAgentExtension());
        }
        if (request.getIvrFlowId() != null)
        {
            body.put("ivrFlowId", request.getIvrFlowId());
        }
        if (request.getRecordId() != null)
        {
            body.put("recordId", request.getRecordId());
        }

        try
        {
            String resp = post(trunk, originatePath, body.toJSONString());
            JSONObject json = parse(resp);
            if (json != null && isSuccess(json))
            {
                DialResult result = DialResult.ok(json.getString("callId") != null
                        ? json.getString("callId") : callUuid);
                result.setDialStatus(DialStatusEnum.DIALING.getCode());
                log.info("[HTTP_API] originate 成功 trunk={} callee={} uuid={}",
                        trunk.getTrunkCode(), callee, result.getCallUuid());
                return result;
            }
            String msg = json == null ? resp : json.getString("message");
            log.warn("[HTTP_API] originate 失败 trunk={} resp={}", trunk.getTrunkCode(), resp);
            DialResult fail = DialResult.fail("GATEWAY_REJECT", msg);
            fail.setDialStatus(DialStatusEnum.FAILED.getCode());
            if (json != null && json.containsKey("sipCode"))
            {
                fail.setSipCode(json.getInteger("sipCode"));
            }
            return fail;
        }
        catch (Exception e)
        {
            log.error("[HTTP_API] originate 异常 trunk={}", trunk.getTrunkCode(), e);
            DialResult fail = DialResult.fail("GATEWAY_ERROR", e.getMessage());
            fail.setDialStatus(DialStatusEnum.FAILED.getCode());
            return fail;
        }
    }

    @Override
    public boolean hangup(AiCallTrunk trunk, String callUuid)
    {
        JSONObject body = new JSONObject();
        body.put("callId", callUuid);
        try
        {
            JSONObject json = parse(post(trunk, hangupPath, body.toJSONString()));
            return json != null && isSuccess(json);
        }
        catch (Exception e)
        {
            log.error("[HTTP_API] hangup 异常 uuid={}", callUuid, e);
            return false;
        }
    }

    @Override
    public boolean bridgeToAgent(AiCallTrunk trunk, String callUuid, String extension)
    {
        JSONObject body = new JSONObject();
        body.put("callId", callUuid);
        body.put("target", extension);
        try
        {
            JSONObject json = parse(post(trunk, transferPath, body.toJSONString()));
            return json != null && isSuccess(json);
        }
        catch (Exception e)
        {
            log.error("[HTTP_API] transfer 异常 uuid={}", callUuid, e);
            return false;
        }
    }

    @Override
    public GatewayHealth checkHealth(AiCallTrunk trunk)
    {
        long start = System.currentTimeMillis();
        try
        {
            String resp = get(trunk, statusPath + "?trunkCode=" + trunk.getTrunkCode());
            long cost = System.currentTimeMillis() - start;
            JSONObject json = parse(resp);
            if (json != null && isSuccess(json))
            {
                GatewayHealth h = GatewayHealth.up(cost);
                String state = json.getString("state");
                if (state != null)
                {
                    h.setRegisterState(state);
                }
                return h;
            }
            return GatewayHealth.down("状态接口返回异常: " + resp);
        }
        catch (Exception e)
        {
            return GatewayHealth.down(e.getMessage());
        }
    }

    private boolean isSuccess(JSONObject json)
    {
        // 兼容 code=0 / code=200 / success=true 三种常见约定
        if (Boolean.TRUE.equals(json.getBoolean("success")))
        {
            return true;
        }
        Integer code = json.getInteger("code");
        return code != null && (code == 0 || code == 200);
    }

    private JSONObject parse(String resp)
    {
        if (resp == null || resp.trim().isEmpty())
        {
            return null;
        }
        try
        {
            return JSON.parseObject(resp);
        }
        catch (Exception e)
        {
            log.warn("[HTTP_API] 响应非法 JSON: {}", resp);
            return null;
        }
    }

    private String post(AiCallTrunk trunk, String path, String body) throws Exception
    {
        return request(trunk, path, "POST", body);
    }

    private String get(AiCallTrunk trunk, String path) throws Exception
    {
        return request(trunk, path, "GET", null);
    }

    private String request(AiCallTrunk trunk, String path, String method, String body) throws Exception
    {
        String base = trunk.getApiBaseUrl();
        if (base == null || base.trim().isEmpty())
        {
            base = "http://" + trunk.getGatewayHost() + ":" + trunk.getGatewayPort();
        }
        if (base.endsWith("/"))
        {
            base = base.substring(0, base.length() - 1);
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(base + path).openConnection();
        try
        {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            applyAuth(conn, trunk);

            if (body != null)
            {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream())
                {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            }

            int status = conn.getResponseCode();
            InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
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
        finally
        {
            conn.disconnect();
        }
    }

    private void applyAuth(HttpURLConnection conn, AiCallTrunk trunk)
    {
        String user = trunk.getAuthUser();
        String pwd = trunk.getAuthPassword();
        if (pwd == null || pwd.isEmpty())
        {
            return;
        }
        if (user == null || user.trim().isEmpty())
        {
            conn.setRequestProperty("Authorization", "Bearer " + pwd);
        }
        else
        {
            String token = Base64.getEncoder()
                    .encodeToString((user + ":" + pwd).getBytes(StandardCharsets.UTF_8));
            conn.setRequestProperty("Authorization", "Basic " + token);
        }
    }
}
