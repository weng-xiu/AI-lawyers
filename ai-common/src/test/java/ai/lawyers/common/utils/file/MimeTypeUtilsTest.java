package ai.lawyers.common.utils.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 媒体类型工具测试：图片 MIME 与扩展名互转、非图片类型兜底空串。
 *
 * @author ai-lawyers
 */
class MimeTypeUtilsTest
{
    @Test
    void getExtension_图片类型映射扩展名()
    {
        assertEquals("png", MimeTypeUtils.getExtension(MimeTypeUtils.IMAGE_PNG));
        assertEquals("jpg", MimeTypeUtils.getExtension(MimeTypeUtils.IMAGE_JPG));
        assertEquals("jpeg", MimeTypeUtils.getExtension(MimeTypeUtils.IMAGE_JPEG));
        assertEquals("bmp", MimeTypeUtils.getExtension(MimeTypeUtils.IMAGE_BMP));
        assertEquals("gif", MimeTypeUtils.getExtension(MimeTypeUtils.IMAGE_GIF));
    }

    @Test
    void getExtension_非图片类型返回空串()
    {
        assertEquals("", MimeTypeUtils.getExtension("application/pdf"));
        assertEquals("", MimeTypeUtils.getExtension("video/mp4"));
        assertEquals("", MimeTypeUtils.getExtension(""));
    }

    @Test
    void 图片扩展名清单覆盖全部图片类型()
    {
        assertArrayEquals(new String[] { "bmp", "gif", "jpg", "jpeg", "png" },
                MimeTypeUtils.IMAGE_EXTENSION);
        assertEquals(5, MimeTypeUtils.IMAGE_EXTENSION.length);
    }
}
