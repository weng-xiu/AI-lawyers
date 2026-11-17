package ai.lawyers.system.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * AI 辅助会话状态枚举测试：编码解析与未知兜底。
 *
 * @author ai-lawyers
 */
class AiAssistSessionStatusEnumTest
{
    @Test
    void getByCode_按编码解析()
    {
        assertSame(AiAssistSessionStatusEnum.INIT, AiAssistSessionStatusEnum.getByCode("0"));
        assertSame(AiAssistSessionStatusEnum.ANALYZING, AiAssistSessionStatusEnum.getByCode("1"));
        assertSame(AiAssistSessionStatusEnum.RECOMMENDING, AiAssistSessionStatusEnum.getByCode("2"));
        assertSame(AiAssistSessionStatusEnum.SUMMARIZED, AiAssistSessionStatusEnum.getByCode("3"));
        assertSame(AiAssistSessionStatusEnum.ENDED, AiAssistSessionStatusEnum.getByCode("4"));
        assertSame(AiAssistSessionStatusEnum.ERROR, AiAssistSessionStatusEnum.getByCode("9"));
    }

    @Test
    void getByCode_未知与空值返回null()
    {
        assertNull(AiAssistSessionStatusEnum.getByCode("5"));
        assertNull(AiAssistSessionStatusEnum.getByCode(""));
        assertNull(AiAssistSessionStatusEnum.getByCode(null));
    }

    @Test
    void 编码与描述符合预期()
    {
        assertEquals("初始化", AiAssistSessionStatusEnum.INIT.getDesc());
        assertEquals("分析中", AiAssistSessionStatusEnum.ANALYZING.getDesc());
        assertEquals("推荐中", AiAssistSessionStatusEnum.RECOMMENDING.getDesc());
        assertEquals("已小结", AiAssistSessionStatusEnum.SUMMARIZED.getDesc());
        assertEquals("已结束", AiAssistSessionStatusEnum.ENDED.getDesc());
        assertEquals("异常", AiAssistSessionStatusEnum.ERROR.getDesc());
    }
}
