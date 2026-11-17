package ai.lawyers.system.service.lawyers.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * 语音引擎枚举测试：编码解析、忽略大小写、空白与未知兜底为本地模拟。
 *
 * @author ai-lawyers
 */
class VoiceModelEnumTest
{
    @Test
    void of_按编码解析()
    {
        assertSame(VoiceModelEnum.ALI, VoiceModelEnum.of("ali"));
        assertSame(VoiceModelEnum.DIANXIN, VoiceModelEnum.of("dianxin"));
        assertSame(VoiceModelEnum.DASHSCOPE, VoiceModelEnum.of("dashscope"));
        assertSame(VoiceModelEnum.MOCK, VoiceModelEnum.of("mock"));
    }

    @Test
    void of_忽略大小写与首尾空白()
    {
        assertSame(VoiceModelEnum.DASHSCOPE, VoiceModelEnum.of("  DashScope "));
        assertSame(VoiceModelEnum.ALI, VoiceModelEnum.of("ALI"));
    }

    @Test
    void of_空值与未知编码回退本地模拟()
    {
        assertSame(VoiceModelEnum.MOCK, VoiceModelEnum.of(null));
        assertSame(VoiceModelEnum.MOCK, VoiceModelEnum.of(""));
        assertSame(VoiceModelEnum.MOCK, VoiceModelEnum.of("unknown"));
    }

    @Test
    void 编码与描述符合预期()
    {
        assertEquals("ali", VoiceModelEnum.ALI.getCode());
        assertEquals("阿里云", VoiceModelEnum.ALI.getDesc());
        assertEquals("通义千问", VoiceModelEnum.DASHSCOPE.getDesc());
    }
}
