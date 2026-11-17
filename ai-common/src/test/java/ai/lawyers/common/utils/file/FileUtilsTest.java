package ai.lawyers.common.utils.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ai.lawyers.common.config.RuoYiConfig;

/**
 * 文件工具测试：路径处理、文件名校验、下载白名单、图片后缀识别、
 * 文件删除与读写、下载响应头编码。
 *
 * @author ai-lawyers
 */
class FileUtilsTest
{
    @TempDir
    File tempDir;

    @Test
    void stripPrefix_移除资源前缀()
    {
        assertEquals("/upload/a.png", FileUtils.stripPrefix("/profile/upload/a.png"));
        assertEquals("", FileUtils.stripPrefix("abc"));
    }

    @Test
    void isValidFilename_合法与非法文件名()
    {
        assertTrue(FileUtils.isValidFilename("中文_1-2.txt"));
        assertFalse(FileUtils.isValidFilename("a b.txt"));
        assertFalse(FileUtils.isValidFilename("a/b.txt"));
    }

    @Test
    void checkAllowDownload_目录上跳与后缀白名单()
    {
        assertTrue(FileUtils.checkAllowDownload("report.txt"));
        assertFalse(FileUtils.checkAllowDownload("../report.txt"));
        assertFalse(FileUtils.checkAllowDownload("virus.exe"));
        assertFalse(FileUtils.checkAllowDownload("noext"));
    }

    @Test
    void getName与getNameNotSuffix_去除路径与后缀()
    {
        assertEquals("ruoyi.png", FileUtils.getName("/profile/upload/2022/04/16/ruoyi.png"));
        assertEquals("ruoyi.png", FileUtils.getName("\\profile\\upload\\ruoyi.png"));
        assertNull(FileUtils.getName(null));

        assertEquals("ruoyi", FileUtils.getNameNotSuffix("/profile/upload/2022/04/16/ruoyi.png"));
        assertNull(FileUtils.getNameNotSuffix(null));
    }

    @Test
    void getFileExtendName_按魔数识别图片后缀()
    {
        assertEquals("gif", FileUtils.getFileExtendName(new byte[] { 71, 73, 70, 56, 57, 97, 0, 0, 0, 0 }));
        assertEquals("jpg", FileUtils.getFileExtendName(new byte[] { 0, 0, 0, 0, 0, 0, 74, 70, 73, 70 }));
        assertEquals("bmp", FileUtils.getFileExtendName(new byte[] { 66, 77, 0, 0, 0, 0, 0, 0, 0, 0 }));
        assertEquals("png", FileUtils.getFileExtendName(new byte[] { 0, 80, 78, 71, 0, 0, 0, 0, 0, 0 }));
        // 无法识别时默认按 jpg 处理
        assertEquals("jpg", FileUtils.getFileExtendName(new byte[10]));
    }

    @Test
    void deleteFile_存在则删除不存在返回false() throws Exception
    {
        File file = new File(tempDir, "delete-me.txt");
        Files.write(file.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        assertTrue(FileUtils.deleteFile(file.getAbsolutePath()));
        assertFalse(file.exists());

        assertFalse(FileUtils.deleteFile(new File(tempDir, "missing.txt").getAbsolutePath()));
    }

    @Test
    void writeBytes_按魔数生成后缀文件() throws Exception
    {
        byte[] png = new byte[] { 0, 80, 78, 71, 1, 2, 3, 4, 5, 6 };
        // getPathFileName 依赖 RuoYiConfig 的静态 profile 配置
        Field profile = RuoYiConfig.class.getDeclaredField("profile");
        profile.setAccessible(true);
        String original = (String) profile.get(null);
        profile.set(null, tempDir.getAbsolutePath());
        try
        {
            String path = FileUtils.writeBytes(png, tempDir.getAbsolutePath());
            assertTrue(path.endsWith(".png"));
        }
        finally
        {
            profile.set(null, original);
        }
    }

    @Test
    void writeBytes_输出流复制与文件不存在异常() throws Exception
    {
        File file = new File(tempDir, "source.txt");
        Files.write(file.toPath(), "hello".getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileUtils.writeBytes(file.getAbsolutePath(), out);
        assertEquals("hello", out.toString(StandardCharsets.UTF_8));

        assertThrows(FileNotFoundException.class,
                () -> FileUtils.writeBytes(new File(tempDir, "missing.txt").getAbsolutePath(), new ByteArrayOutputStream()));
    }

    @Test
    void percentEncode_空格编码为百分号二十() throws Exception
    {
        assertEquals("%E4%B8%AD%E6%96%87%20%E7%A9%BA%E6%A0%BC.txt", FileUtils.percentEncode("中文 空格.txt"));
    }

    @Test
    void setFileDownloadHeader_按浏览器类型编码() throws Exception
    {
        // IE 使用 URLEncoder 后把加号还原为空格
        assertEquals("a b.txt", FileUtils.setFileDownloadHeader(request("MSIE 11.0"), "a b.txt"));
        // Chrome 与其它浏览器统一使用 UTF-8 百分号编码
        assertEquals("%E4%B8%AD%E6%96%87.txt", FileUtils.setFileDownloadHeader(request("Chrome/120"), "中文.txt"));
        assertEquals("%E4%B8%AD%E6%96%87.txt", FileUtils.setFileDownloadHeader(request("Safari/605"), "中文.txt"));
        // Firefox 走 ISO-8859-1 重编码，结果不应与原值相同
        String firefox = FileUtils.setFileDownloadHeader(request("Firefox/121"), "中文.txt");
        assertNotNull(firefox);
        assertNotEquals("中文.txt", firefox);
    }

    @Test
    void setAttachmentResponseHeader_写入下载响应头() throws Exception
    {
        ResponseEntityRecorder recorder = new ResponseEntityRecorder();
        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { HttpServletResponse.class }, recorder);

        FileUtils.setAttachmentResponseHeader(response, "中文.txt");

        assertEquals("attachment; filename=%E4%B8%AD%E6%96%87.txt;filename*=utf-8''%E4%B8%AD%E6%96%87.txt",
                recorder.headers.get("Content-disposition"));
        assertEquals("%E4%B8%AD%E6%96%87.txt", recorder.headers.get("download-filename"));
        assertEquals("Content-Disposition,download-filename", recorder.headers.get("Access-Control-Expose-Headers"));
    }

    /** 构造只响应 getHeader 的请求代理 */
    private HttpServletRequest request(String userAgent)
    {
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { HttpServletRequest.class },
                (proxy, method, args) -> "getHeader".equals(method.getName()) ? userAgent : null);
    }

    /** 记录 setHeader/addHeader 写入的响应头 */
    private static final class ResponseEntityRecorder implements InvocationHandler
    {
        private final Map<String, String> headers = new HashMap<>();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
        {
            if (("setHeader".equals(method.getName()) || "addHeader".equals(method.getName()))
                    && args != null && args.length == 2)
            {
                headers.put((String) args[0], (String) args[1]);
            }
            return null;
        }
    }
}
