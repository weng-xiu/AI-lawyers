package ai.lawyers.system.service.impl.lawyers.storage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.service.lawyers.storage.RecordingStorageService;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * F2：录音 S3 兼容对象存储实现（阿里云 OSS / MinIO / 华为 OBS / 腾讯 COS）。
 *
 * <p>沿用 P3-A2/A3 既定决策——不引 AWS/阿里云 SDK（避免依赖污染），
 * 基于项目现有 okhttp 3.14.9 自实现 AWS Signature Version 4 签名。</p>
 *
 * <p>播放/下载通过预签名 GET URL（支持 Range，前端 {@code <audio>} 可直接使用）。</p>
 *
 * @author ai-lawyers
 */
@Service
@ConditionalOnProperty(name = "call.recording.type", havingValue = "s3")
public class S3RecordingStorageService implements RecordingStorageService
{
    private static final Logger log = LoggerFactory.getLogger(S3RecordingStorageService.class);

    /** S3 端点（如 oss-cn-guangzhou.aliyuncs.com / minio.gov.cn:9000） */
    @Value("${call.recording.s3.endpoint:}")
    private String endpoint;

    /** 存储桶 */
    @Value("${call.recording.s3.bucket:}")
    private String bucket;

    /** AccessKey */
    @Value("${call.recording.s3.access-key:}")
    private String accessKey;

    /** SecretKey */
    @Value("${call.recording.s3.secret-key:}")
    private String secretKey;

    /** 区域（如 cn-guangzhou / us-east-1） */
    @Value("${call.recording.s3.region:cn-guangzhou}")
    private String region;

    /** 是否路径风格（MinIO 为 true，OSS/OBS/COS 为 false） */
    @Value("${call.recording.s3.path-style:false}")
    private boolean pathStyle;

    /** 预签名 URL 默认有效时长（秒） */
    @Value("${call.recording.s3.url-expiry-seconds:3600}")
    private int urlExpirySeconds;

    /** 对象 key 前缀（如 recordings/） */
    @Value("${call.recording.s3.key-prefix:recordings/}")
    private String keyPrefix;

    /** HTTP 客户端（项目无全局 OkHttpClient Bean，自维护单例） */
    private volatile OkHttpClient client;

    /** 包级测试点：注入自定义 HTTP 客户端 */
    void setHttpClient(OkHttpClient httpClient)
    {
        this.client = httpClient;
    }

    @Override
    public void save(String key, File file) throws IOException
    {
        String contentType = guessContentType(file.getName());
        try (InputStream in = new java.io.FileInputStream(file))
        {
            save(key, in, file.length(), contentType);
        }
    }

    @Override
    public void save(String key, InputStream inputStream, long contentLength, String contentType)
            throws IOException
    {
        if (StringUtils.isEmpty(key))
        {
            throw new IllegalArgumentException("object key 不能为空");
        }
        if (StringUtils.isEmpty(endpoint) || StringUtils.isEmpty(bucket)
                || StringUtils.isEmpty(accessKey) || StringUtils.isEmpty(secretKey))
        {
            throw new IllegalStateException("S3 配置不完整（endpoint/bucket/accessKey/secretKey）");
        }
        String objectKey = buildKey(key);
        String url = buildObjectUrl(objectKey);
        String now = iso8601();
        String date = now.substring(0, 8);

        // 先读取字节，确保 Content-Length 与实际一致
        byte[] bodyBytes = readAllBytes(inputStream);
        String mimeType = contentType != null ? contentType : "application/octet-stream";

        Map<String, String> headers = new HashMap<>();
        headers.put("x-amz-date", now);
        headers.put("x-amz-content-sha256", "UNSIGNED-PAYLOAD");
        headers.put("Content-Type", mimeType);

        String authorization = buildAuthorization("PUT", objectKey, headers, now, date);

        RequestBody requestBody = RequestBody.create(MediaType.parse(mimeType), bodyBytes);
        Request.Builder builder = new Request.Builder()
                .url(url)
                .put(requestBody)
                .header("Authorization", authorization)
                .header("x-amz-date", now)
                .header("x-amz-content-sha256", "UNSIGNED-PAYLOAD")
                .header("Content-Type", mimeType)
                .header("Content-Length", String.valueOf(bodyBytes.length));

        try (Response response = httpClient().newCall(builder.build()).execute())
        {
            if (!response.isSuccessful())
            {
                String body = response.body() != null ? response.body().string() : "";
                throw new IOException("S3 PUT 失败 HTTP " + response.code() + ": " + body);
            }
            log.info("[RecordingStorage] S3 上传成功 key={} size={}", objectKey, contentLength);
        }
    }

