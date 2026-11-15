package ai.lawyers.system.service.lawyers.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * RecordingCryptoService 单测：SM4-GCM 加密/解密往返、明文档兼容直通、
 * 空流处理与密文魔数识别。
 *
 * @author ai-lawyers
 */
class RecordingCryptoServiceTest
{
    private RecordingCryptoService service;

    @BeforeEach
    void setUp()
    {
        service = new RecordingCryptoService();
    }

    @Test
    void encryptThenDecryptRoundTrip() throws Exception
    {
        byte[] plain = "录音明文内容".getBytes(StandardCharsets.UTF_8);

        byte[] cipher = service.encrypt(new ByteArrayInputStream(plain));
        byte[] restored = service.decrypt(new ByteArrayInputStream(cipher));

        assertThat(cipher).isNotEqualTo(plain);
        assertThat(service.isEncrypted(cipher)).isTrue();
        assertThat(restored).isEqualTo(plain);
    }

    @Test
    void plainTextDecryptReturnsAsIs() throws Exception
    {
        byte[] plain = "legacy-plain-recording".getBytes(StandardCharsets.UTF_8);

        byte[] result = service.decrypt(new ByteArrayInputStream(plain));

        assertThat(result).isEqualTo(plain);
        assertThat(service.isEncrypted(plain)).isFalse();
    }

    @Test
    void shortBytesTreatedAsPlainText() throws Exception
    {
        byte[] shortBytes = { 'A', 'I' };

        assertThat(service.decrypt(new ByteArrayInputStream(shortBytes))).isEqualTo(shortBytes);
        assertThat(service.isEncrypted(shortBytes)).isFalse();
    }

    @Test
    void emptyAndNullStreamsProduceEmptyBytes() throws Exception
    {
        assertThat(service.encrypt(new ByteArrayInputStream(new byte[0]))).isEmpty();
        assertThat(service.decrypt(new ByteArrayInputStream(new byte[0]))).isEmpty();
        assertThat(service.encrypt(null)).isEmpty();
        assertThat(service.decrypt(null)).isEmpty();
        assertThat(service.isEncrypted(null)).isFalse();
        assertThat(service.isEncrypted(new byte[0])).isFalse();
    }

    @Test
    void magicHeaderDetected() 
    {
        assertThat(service.isEncrypted("AIRC".getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(service.isEncrypted("AIRC-rest".getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(service.isEncrypted("AIRX".getBytes(StandardCharsets.US_ASCII))).isFalse();
    }
}
