package ai.lawyers.common.utils.sign;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * MD5 工具测试：已知摘要值与确定性。
 *
 * @author ai-lawyers
 */
class Md5UtilsTest
{
    @Test
    void hash_输出三十二位小写十六进制()
    {
        assertEquals("e10adc3949ba59abbe56e057f20f883e", Md5Utils.hash("123456"));
    }

    @Test
    void hash_空串摘要正确()
    {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", Md5Utils.hash(""));
    }

    @Test
    void hash_中文内容按UTF8编码且结果稳定()
    {
        String first = Md5Utils.hash("法律援助");

        assertEquals(32, first.length());
        assertEquals(first, Md5Utils.hash("法律援助"));
    }
}