    @Override
    public InputStream load(String key) throws IOException
    {
        if (StringUtils.isEmpty(key))
        {
            return null;
        }
        String objectKey = buildKey(key);
        String url = buildObjectUrl(objectKey);
        String now = iso8601();
        String date = now.substring(0, 8);

        Map<String, String> headers = new HashMap<>();
        headers.put("x-amz-date", now);
        headers.put("x-amz-content-sha256", "UNSIGNED-PAYLOAD");

        String authorization = buildAuthorization("GET", objectKey, headers, now, date);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", authorization)
                .header("x-amz-date", now)
                .header("x-amz-content-sha256", "UNSIGNED-PAYLOAD")
                .build();

        Response response = httpClient().newCall(request).execute();
        if (response.code() == 404)
        {
            response.close();
            return null;
        }
        if (!response.isSuccessful())
        {
            String body = response.body() != null ? response.body().string() : "";
            response.close();
            throw new IOException("S3 GET 失败 HTTP " + response.code() + ": " + body);
        }
        ResponseBody responseBody = response.body();
        if (responseBody == null)
        {
            response.close();
            return null;
        }
        // 返回包装流，关闭时同时关闭 Response
        return new ResponseClosingInputStream(responseBody.byteStream(), response);
    }

    @Override
    public boolean delete(String key)
    {
        if (StringUtils.isEmpty(key))
        {
            return false;
        }
        try
        {
            String objectKey = buildKey(key);
            String url = buildObjectUrl(objectKey);
            String now = iso8601();
            String date = now.substring(0, 8);

            Map<String, String> headers = new HashMap<>();
            headers.put("x-amz-date", now);
            headers.put("x-amz-content-sha256", "UNSIGNED-PAYLOAD");

            String authorization = buildAuthorization("DELETE", objectKey, headers, now, date);

            Request request = new Request.Builder()
                    .url(url)
                    .delete()
                    .header("Authorization", authorization)
                    .header("x-amz-date", now)
                    .header("x-amz-content-sha256", "UNSIGNED-PAYLOAD")
                    .build();

            try (Response response = httpClient().newCall(request).execute())
            {
                // 204 No Content 或 404 Not Found 均视为成功（幂等）
                if (response.isSuccessful() || response.code() == 404)
                {
                    return true;
                }
                log.warn("[RecordingStorage] S3 DELETE 失败 HTTP {} key={}", response.code(), objectKey);
                return false;
            }
        }
        catch (Exception e)
        {
            log.warn("[RecordingStorage] S3 DELETE 异常 key={}: {}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean exists(String key)
    {
        if (StringUtils.isEmpty(key))
        {
            return false;
        }
        try
        {
            String objectKey = buildKey(key);
            String url = buildObjectUrl(objectKey);
            String now = iso8601();
            String date = now.substring(0, 8);

            Map<String, String> headers = new HashMap<>();
            headers.put("x-amz-date", now);
            headers.put("x-amz-content-sha256", "UNSIGNED-PAYLOAD");

            String authorization = buildAuthorization("HEAD", objectKey, headers, now, date);

            Request request = new Request.Builder()
                    .url(url)
                    .head()
                    .header("Authorization", authorization)
                    .header("x-amz-date", now)
                    .header("x-amz-content-sha256", "UNSIGNED-PAYLOAD")
                    .build();

            try (Response response = httpClient().newCall(request).execute())
            {
                return response.isSuccessful();
            }
        }
        catch (Exception e)
        {
            log.warn("[RecordingStorage] S3 HEAD 异常 key={}: {}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public String getPlayUrl(String key, int expirySeconds)
    {
        if (StringUtils.isEmpty(key))
        {
            return null;
        }
        int expiry = expirySeconds > 0 ? expirySeconds : urlExpirySeconds;
        String objectKey = buildKey(key);
        String now = iso8601();
        String date = now.substring(0, 8);
        String credential = accessKey + "/" + date + "/" + region + "/s3/aws4_request";

        Map<String, String> query = new HashMap<>();
        query.put("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
        query.put("X-Amz-Credential", credential);
        query.put("X-Amz-Date", now);
        query.put("X-Amz-Expires", String.valueOf(expiry));
        query.put("X-Amz-SignedHeaders", "host");

        String canonicalQuery = buildCanonicalQuery(query);
        String host = buildHost();
        String canonicalRequest = "GET\n"
                + "/" + objectKey + "\n"
                + canonicalQuery + "\n"
                + "host:" + host + "\n"
                + "\n"
                + "host\n"
                + "UNSIGNED-PAYLOAD";

        String stringToSign = "AWS4-HMAC-SHA256\n"
                + now + "\n"
                + date + "/" + region + "/s3/aws4_request\n"
                + sha256Hex(canonicalRequest);

        String signature = hmacSha256Hex(stringToSign, "AWS4" + secretKey, date, region);
        String baseUrl = buildObjectUrl(objectKey);
        return baseUrl + "?" + canonicalQuery + "&X-Amz-Signature=" + signature;
    }

    @Override
    public boolean isObjectStorage()
    {
        return true;
    }

    /* ================= 内部工具 ================= */

    private OkHttpClient httpClient()
    {
        if (client == null)
        {
            synchronized (this)
            {
                if (client == null)
                {
                    client = new OkHttpClient();
                }
            }
        }
        return client;
    }

    private String buildKey(String key)
    {
        // key 可能是 FreeSWITCH 传来的绝对路径，统一转为相对 object key
        String k = key.trim();
        // 去掉 $${recordings_dir} 前缀
        if (k.startsWith("$${recordings_dir}") || k.startsWith("${recordings_dir}"))
        {
            k = k.substring(k.indexOf('}') + 1);
        }
        // 去掉前导斜杠
        while (k.startsWith("/") || k.startsWith("\\"))
        {
            k = k.substring(1);
        }
        // Windows 绝对路径（如 C:\Program Files\FreeSWITCH\recordings\xxx.wav）取文件名部分
        if (k.matches("^[a-zA-Z]:.*"))
        {
            int idx = Math.max(k.lastIndexOf('/'), k.lastIndexOf('\\'));
            if (idx >= 0)
            {
                k = k.substring(idx + 1);
            }
        }
        // 如果已带 keyPrefix 则不重复拼接
        if (k.startsWith(keyPrefix))
        {
            return k;
        }
        return keyPrefix + k;
    }

    private String buildHost()
    {
        String ep = endpoint.trim();
        if (ep.startsWith("http://"))
        {
            ep = ep.substring(7);
        }
        else if (ep.startsWith("https://"))
        {
            ep = ep.substring(8);
        }
        if (pathStyle)
        {
            return ep;
        }
        return bucket + "." + ep;
    }

    private String buildObjectUrl(String objectKey)
    {
        String ep = endpoint.trim();
        if (!ep.startsWith("http://") && !ep.startsWith("https://"))
        {
            ep = "https://" + ep;
        }
        if (pathStyle)
        {
            return ep + "/" + bucket + "/" + objectKey;
        }
        // 虚拟主机风格：bucket.endpoint/objectKey
        String scheme = ep.substring(0, ep.indexOf("://") + 3);
        String host = ep.substring(ep.indexOf("://") + 3);
        return scheme + bucket + "." + host + "/" + objectKey;
    }

    private static String iso8601()
    {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    private String buildAuthorization(String method, String objectKey,
                                      Map<String, String> headers, String now, String date)
    {
        StringBuilder canonicalHeaders = new StringBuilder();
        StringBuilder signedHeaders = new StringBuilder();
        headers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    canonicalHeaders.append(e.getKey().toLowerCase()).append(':')
                            .append(e.getValue().trim()).append('\n');
                    signedHeaders.append(e.getKey().toLowerCase()).append(';');
                });
        // 去掉末尾分号
        if (signedHeaders.length() > 0)
        {
            signedHeaders.setLength(signedHeaders.length() - 1);
        }

        String canonicalRequest = method + "\n"
                + "/" + objectKey + "\n"
                + "\n"
                + canonicalHeaders
                + "\n"
                + signedHeaders + "\n"
                + "UNSIGNED-PAYLOAD";

        String stringToSign = "AWS4-HMAC-SHA256\n"
                + now + "\n"
                + date + "/" + region + "/s3/aws4_request\n"
                + sha256Hex(canonicalRequest);

        String signature = hmacSha256Hex(stringToSign, "AWS4" + secretKey, date, region);

        return "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + date + "/" + region
                + "/s3/aws4_request, SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;
    }

    private static String buildCanonicalQuery(Map<String, String> query)
    {
        return query.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
    }

    private static String urlEncode(String s)
    {
        try
        {
            return URLEncoder.encode(s, "UTF-8")
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        }
        catch (Exception e)
        {
            return s;
        }
    }

    private static String sha256Hex(String data)
    {
        try
        {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash)
            {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
        catch (Exception e)
        {
            throw new RuntimeException("SHA-256 计算失败", e);
        }
    }

    private static String hmacSha256Hex(String data, String key, String date, String region)
    {
        try
        {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(("AWS4" + key).getBytes("UTF-8"), "HmacSHA256"));
            byte[] kDate = mac.doFinal(date.getBytes("UTF-8"));
            mac.init(new SecretKeySpec(kDate, "HmacSHA256"));
            byte[] kRegion = mac.doFinal(region.getBytes("UTF-8"));
            mac.init(new SecretKeySpec(kRegion, "HmacSHA256"));
            byte[] kService = mac.doFinal("s3".getBytes("UTF-8"));
            mac.init(new SecretKeySpec(kService, "HmacSHA256"));
            byte[] kSigning = mac.doFinal("aws4_request".getBytes("UTF-8"));
            mac.init(new SecretKeySpec(kSigning, "HmacSHA256"));
            byte[] result = mac.doFinal(data.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : result)
            {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
        catch (Exception e)
        {
            throw new RuntimeException("HMAC-SHA256 计算失败", e);
        }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException
    {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1)
        {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static String guessContentType(String fileName)
    {
        String name = fileName == null ? "" : fileName.toLowerCase();
        if (name.endsWith(".wav")) return "audio/wav";
        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".ogg")) return "audio/ogg";
        if (name.endsWith(".webm")) return "audio/webm";
        return "application/octet-stream";
    }

    /** 包装 Response 的输入流，关闭时同时关闭 Response 释放连接 */
    private static final class ResponseClosingInputStream extends InputStream
    {
        private final InputStream delegate;
        private final Response response;

        ResponseClosingInputStream(InputStream delegate, Response response)
        {
            this.delegate = delegate;
            this.response = response;
        }

        @Override
        public int read() throws IOException
        {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException
        {
            return delegate.read(b, off, len);
        }

        @Override
        public void close() throws IOException
        {
            try
            {
                delegate.close();
            }
            finally
            {
                response.close();
            }
        }
    }
}
