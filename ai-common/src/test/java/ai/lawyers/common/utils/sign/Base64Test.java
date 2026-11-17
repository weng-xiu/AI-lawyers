package ai.lawyers.common.utils.sign;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Base64 工具测试：编码分组、填充符、空白剔除与非法输入。
 *
 * @author ai-lawyers
 */
class Base64Test
{
    @Test
    void encode_不同长度按三字节分组并补等号()
    {
        assertEquals("", Base64.encode(new byte[0]));
        assertEquals("TQ==", Base64.encode("M".getBytes(StandardCharsets.UTF_8)));
        assertEquals("TWE=", Base64.encode("Ma".getBytes(StandardCharsets.UTF_8)));
        assertEquals("TWFu", Base64.encode("Man".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void encode_空入参返回null()
    {
        assertNull(Base64.encode(null));
    }

    @Test
    void encode与decode_中文往返一致()
    {
        byte[] source = "法律援助中心".getBytes(StandardCharsets.UTF_8);
        String encoded = Base64.encode(source);

        assertArrayEquals(source, Base64.decode(encoded));
    }

    @Test
    void decode_忽略空白字符()
    {
        assertArrayEquals("Man".getBytes(StandardCharsets.UTF_8), Base64.decode("TW Fu"));
        assertArrayEquals("Man".getBytes(StandardCharsets.UTF_8), Base64.decode("TWFu\r\n"));
    }

    @Test
    void decode_空值与空串处理()
    {
        assertNull(Base64.decode(null));
        assertEquals(0, Base64.decode("").length);
    }

    @Test
    void decode_长度非四的倍数返回null()
    {
        assertNull(Base64.decode("TWF"));
        assertNull(Base64.decode("T"));
    }

    @Test
    void decode_非法字符返回null()
    {
        assertNull(Base64.decode("TW*u"));
        assertNull(Base64.decode("===="));
        // 末四位中前两位必须是数据字符
        assertNull(Base64.decode("==TQ"));
        // 填充位剩余比特必须为零
        assertNull(Base64.decode("TR=="));
        assertNull(Base64.decode("TWF="));
    }

    @Test
    void decode_带填充符的结尾还原字节()
    {
        assertEquals("M", new String(Base64.decode("TQ=="), StandardCharsets.UTF_8));
        assertEquals("Ma", new String(Base64.decode("TWE="), StandardCharsets.UTF_8));
    }
}
