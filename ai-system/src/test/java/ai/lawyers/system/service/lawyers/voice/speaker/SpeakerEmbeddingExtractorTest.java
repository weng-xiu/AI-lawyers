package ai.lawyers.system.service.lawyers.voice.speaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

/**
 * P2-15：声纹嵌入提取器降级行为单测。
 *
 * <p>测试环境既不放置 ONNX Runtime 也不放置模型，校验提取器进入降级态、
 * 不伪造向量（extract 返回 null），由调用方跳过聚类。</p>
 *
 * @author ai-lawyers
 */
class SpeakerEmbeddingExtractorTest
{
    @Test
    void noModelOrRuntime_degradesAndSkips()
    {
        SpeakerEmbeddingExtractor extractor =
                new SpeakerEmbeddingExtractor(16000, "models/__no_such_speaker_model__.onnx");

        assertFalse(extractor.isModelAvailable());
        // 降级态不得伪造身份向量
        assertNull(extractor.extract(new byte[] { 1, 0, 2, 0 }));
    }

    @Test
    void extract_emptyOrTinyPcm_returnsNull()
    {
        SpeakerEmbeddingExtractor extractor = new SpeakerEmbeddingExtractor(16000);
        assertNull(extractor.extract(null));
        assertNull(extractor.extract(new byte[0]));
        assertNull(extractor.extract(new byte[] { 1 }));
    }

    @Test
    void metadata_exposed()
    {
        SpeakerEmbeddingExtractor extractor = new SpeakerEmbeddingExtractor(8000);
        assertEquals(8000, extractor.getSampleRate());
        assertEquals(SpeakerEmbeddingExtractor.DEFAULT_MODEL_PATH, extractor.getModelPath());
    }
}
