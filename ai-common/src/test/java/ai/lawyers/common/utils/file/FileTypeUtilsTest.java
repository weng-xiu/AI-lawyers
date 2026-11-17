package ai.lawyers.common.utils.file;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;

import org.junit.jupiter.api.Test;

/**
 * 文件类型工具测试：后缀提取与图片魔数识别。
 *
 * @author ai-lawyers
 */
class FileTypeUtilsTest
{
    @Test
    void getFileType_按文件名提取小写后缀()
    {
        assertEquals("txt", FileTypeUtils.getFileType("ruoyi.txt"));
        assertEquals("pdf", FileTypeUtils.getFileType("法律援助.PDF"));
        assertEquals("gz", FileTypeUtils.getFileType("a.b.tar.gz"));
    }

    @Test
    void getFileType_无后缀或空文件返回空串()
    {
        assertEquals("", FileTypeUtils.getFileType("noext"));
        assertEquals("", FileTypeUtils.getFileType(new File("noext")));
        assertEquals("", FileTypeUtils.getFileType((File) null));
        // 以点开头的隐藏文件仍按第一个点之后的内容作为后缀
        assertEquals("hidden", FileTypeUtils.getFileType(".hidden"));
    }

    @Test
    void getFileType_File入参取文件名后缀()
    {
        assertEquals("docx", FileTypeUtils.getFileType(new File("合同模板.docx")));
    }

    @Test
    void getFileExtendName_识别GIF魔数()
    {
        assertEquals("GIF", FileTypeUtils.getFileExtendName(
                new byte[] { 71, 73, 70, 56, 55, 97, 0, 0, 0, 0 }));
        assertEquals("GIF", FileTypeUtils.getFileExtendName(
                new byte[] { 71, 73, 70, 56, 57, 97, 0, 0, 0, 0 }));
    }

    @Test
    void getFileExtendName_识别JPG与BMP魔数()
    {
        assertEquals("JPG", FileTypeUtils.getFileExtendName(
                new byte[] { 0, 0, 0, 0, 0, 0, 74, 70, 73, 70 }));
        assertEquals("BMP", FileTypeUtils.getFileExtendName(
                new byte[] { 66, 77, 0, 0, 0, 0, 0, 0, 0, 0 }));
    }

    @Test
    void getFileExtendName_识别PNG魔数()
    {
        assertEquals("PNG", FileTypeUtils.getFileExtendName(
                new byte[] { 0, 80, 78, 71, 0, 0, 0, 0, 0, 0 }));
    }

    @Test
    void getFileExtendName_无法识别时默认JPG()
    {
        assertEquals("JPG", FileTypeUtils.getFileExtendName(new byte[10]));
        assertEquals("JPG", FileTypeUtils.getFileExtendName(
                new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }));
    }
}
