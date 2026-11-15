package ai.lawyers.system.service.impl.lawyers.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.service.lawyers.storage.RecordingCryptoService;

/**
 * LocalRecordingStorageService 单测：本地文件保存/读取/删除/存在性，
 * 以及加密开关下的原地加密、流式加密保存与读取解密。
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class LocalRecordingStorageServiceTest
{
    @TempDir
    Path tempDir;

    @Mock
    private RecordingCryptoService cryptoService;

    private LocalRecordingStorageService service;

    @BeforeEach
    void setUp()
    {
        service = new LocalRecordingStorageService();
        ReflectionTestUtils.setField(service, "recordingBasePath", tempDir.toString());
    }

    private void enableEncryption()
    {
        ReflectionTestUtils.setField(service, "encryptEnabled", true);
        ReflectionTestUtils.setField(service, "recordingCryptoService", cryptoService);
    }

    @Test
    void saveFileWithoutEncryptionIsNoop() throws Exception
    {
        File file = tempDir.resolve("a.wav").toFile();
        Files.write(file.toPath(), "PLAIN".getBytes(StandardCharsets.UTF_8));

        service.save("a.wav", file);

        assertThat(Files.readAllBytes(file.toPath())).isEqualTo("PLAIN".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void saveFileEncryptsInPlace() throws Exception
    {
        enableEncryption();
        File file = tempDir.resolve("a.wav").toFile();
        Files.write(file.toPath(), "PLAIN".getBytes(StandardCharsets.UTF_8));
        when(cryptoService.isEncrypted(any())).thenReturn(false);
        when(cryptoService.encrypt(any(InputStream.class))).thenReturn("CIPHER".getBytes(StandardCharsets.UTF_8));

        service.save("a.wav", file);

        assertThat(Files.readAllBytes(file.toPath())).isEqualTo("CIPHER".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void saveFileAlreadyEncryptedSkipped() throws Exception
    {
        enableEncryption();
        File file = tempDir.resolve("a.wav").toFile();
        Files.write(file.toPath(), "CIPHER".getBytes(StandardCharsets.UTF_8));
        when(cryptoService.isEncrypted(any())).thenReturn(true);

        service.save("a.wav", file);

        verify(cryptoService, never()).encrypt(any(InputStream.class));
        assertThat(Files.readAllBytes(file.toPath())).isEqualTo("CIPHER".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void saveMissingFileIsNoop() throws Exception
    {
        enableEncryption();
        File file = tempDir.resolve("missing.wav").toFile();

        service.save("missing.wav", file);

        verify(cryptoService, never()).isEncrypted(any());
        assertThat(file).doesNotExist();
    }

    @Test
    void saveStreamWithoutEncryptionWritesNothing() throws Exception
    {
        service.save("sub/a.wav", new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), 1L, "audio/wav");

        assertThat(tempDir.resolve("sub/a.wav")).doesNotExist();
    }

    @Test
    void saveStreamEncryptsAndCreatesDirectories() throws Exception
    {
        enableEncryption();
        when(cryptoService.encrypt(any(InputStream.class))).thenReturn("CIPHER".getBytes(StandardCharsets.UTF_8));

        service.save("sub/a.wav", new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), 1L, "audio/wav");

        Path target = tempDir.resolve("sub/a.wav");
        assertThat(Files.readAllBytes(target)).isEqualTo("CIPHER".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void saveStreamWithEmptyKeyIgnored() throws Exception
    {
        enableEncryption();

        service.save("", new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), 1L, "audio/wav");

        verify(cryptoService, never()).encrypt(any(InputStream.class));
    }

    @Test
    void loadMissingFileReturnsNull() throws Exception
    {
        assertThat(service.load("missing.wav")).isNull();
        assertThat(service.load("")).isNull();
    }

    @Test
    void loadPlainFileReturnsBytes() throws Exception
    {
        Files.write(tempDir.resolve("a.wav"), "PLAIN".getBytes(StandardCharsets.UTF_8));

        InputStream in = service.load("a.wav");

        assertThat(in).isNotNull();
        assertThat(read(in)).isEqualTo("PLAIN");
    }

    @Test
    void loadEncryptedFileDecrypts() throws Exception
    {
        enableEncryption();
        Files.write(tempDir.resolve("a.wav"), "CIPHER".getBytes(StandardCharsets.UTF_8));
        when(cryptoService.decrypt(any(InputStream.class))).thenReturn("PLAIN".getBytes(StandardCharsets.UTF_8));

        InputStream in = service.load("a.wav");

        assertThat(read(in)).isEqualTo("PLAIN");
    }

    @Test
    void deleteExistingAndMissingFile() throws Exception
    {
        File file = tempDir.resolve("a.wav").toFile();
        Files.write(file.toPath(), new byte[] { 1 });

        assertThat(service.delete("a.wav")).isTrue();
        assertThat(file).doesNotExist();
        assertThat(service.delete("a.wav")).isTrue();
        assertThat(service.delete("")).isFalse();
    }

    @Test
    void existsChecksFile() throws Exception
    {
        Files.write(tempDir.resolve("a.wav"), new byte[] { 1 });

        assertThat(service.exists("a.wav")).isTrue();
        assertThat(service.exists("missing.wav")).isFalse();
        assertThat(service.exists("")).isFalse();
    }

    @Test
    void absolutePathUsedDirectly() throws Exception
    {
        Path absolute = tempDir.resolve("abs.wav");
        Files.write(absolute, new byte[] { 1 });

        assertThat(service.exists(absolute.toString())).isTrue();
    }

    @Test
    void localModeHasNoPlayUrlOrObjectStorage()
    {
        assertThat(service.getPlayUrl("a.wav", 60)).isNull();
        assertThat(service.isObjectStorage()).isFalse();
    }

    private String read(InputStream in) throws Exception
    {
        try (InputStream source = in)
        {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = source.read(buf)) != -1)
            {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
