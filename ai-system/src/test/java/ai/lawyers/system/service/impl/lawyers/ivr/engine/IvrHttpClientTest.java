package ai.lawyers.system.service.impl.lawyers.ivr.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * IvrHttpClient 单测：SSRF 目标校验（协议/主机/元数据/内网/回环/组播）与 JSON 路径取值。
 *
 * @author ai-lawyers
 */
class IvrHttpClientTest
{
    private IvrHttpClient client;

    @BeforeEach
    void setUp()
    {
        client = new IvrHttpClient();
    }

    @Test
    void allowsPublicHttpIp()
    {
        assertThat(client.checkUrlAllowed("https://8.8.8.8/path")).isNull();
    }

    @Test
    void allowsUpperCaseScheme()
    {
        assertThat(client.checkUrlAllowed("HTTP://8.8.8.8/x")).isNull();
    }

    @Test
    void rejectsNonHttpScheme()
    {
        assertThat(client.checkUrlAllowed("ftp://8.8.8.8/file")).contains("仅允许 http/https 协议");
        assertThat(client.checkUrlAllowed("file:///etc/passwd")).contains("仅允许 http/https 协议");
    }

    @Test
    void rejectsMissingHost()
    {
        assertThat(client.checkUrlAllowed("http:/path")).contains("URL缺少主机名");
    }

    @Test
    void rejectsInvalidUrl()
    {
        assertThat(client.checkUrlAllowed("not a url")).contains("URL解析失败");
    }

    @Test
    void rejectsCloudMetadataAddress()
    {
        assertThat(client.checkUrlAllowed("http://169.254.169.254/latest/meta-data"))
                .contains("禁止访问云元数据地址");
        assertThat(client.checkUrlAllowed("http://metadata.google.internal/computeMetadata"))
                .contains("禁止访问云元数据地址");
    }

    @Test
    void rejectsLoopback()
    {
        assertThat(client.checkUrlAllowed("http://127.0.0.1:8080/admin"))
                .contains("禁止访问内网/回环/保留地址");
        assertThat(client.checkUrlAllowed("http://localhost:8080/"))
                .contains("禁止访问内网/回环/保留地址");
    }

    @Test
    void rejectsSiteLocal()
    {
        assertThat(client.checkUrlAllowed("http://10.1.2.3/api"))
                .contains("禁止访问内网/回环/保留地址");
        assertThat(client.checkUrlAllowed("http://192.168.1.1/api"))
                .contains("禁止访问内网/回环/保留地址");
        assertThat(client.checkUrlAllowed("http://172.16.0.1/api"))
                .contains("禁止访问内网/回环/保留地址");
    }

    @Test
    void rejectsAnyLocalAndMulticast()
    {
        assertThat(client.checkUrlAllowed("http://0.0.0.0/"))
                .contains("禁止访问内网/回环/保留地址");
        assertThat(client.checkUrlAllowed("http://224.0.0.1/"))
                .contains("禁止访问内网/回环/保留地址");
    }

    @Test
    void emptyPathReturnsWholeBody()
    {
        String body = "{\"a\":1}";

        assertThat(client.jsonPath(body, "")).isEqualTo(body);
    }

    @Test
    void readsNestedValue()
    {
        String body = "{\"data\":{\"id\":42,\"name\":\"张三\"}}";

        assertThat(client.jsonPath(body, "data.id")).isEqualTo("42");
        assertThat(client.jsonPath(body, "data.name")).isEqualTo("张三");
    }

    @Test
    void readsArrayIndexAndNestedField()
    {
        String body = "{\"list\":[{\"x\":\"y\"},{\"x\":\"z\"}]}";

        assertThat(client.jsonPath(body, "list[1].x")).isEqualTo("z");
        assertThat(client.jsonPath(body, "list[0]")).isEqualTo("{\"x\":\"y\"}");
    }

    @Test
    void missingPathReturnsNull()
    {
        assertThat(client.jsonPath("{\"a\":1}", "b.c")).isNull();
        assertThat(client.jsonPath("{\"a\":null}", "a")).isNull();
        assertThat(client.jsonPath("not-json", "a")).isNull();
    }
}
