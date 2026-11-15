package ai.lawyers.system.service.impl.lawyers.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.service.lawyers.storage.RecordingCryptoService;

/**
 * F2/G1-b3：{@link LocalRecordingStorageService} 本地录音存储测试。
 *
 * <p>覆盖明文模式直接读取、加密模式原地/流式加密保存、解密读取、文件存在判断、
 * 幂等删除与删除失败、路径解析（绝对路径、recordings_dir 前缀）、播放 URL 与存储类型。</p>
 *
 * @author ai-lawyers
 */
class LocalRecordingStorageServiceTest
{
    @TempDir
    Path tempDir;

    private LocalRecordingStorageService service;

    private RecordingCryptoService crypto;

    @BeforeEach
    void setUp()
    {
        service = new LocalRecordingStorageService();
        crypto = mock(RecordingCryptoService.class);
        ReflectionTestUtils.setField(service, "recordingBasePath", tempDir.toString());
        ReflectionTestUtils.setField(service, "encryptEnabled", false);
        ReflectionTestUtils.setField(service, "recordingCryptoService", crypto);
    }

    @Test
    void plainModeReadsAndIgnoresSave() throws IOException
    {
        Path file = Files.write(tempDir.resolve("a.wav"), "voice".getBytes(StandardCharsets.UTF_8));

        service.save("a.wav", file.toFile());
        service.save("a.wav", new ByteArrayInputStream(new byte[] { 1 }), 1L, null);

        assertThat(read(service.load("a.wav"))).isEqualTo("voice");
        verify(crypto, never()).encrypt(any());
    }

    @Test
    void loadReturnsNullForMissingOrBlankKey() throws IOException
    {
        assertThat(service.load("missing.wav")).isNull();
        assertThat(service.load("  ")).isNull();

        Path dir = Files.createDirectory(tempDir.resolve("dir"));
        assertThat(service.load("dir")).isNull();
    }

    @Test
    void streamSaveEncryptsToResolvedPath() throws IOException
    {
        ReflectionTestUtils.setField(service, "encryptEnabled", true);
        when(crypto.encrypt(any(InputStream.class))).thenReturn("cipher".getBytes(StandardCharsets.UTF_8));

        service.save("2026/01/b.wav", new ByteArrayInputStream("plain".getBytes(StandardCharsets.UTF_8)),
                5L, "audio/wav");

        Path saved = tempDir.resolve("2026/01/b.wav");
        assertThat(saved).exists();
        assertThat(new String(Files.readAllBytes(saved), StandardCharsets.UTF_8)).isEqualTo("cipher");
    }

    @Test
    void streamSaveSkipsWhenCryptoMissingOrKeyBlank() throws IOException
    {
        ReflectionTestUtils.setField(service, "encryptEnabled", true);

        service.save("  ", new ByteArrayInputStream(new byte[] { 1 }), 1L, null);
        ReflectionTestUtils.setField(service, "recordingCryptoService", null);
        service.save("c.wav", new ByteArrayInputStream(new byte[] { 1 }), 1L, null);

        assertThat(tempDir.resolve("c.wav")).doesNotExist();
    }

    @Test
    void saveEncryptsExistingFileInPlace() throws IOException
    {
        ReflectionTestUtils.setField(service, "encryptEnabled", true);
        Path file = Files.write(tempDir.resolve("d.wav"), "plain".getBytes(StandardCharsets.UTF_8));
        when(crypto.isEncrypted(any())).thenReturn(false);
        when(crypto.encrypt(any(InputStream.class))).thenReturn("ENC".getBytes(StandardCharsets.UTF_8));

        service.save("d.wav", file.toFile());

        assertThat(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).isEqualTo("ENC");
        assertThat(tempDir.resolve("d.wav.enc.tmp")).doesNotExist();
    }

    @Test
    void saveSkipsAlreadyEncryptedFile() throws IOException
    {
        ReflectionTestUtils.setField(service, "encryptEnabled", true);
        Path file = Files.write(tempDir.resolve("e.wav"), "cipher".getBytes(StandardCharsets.UTF_8));
        when(crypto.isEncrypted(any())).thenReturn(true);

        service.save("e.wav", file.toFile());
        service.save("e.wav", (File) null);
        service.save("f.wav", tempDir.resolve("absent.wav").toFile());

        verify(crypto, never()).encrypt(any());
    }

    @Test
    void encryptedLoadDecryptsStoredBytes() throws IOException
    {
        ReflectionTestUtils.setField(service, "encryptEnabled", true);
        Files.write(tempDir.resolve("g.wav"), new byte[] { 9, 9 });
        when(crypto.decrypt(any(InputStream.class))).thenReturn("plain".getBytes(StandardCharsets.UTF_8));

        assertThat(read(service.load("g.wav"))).isEqualTo("plain");
    }

    @Test
    void deleteIsIdempotentAndReportsFailure()
    {
        assertThat(service.delete("  ")).isFalse();
        assertThat(service.delete("never.wav")).isTrue();

        try
        {
            Path file = Files.write(tempDir.resolve("h.wav"), new byte[] { 1 });
            assertThat(service.delete("h.wav")).isTrue();
            assertThat(file).doesNotExist();

            Files.createDirectory(tempDir.resolve("nonempty"));
            Files.write(tempDir.resolve("nonempty/child"), new byte[] { 1 });
            assertThat(service.delete("nonempty")).isFalse();
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void existsOnlyForRegularFiles() throws IOException
    {
        Files.write(tempDir.resolve("i.wav"), new byte[] { 1 });
        Files.createDirectory(tempDir.resolve("folder"));

        assertThat(service.exists("i.wav")).isTrue();
        assertThat(service.exists("folder")).isFalse();
        assertThat(service.exists("absent.wav")).isFalse();
        assertThat(service.exists(null)).isFalse();
    }

    @Test
    void resolvesAbsoluteAndRecordingsDirPaths() throws IOException
    {
        Path absolute = Files.write(tempDir.resolve("abs.wav"), new byte[] { 1 });
        assertThat(service.exists(absolute.toString())).isTrue();

        Path prefixed = tempDir.resolve("sub/pre.wav");
        Files.createDirectories(prefixed.getParent());
        Files.write(prefixed, new byte[] { 1 });
        assertThat(service.exists("$${recordings_dir}/sub/pre.wav")).isTrue();
    }

    @Test
    void localModeHasNoPlayUrlAndIsNotObjectStorage()
    {
        assertThat(service.getPlayUrl("a.wav", 60)).isNull();
        assertThat(service.isObjectStorage()).isFalse();
    }

    private String read(InputStream in) throws IOException
    {
        if (in == null)
        {
            return null;
        }
        try (InputStream input = in)
        {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
