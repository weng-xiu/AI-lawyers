package ai.lawyers.web.controller.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.utils.file.FileUploadUtils;
import ai.lawyers.common.utils.file.FileUtils;
import ai.lawyers.framework.config.ServerConfig;

/**
 * 通用上传下载接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class CommonControllerTest
{
    @Mock
    private ServerConfig serverConfig;

    @InjectMocks
    private CommonController controller;

    private MockMultipartFile file(String name)
    {
        return new MockMultipartFile("file", name, "text/plain", new byte[] { 1, 2 });
    }

    @Test
    void 下载文件名非法时不做响应()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<FileUtils> mocked = mockStatic(FileUtils.class))
        {
            mocked.when(() -> FileUtils.checkAllowDownload("../etc/passwd")).thenReturn(false);
            controller.fileDownload("../etc/passwd", true, response, request);

            mocked.verify(() -> FileUtils.writeBytes(anyString(), any()), never());
        }
        assertNull(response.getContentType());
        assertEquals(0, response.getContentAsByteArray().length);
    }

    @Test
    void 下载成功后删除临时文件()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<FileUtils> mocked = mockStatic(FileUtils.class))
        {
            mocked.when(() -> FileUtils.checkAllowDownload("import_合同.txt")).thenReturn(true);
            controller.fileDownload("import_合同.txt", true, response, request);

            mocked.verify(() -> FileUtils.deleteFile(anyString()));
        }
        assertTrue(response.getContentType().startsWith("application/octet-stream"));
    }

    @Test
    void 下载不删除时不清理文件()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<FileUtils> mocked = mockStatic(FileUtils.class))
        {
            mocked.when(() -> FileUtils.checkAllowDownload("import_合同.txt")).thenReturn(true);
            controller.fileDownload("import_合同.txt", false, response, request);

            mocked.verify(() -> FileUtils.deleteFile(anyString()), never());
        }
    }

    @Test
    void 单文件上传返回访问地址() throws Exception
    {
        when(serverConfig.getUrl()).thenReturn("http://localhost:8080");

        try (MockedStatic<FileUploadUtils> upload = mockStatic(FileUploadUtils.class);
                MockedStatic<FileUtils> fileUtils = mockStatic(FileUtils.class))
        {
            upload.when(() -> FileUploadUtils.upload(anyString(), any(MultipartFile.class)))
                    .thenReturn("/profile/upload/2026/11/20/x.txt");
            fileUtils.when(() -> FileUtils.getName("/profile/upload/2026/11/20/x.txt")).thenReturn("x.txt");

            AjaxResult result = controller.uploadFile(file("合同.pdf"));

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            assertEquals("http://localhost:8080/profile/upload/2026/11/20/x.txt", result.get("url"));
            assertEquals("/profile/upload/2026/11/20/x.txt", result.get("fileName"));
            assertEquals("x.txt", result.get("newFileName"));
            assertEquals("合同.pdf", result.get("originalFilename"));
        }
    }

    @Test
    void 单文件上传异常返回错误信息() throws Exception
    {
        try (MockedStatic<FileUploadUtils> upload = mockStatic(FileUploadUtils.class))
        {
            upload.when(() -> FileUploadUtils.upload(anyString(), any(MultipartFile.class)))
                    .thenThrow(new IOException("磁盘已满"));

            AjaxResult result = controller.uploadFile(file("合同.pdf"));

            assertEquals(500, result.get(AjaxResult.CODE_TAG));
            assertEquals("磁盘已满", result.get(AjaxResult.MSG_TAG));
        }
    }

    @Test
    void 多文件上传逗号拼接结果() throws Exception
    {
        when(serverConfig.getUrl()).thenReturn("http://localhost:8080");
        List<MultipartFile> files = new ArrayList<MultipartFile>();
        files.add(file("a.txt"));
        files.add(file("b.txt"));

        try (MockedStatic<FileUploadUtils> upload = mockStatic(FileUploadUtils.class);
                MockedStatic<FileUtils> fileUtils = mockStatic(FileUtils.class))
        {
            upload.when(() -> FileUploadUtils.upload(anyString(), any(MultipartFile.class)))
                    .thenReturn("/u/a.txt", "/u/b.txt");
            fileUtils.when(() -> FileUtils.getName(anyString()))
                    .thenReturn("a.txt", "b.txt");

            AjaxResult result = controller.uploadFiles(files);

            assertEquals("http://localhost:8080/u/a.txt,http://localhost:8080/u/b.txt", result.get("urls"));
            assertEquals("/u/a.txt,/u/b.txt", result.get("fileNames"));
            assertEquals("a.txt,b.txt", result.get("newFileNames"));
            assertEquals("a.txt,b.txt", result.get("originalFilenames"));
        }
    }

    @Test
    void 多文件上传异常返回错误信息() throws Exception
    {
        List<MultipartFile> files = new ArrayList<MultipartFile>();
        files.add(file("a.txt"));

        try (MockedStatic<FileUploadUtils> upload = mockStatic(FileUploadUtils.class))
        {
            upload.when(() -> FileUploadUtils.upload(anyString(), any(MultipartFile.class)))
                    .thenThrow(new IOException("上传失败"));

            assertEquals("上传失败", controller.uploadFiles(files).get(AjaxResult.MSG_TAG));
        }
    }

    @Test
    void 资源下载非法时不做响应() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<FileUtils> mocked = mockStatic(FileUtils.class))
        {
            mocked.when(() -> FileUtils.checkAllowDownload("../secret")).thenReturn(false);
            controller.resourceDownload("../secret", request, response);

            mocked.verify(() -> FileUtils.writeBytes(anyString(), any()), never());
        }
        assertNull(response.getContentType());
    }

    @Test
    void 资源下载合法时写出文件流() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<FileUtils> mocked = mockStatic(FileUtils.class))
        {
            mocked.when(() -> FileUtils.checkAllowDownload("/profile/download/x.txt")).thenReturn(true);
            mocked.when(() -> FileUtils.stripPrefix("/profile/download/x.txt")).thenReturn("/download/x.txt");

            controller.resourceDownload("/profile/download/x.txt", request, response);

            mocked.verify(() -> FileUtils.setAttachmentResponseHeader(eq(response), eq("x.txt")));
            mocked.verify(() -> FileUtils.writeBytes(anyString(), any()));
        }
        assertTrue(response.getContentType().startsWith("application/octet-stream"));
    }
}
