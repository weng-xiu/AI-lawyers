package ai.lawyers.web.health;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P3-⑪：Mock 兜底清单推导单测——全 Mock 命中、真实配置通过、API Key 缺失降级。
 */
public class MockFallbackReporterTest
{
    private static final Map<String, String> PROPS = new HashMap<>();

    static
    {
        PROPS.put("voice.engine", "dashscope");
        PROPS.put("voice.dashscope-api-key", "sk-test");
        PROPS.put("voice.asr-base-url", "http://127.0.0.1:8000/v1");
    }

    @Test
    public void allMockImplementationsAreListed()
    {
        Map<String, String> props = new HashMap<>();
        props.put("voice.engine", "mock");
        List<String> mocks = MockFallbackReporter.detect(props::get,
                new HashSet<>(Collections.singletonList("SIMULATOR")),
                Collections.singletonList("mock"));

        assertEquals(4, mocks.size());
        assertTrue(mocks.get(0).contains("短信"));
        assertTrue(mocks.get(1).contains("SIMULATOR"));
        assertTrue(mocks.get(2).contains("TTS"));
        assertTrue(mocks.get(3).contains("ASR"));
    }

    @Test
    public void realImplementationsProduceNoMockList()
    {
        List<String> mocks = MockFallbackReporter.detect(PROPS::get,
                new HashSet<>(Collections.singletonList("FREESWITCH")),
                Arrays.asList("aliyun", "tencent"));

        assertTrue(mocks.isEmpty());
    }

    @Test
    public void dashscopeWithoutApiKeyFallsBackToMock()
    {
        Map<String, String> props = new HashMap<>(PROPS);
        props.put("voice.dashscope-api-key", "  ");

        List<String> mocks = MockFallbackReporter.detect(props::get,
                new HashSet<>(Collections.singletonList("FREESWITCH")),
                Collections.singletonList("aliyun"));

        assertEquals(1, mocks.size());
        assertTrue(mocks.get(0).contains("API Key"));
    }

    @Test
    public void emptyRegistriesAreReported()
    {
        List<String> mocks = MockFallbackReporter.detect(PROPS::get,
                Collections.emptySet(), Collections.emptyList());

        assertEquals(2, mocks.size());
        assertTrue(mocks.get(0).contains("未注册任何供应商"));
        assertTrue(mocks.get(1).contains("未注册任何适配器"));
    }
}
