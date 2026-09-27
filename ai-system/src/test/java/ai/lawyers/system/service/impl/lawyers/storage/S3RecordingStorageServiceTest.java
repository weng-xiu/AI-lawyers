package ai.lawyers.system.service.impl.lawyers.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * F2：{@link S3RecordingStorageService} 单元测试。
 *
 * <p>用 OkHttpClient Interceptor mock HTTP 响应，验证 key 构建、
 * 预签名 URL 生成、各操作异常路径。</p>
 *
 * @author ai-lawyers
 */
class S3RecordingStorageServiceTest
{
    private S3RecordingStorageService service;

    /** 拦截器捕获请求 */
    private final AtomicReference<Request> capturedRequest = new AtomicReference<>();

    /** 拦截器返回的响应码 */
    private volatile int mockResponseCode = 200;

    /** 拦截器返回的响应体 */
    private volatile String mockResponseBody = "";

    @BeforeEach
    void setUp()
    {
        service = new S3RecordingStorageService();
        ReflectionTestUtils.setField(service, "endpoint", "oss-cn-guangzhou.aliyuncs.com");
        ReflectionTestUtils.setField(service, "bucket", "test-bucket");
        ReflectionTestUtils.setField(service, "accessKey", "test-ak");
        ReflectionTestUtils.setField(service, "secretKey", "test-sk");
        ReflectionTestUtils.setField(service, "region", "cn-guangzhou");
        ReflectionTestUtils.setField(service, "pathStyle", false);
        ReflectionTestUtils.setField(service, "urlExpirySeconds", 3600);
        ReflectionTestUtils.setField(service, "keyPrefix", "recordings/");

        // 拦截器 mock HTTP 响应
        OkHttpClient mockClient = new OkHttpClient.Builder()
                .addInterceptor((Interceptor.Chain chain) -> {
                    Request req = chain.request();
                    capturedRequest.set(req);
                    return new Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(mockResponseCode)
                            .message("OK")
                            .body(ResponseBody.create(null, mockResponseBody))
                            .build();
                })
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
        service.setHttpClient(mockClient);
    }

    // ---------- key 构建 ----------

    @Test
    void save_plainFileName_buildsKeyWithPrefix() throws Exception
    {
        mockResponseCode = 200;
        service.save("test.wav", "audio/wav".getBytes().length > 0
                ? new java.io.ByteArrayInputStream("audio/wav".getBytes())
                : null, 9, "audio/wav");

        Request req = capturedRequest.get();
        assertThat(req.url().toString())
                .isEqualTo("https://test-bucket.oss-cn-guangzhou.aliyuncs.com/recordings/test.wav");
        assertThat(req.method()).isEqualTo("PUT");
        assertThat(req.header("Authorization")).isNotBlank();
        assertThat(req.header("x-amz-content-sha256")).isEqualTo("UNSIGNED-PAYLOAD");
    }

    @Test
    void save_windowsAbsolutePath_extractsFileName() throws Exception
    {
        mockResponseCode = 200;
        service.save("C:\\Program Files\\FreeSWITCH\\recordings\\2026\\09\\test.wav",
                new java.io.ByteArrayInputStream("x".getBytes()), 1, null);

        Request req = capturedRequest.get();
        assertThat(req.url().toString())
                .isEqualTo("https://test-bucket.oss-cn-guangzhou.aliyuncs.com/recordings/test.wav");
    }

    @Test
    void save_recordingsDirVariable_stripped() throws Exception
    {
        mockResponseCode = 200;
        service.save("$${recordings_dir}/2026/09/test.wav",
                new java.io.ByteArrayInputStream("x".getBytes()), 1, null);

        Request req = capturedRequest.get();
        assertThat(req.url().toString())
                .isEqualTo("https://test-bucket.oss-cn-guangzhou.aliyuncs.com/recordings/2026/09/test.wav");
    }

