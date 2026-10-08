package ai.lawyers.system.service.impl.lawyers.ivr.engine;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.common.utils.StringUtils;

/**
 * P2-14：IVR service 节点的 SSRF 安全 HTTP 客户端。
 *
 * <p>从 {@code IvrEngineServiceImpl} 抽出的叶子工具，无状态；请求前校验协议/主机，
 * 拦截云元数据与内网/回环/保留地址，禁止自动跟随重定向。</p>
 *
 * @author ai-lawyers
 */
@Component
public class IvrHttpClient
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 校验目标 URL 是否允许访问；允许返回 null，否则返回错误原因。
     */
    public String checkUrlAllowed(String url)
    {
        try
        {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)))
            {
                return "仅允许 http/https 协议";
            }
            String host = uri.getHost();
            if (StringUtils.isEmpty(host))
            {
                return "URL缺少主机名";
            }
            // 云元数据地址显式拦截
            String lowerHost = host.toLowerCase();
            if (lowerHost.startsWith("169.254.") || lowerHost.contains("metadata.google.internal"))
            {
                return "禁止访问云元数据地址";
            }
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                    || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                    || address.isMulticastAddress())
            {
                return "禁止访问内网/回环/保留地址：" + host;
            }
            return null;
        }
        catch (Exception e)
        {
            return "URL解析失败：" + e.getMessage();
        }
    }

    /**
     * 简易 JSON 路径取值：支持 a.b.c 与数组下标 a.b[0]。
     */
    public Object jsonPath(String body, String path)
    {
        if (StringUtils.isEmpty(path))
        {
            return body;
        }
        try
        {
            JsonNode node = MAPPER.readTree(body);
            String[] segments = path.trim().split("\\.");
            for (String segment : segments)
            {
                if (StringUtils.isEmpty(segment))
                {
                    continue;
                }
                int arrayIndex = -1;
                int bracket = segment.indexOf('[');
                if (bracket >= 0 && segment.endsWith("]"))
                {
                    try
                    {
                        arrayIndex = Integer.parseInt(
                                segment.substring(bracket + 1, segment.length() - 1).trim());
                    }
                    catch (Exception ignored)
                    {
                    }
                    segment = segment.substring(0, bracket);
                }
                node = node.path(segment);
                if (arrayIndex >= 0)
                {
                    node = node.path(arrayIndex);
                }
            }
            if (node.isMissingNode() || node.isNull())
            {
                return null;
            }
            return node.isValueNode() ? node.asText() : node.toString();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * 发起 HTTP 调用并返回响应正文（2xx）。
     */
    public String httpCall(String url, String method, Map<String, String> headers, String body,
                            int timeoutMs) throws Exception
    {
        HttpURLConnection connection = null;
        try
        {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setDoOutput(body != null);
            // 安全收口(S5)：禁止自动跟随重定向，防止 302 跳转绕过 SSRF 校验指向内网
            connection.setInstanceFollowRedirects(false);
            for (Map.Entry<String, String> entry : headers.entrySet())
            {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            if (body != null)
            {
                try (OutputStream out = connection.getOutputStream())
                {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = readText(stream);
            if (status < 200 || status >= 300)
            {
                throw new IllegalStateException("HTTP " + status + "：" + truncate(response, 200));
            }
            return response;
        }
        finally
        {
            if (connection != null)
            {
                connection.disconnect();
            }
        }
    }

    private String readText(InputStream in) throws Exception
    {
        if (in == null)
        {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private String truncate(String s, int max)
    {
        if (s == null)
        {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
