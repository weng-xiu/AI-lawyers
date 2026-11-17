package ai.lawyers.common.utils.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

import ai.lawyers.common.config.RuoYiConfig;
import ai.lawyers.common.constant.Constants;
import ai.lawyers.common.exception.file.FileNameLengthLimitExceededException;
import ai.lawyers.common.exception.file.FileSizeLimitExceededException;
import ai.lawyers.common.exception.file.InvalidExtensionException;

/**
 * 文件上传工具测试：大小与后缀校验、文件名生成、路径拼接与落盘。
 *
 * @author ai-lawyers
 */
class FileUploadUtilsTest
{
    @TempDir
    Path tempDir;

    private String oldBaseDir;

    private String oldProfile;

    @BeforeEach
    void setUp()
    {
        oldBaseDir = FileUploadUtils.getDefaultBaseDir();
        oldProfile = RuoYiConfig.getProfile();
        new RuoYiConfig().setProfile(tempDir.toString());
        FileUploadUtils.setDefaultBaseDir(tempDir.toString());
    }

    @AfterEach
    void tearDown()
    {
        FileUploadUtils.setDefaultBaseDir(oldBaseDir);
        new RuoYiConfig().setProfile(oldProfile);
    }

    @Test
    void 默认常量()
    {
        assertEquals(50 * 1024 * 1024L, FileUploadUtils.DEFAULT_MAX_SIZE);
        assertEquals(100, FileUploadUtils.DEFAULT_FILE_NAME_LENGTH);
    }

    @Test
    void setDefaultBaseDir_可读写()
    {
        FileUploadUtils.setDefaultBaseDir("upload/base");

        assertEquals("upload/base", FileUploadUtils.getDefaultBaseDir());
    }

    @Test
    void isAllowedExtension_忽略大小写()
    {
        String[] allowed = { "pdf", "docx" };

        assertTrue(FileUploadUtils.isAllowedExtension("PDF", allowed));
        assertTrue(FileUploadUtils.isAllowedExtension("docx", allowed));
        assertFalse(FileUploadUtils.isAllowedExtension("exe", allowed));
    }

    @Test
    void getExtension_优先文件名后缀()
    {
        assertEquals("PDF", FileUploadUtils.getExtension(file("合同.PDF", "application/pdf", 1)));
        // 无后缀时按内容类型推断
        assertEquals("png", FileUploadUtils.getExtension(file("合同", "image/png", 1)));
        // 无法识别的内容类型返回空串
        assertEquals("", FileUploadUtils.getExtension(file("合同", "application/octet-stream", 1)));
    }

    @Test
    void assertAllowed_大小超限抛出异常()
    {
        MultipartFile tooLarge = new StubMultipartFile("a.pdf", "application/pdf",
                new byte[] { 1 }, FileUploadUtils.DEFAULT_MAX_SIZE + 1);

        assertThrows(FileSizeLimitExceededException.class,
                () -> FileUploadUtils.assertAllowed(tooLarge, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION));
    }

    @Test
    void assertAllowed_后缀不允许抛出异常()
    {
        MultipartFile exe = file("virus.exe", "application/octet-stream", 1);

        assertThrows(InvalidExtensionException.class,
                () -> FileUploadUtils.assertAllowed(exe, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION));
        // 传入图片类型常量时使用图片专用异常
        assertThrows(InvalidExtensionException.InvalidImageExtensionException.class,
                () -> FileUploadUtils.assertAllowed(exe, MimeTypeUtils.IMAGE_EXTENSION));
    }

    @Test
    void assertAllowed_合法文件通过() throws Exception
    {
        FileUploadUtils.assertAllowed(file("合同.pdf", "application/pdf", 1),
                MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
    }

    @Test
    void upload_默认命名按日期目录落盘() throws Exception
    {
        String baseDir = tempDir.resolve("upload").toString();
        byte[] content = "合同内容".getBytes(StandardCharsets.UTF_8);

        String result = FileUploadUtils.upload(baseDir,
                new StubMultipartFile("合同.pdf", "application/pdf", content));

        assertTrue(result.startsWith(Constants.RESOURCE_PREFIX + "/upload/"));
        assertTrue(result.matches("/profile/upload/\\d{4}/\\d{2}/\\d{2}/合同_\\d{14}A\\d{3}\\.pdf"));

        Path stored = tempDir.resolve(result.substring(Constants.RESOURCE_PREFIX.length() + 1));
        assertTrue(Files.exists(stored));
        assertArrayEquals(content, Files.readAllBytes(stored));
    }

    @Test
    void upload_自定义命名使用UUID() throws Exception
    {
        String baseDir = tempDir.resolve("upload").toString();

        String result = FileUploadUtils.upload(baseDir,
                new StubMultipartFile("合同.pdf", "application/pdf", "x".getBytes(StandardCharsets.UTF_8)),
                MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION, true);

        assertTrue(result.matches("/profile/upload/\\d{4}/\\d{2}/\\d{2}/[0-9a-f]{32}\\.pdf"));
    }

    @Test
    void upload_文件名超长抛出异常()
    {
        MultipartFile longName = file("a".repeat(101) + ".pdf", "application/pdf", 1);

        assertThrows(FileNameLengthLimitExceededException.class,
                () -> FileUploadUtils.upload(tempDir.toString(), longName, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION));
    }

    @Test
    void getAbsoluteFile_自动创建父目录() throws Exception
    {
        File file = FileUploadUtils.getAbsoluteFile(tempDir.toString(), "a/b/c.txt");

        assertTrue(file.getParentFile().exists());
        assertFalse(file.exists());
    }

    private static MultipartFile file(String originalFilename, String contentType, int size)
    {
        return new StubMultipartFile(originalFilename, contentType, new byte[size]);
    }

    /**
     * 简单的内存 MultipartFile 实现。
     */
    private static class StubMultipartFile implements MultipartFile
    {
        private final String originalFilename;

        private final String contentType;

        private final byte[] content;

        private final long size;

        StubMultipartFile(String originalFilename, String contentType, byte[] content)
        {
            this(originalFilename, contentType, content, content.length);
        }

        StubMultipartFile(String originalFilename, String contentType, byte[] content, long size)
        {
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
            this.size = size;
        }

        @Override
        public String getName()
        {
            return "file";
        }

        @Override
        public String getOriginalFilename()
        {
            return originalFilename;
        }

        @Override
        public String getContentType()
        {
            return contentType;
        }

        @Override
        public boolean isEmpty()
        {
            return content.length == 0;
        }

        @Override
        public long getSize()
        {
            return size;
        }

        @Override
        public byte[] getBytes() throws IOException
        {
            return content;
        }

        @Override
        public InputStream getInputStream() throws IOException
        {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException
        {
            Files.copy(getInputStream(), dest.toPath());
        }
    }
}
