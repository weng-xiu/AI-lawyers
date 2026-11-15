package ai.lawyers.system.service.impl.lawyers.ivr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrIntention;
import ai.lawyers.system.mapper.lawyers.ivr.AiIvrIntentionMapper;

/**
 * IVR 意图服务单测。
 *
 * <p>覆盖意图查询、新增/修改/删除委托，以及按正则匹配意图时的空输入、非法正则跳过与大小写不敏感匹配。</p>
 *
 * @author ai-lawyers
 */
class AiIvrIntentionServiceImplTest
{
    private AiIvrIntentionServiceImpl service;

    @Mock private AiIvrIntentionMapper aiIvrIntentionMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiIvrIntentionServiceImpl();
        ReflectionTestUtils.setField(service, "aiIvrIntentionMapper", aiIvrIntentionMapper);
    }

    private static AiIvrIntention intention(String code, String regex)
    {
        AiIvrIntention intention = new AiIvrIntention();
        intention.setIntentionCode(code);
        intention.setRegexPattern(regex);
        return intention;
    }

    @Test
    void selectAiIvrIntentionByIntentionId_delegates()
    {
        AiIvrIntention expected = new AiIvrIntention();
        when(aiIvrIntentionMapper.selectAiIvrIntentionByIntentionId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiIvrIntentionByIntentionId(1L));
    }

    @Test
    void selectAiIvrIntentionList_delegates()
    {
        AiIvrIntention query = new AiIvrIntention();
        List<AiIvrIntention> list = Collections.singletonList(new AiIvrIntention());
        when(aiIvrIntentionMapper.selectAiIvrIntentionList(query)).thenReturn(list);

        assertSame(list, service.selectAiIvrIntentionList(query));
    }

    @Test
    void selectAiIvrIntentionByCode_delegates()
    {
        AiIvrIntention expected = new AiIvrIntention();
        when(aiIvrIntentionMapper.selectAiIvrIntentionByCode("A")).thenReturn(expected);

        assertSame(expected, service.selectAiIvrIntentionByCode("A"));
    }

    @Test
    void selectActiveIntentions_delegates()
    {
        List<AiIvrIntention> list = Collections.singletonList(new AiIvrIntention());
        when(aiIvrIntentionMapper.selectActiveIntentions()).thenReturn(list);

        assertSame(list, service.selectActiveIntentions());
    }

    @Test
    void insertAiIvrIntention_delegates()
    {
        AiIvrIntention intention = new AiIvrIntention();
        when(aiIvrIntentionMapper.insertAiIvrIntention(intention)).thenReturn(1);

        assertEquals(1, service.insertAiIvrIntention(intention));
    }

    @Test
    void updateAiIvrIntention_delegates()
    {
        AiIvrIntention intention = new AiIvrIntention();
        when(aiIvrIntentionMapper.updateAiIvrIntention(intention)).thenReturn(1);

        assertEquals(1, service.updateAiIvrIntention(intention));
    }

    @Test
    void deleteAiIvrIntentionByIntentionId_delegates()
    {
        when(aiIvrIntentionMapper.deleteAiIvrIntentionByIntentionId(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiIvrIntentionByIntentionId(1L));
    }

    @Test
    void deleteAiIvrIntentionByIntentionIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiIvrIntentionMapper.deleteAiIvrIntentionByIntentionIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiIvrIntentionByIntentionIds(ids));
    }

    @Test
    void matchIntention_emptyInput_returnsNull()
    {
        assertNull(service.matchIntention(""));
        assertNull(service.matchIntention(null));
    }

    @Test
    void matchIntention_regexMatched_returnsIntention()
    {
        AiIvrIntention target = intention("REFUND", "退\\s*款");
        when(aiIvrIntentionMapper.selectActiveIntentions()).thenReturn(Collections.singletonList(target));

        assertSame(target, service.matchIntention("我要退款"));
    }

    @Test
    void matchIntention_caseInsensitive()
    {
        AiIvrIntention target = intention("CONSULT", "lawyer");
        when(aiIvrIntentionMapper.selectActiveIntentions()).thenReturn(Collections.singletonList(target));

        assertSame(target, service.matchIntention("I need a LAWYER"));
    }

    @Test
    void matchIntention_skipsInvalidAndEmptyPatterns()
    {
        AiIvrIntention invalid = intention("BAD", "[");
        AiIvrIntention empty = intention("EMPTY", "");
        AiIvrIntention target = intention("TARGET", "咨询");
        when(aiIvrIntentionMapper.selectActiveIntentions()).thenReturn(Arrays.asList(invalid, empty, target));

        assertSame(target, service.matchIntention("法律咨询"));
    }

    @Test
    void matchIntention_noMatch_returnsNull()
    {
        when(aiIvrIntentionMapper.selectActiveIntentions())
            .thenReturn(Collections.singletonList(intention("REFUND", "退款")));

        assertNull(service.matchIntention("你好"));
    }
}