    @Test
    void save_pathStyleMinIO_usesPathStyleUrl() throws Exception
    {
        ReflectionTestUtils.setField(service, "pathStyle", true);
        ReflectionTestUtils.setField(service, "endpoint", "minio.gov.cn:9000");
        mockResponseCode = 200;
        service.save("test.wav", new java.io.ByteArrayInputStream("x".getBytes()), 1, null);

        Request req = capturedRequest.get();
        assertThat(req.url().toString())
                .isEqualTo("https://minio.gov.cn:9000/test-bucket/recordings/test.wav");
    }

    @Test
    void save_missingConfig_throwsIllegalState()
    {
        ReflectionTestUtils.setField(service, "accessKey", "");
        assertThatThrownBy(() -> service.save("test.wav",
                new java.io.ByteArrayInputStream("x".getBytes()), 1, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("S3 配置不完整");
    }

    @Test
    void save_httpError_throwsIOException()
    {
        mockResponseCode = 403;
        mockResponseBody = "AccessDenied";
        assertThatThrownBy(() -> service.save("test.wav",
                new java.io.ByteArrayInputStream("x".getBytes()), 1, null))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("S3 PUT 失败 HTTP 403");
    }

    // ---------- load ----------

    @Test
    void load_404_returnsNull() throws Exception
    {
        mockResponseCode = 404;
        java.io.InputStream in = service.load("not-exist.wav");
        assertThat(in).isNull();
    }

    @Test
    void load_200_returnsStream() throws Exception
    {
        mockResponseCode = 200;
        mockResponseBody = "audio-bytes";
        java.io.InputStream in = service.load("test.wav");
        assertThat(in).isNotNull();
        byte[] buf = new byte[11];
        int n = in.read(buf);
        assertThat(n).isEqualTo(11);
        assertThat(new String(buf)).isEqualTo("audio-bytes");
        in.close();
    }

    // ---------- delete ----------

    @Test
    void delete_204_returnsTrue()
    {
        mockResponseCode = 204;
        assertThat(service.delete("test.wav")).isTrue();
    }

    @Test
    void delete_404_returnsTrue()
    {
        mockResponseCode = 404;
        assertThat(service.delete("not-exist.wav")).isTrue();
    }

    @Test
    void delete_500_returnsFalse()
    {
        mockResponseCode = 500;
        assertThat(service.delete("test.wav")).isFalse();
    }

    // ---------- exists ----------

    @Test
    void exists_200_returnsTrue()
    {
        mockResponseCode = 200;
        assertThat(service.exists("test.wav")).isTrue();
    }

    @Test
    void exists_404_returnsFalse()
    {
        mockResponseCode = 404;
        assertThat(service.exists("not-exist.wav")).isFalse();
    }

    // ---------- getPlayUrl ----------

    @Test
    void getPlayUrl_generatesPresignedUrlWithQueryParams()
    {
        String url = service.getPlayUrl("test.wav", 3600);
        assertThat(url).startsWith("https://test-bucket.oss-cn-guangzhou.aliyuncs.com/recordings/test.wav?");
        assertThat(url).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256");
        assertThat(url).contains("X-Amz-Credential=test-ak%2F");
        assertThat(url).contains("X-Amz-Expires=3600");
        assertThat(url).contains("X-Amz-Signature=");
        assertThat(url).contains("X-Amz-SignedHeaders=host");
    }

    @Test
    void getPlayUrl_pathStyle_usesPathStyleUrl()
    {
        ReflectionTestUtils.setField(service, "pathStyle", true);
        ReflectionTestUtils.setField(service, "endpoint", "minio.gov.cn:9000");
        String url = service.getPlayUrl("test.wav", 3600);
        assertThat(url).startsWith("https://minio.gov.cn:9000/test-bucket/recordings/test.wav?");
    }

    @Test
    void getPlayUrl_nullKey_returnsNull()
    {
        assertThat(service.getPlayUrl(null, 3600)).isNull();
    }

    // ---------- isObjectStorage ----------

    @Test
    void isObjectStorage_alwaysTrue()
    {
        assertThat(service.isObjectStorage()).isTrue();
    }
}
