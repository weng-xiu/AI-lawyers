package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiSystemPrompt;
import ai.lawyers.system.mapper.lawyers.AiSystemPromptMapper;

/**
 * 系统提示词服务单测。
 *
 * <p>覆盖提示词查询、增删改委托、默认提示词互斥处理、默认提示词获取，
 * 以及预览时的变量替换、变量为空、提示词不存在与 JSON 解析失败场景。</p>
 *
 * @author ai-lawyers
 */
class AiSystemPromptServiceImplTest
{
    private AiSystemPromptServiceImpl service;

    @Mock private AiSystemPromptMapper aiSystemPromptMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiSystemPromptServiceImpl();
        ReflectionTestUtils.setField(service, "aiSystemPromptMapper", aiSystemPromptMapper);
    }

    private static AiSystemPrompt prompt(Long promptId, String isDefault, String content)
    {
        AiSystemPrompt prompt = new AiSystemPrompt();
        prompt.setPromptId(promptId);
        prompt.setIsDefault(isDefault);
        prompt.setContent(content);
        return prompt;
    }

    @Test
    void selectAiSystemPromptByPromptId_delegates()
    {
        AiSystemPrompt expected = new AiSystemPrompt();
        when(aiSystemPromptMapper.selectAiSystemPromptByPromptId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiSystemPromptByPromptId(1L));
    }

    @Test
    void selectAiSystemPromptList_delegates()
    {
        AiSystemPrompt query = new AiSystemPrompt();
        List<AiSystemPrompt> list = Collections.singletonList(new AiSystemPrompt());
        when(aiSystemPromptMapper.selectAiSystemPromptList(query)).thenReturn(list);

        assertSame(list, service.selectAiSystemPromptList(query));
    }

    @Test
    void insertAiSystemPrompt_default_resetsOtherDefaults()
    {
        AiSystemPrompt prompt = prompt(1L, "1", "内容");
        when(aiSystemPromptMapper.insertAiSystemPrompt(prompt)).thenReturn(1);

        assertEquals(1, service.insertAiSystemPrompt(prompt));
        verify(aiSystemPromptMapper).updateDefaultPrompt(1L);
    }

    @Test
    void insertAiSystemPrompt_notDefault_skipsReset()
    {
        AiSystemPrompt prompt = prompt(1L, "0", "内容");
        when(aiSystemPromptMapper.insertAiSystemPrompt(prompt)).thenReturn(1);

        assertEquals(1, service.insertAiSystemPrompt(prompt));
        verify(aiSystemPromptMapper, never()).updateDefaultPrompt(1L);
    }

    @Test
    void updateAiSystemPrompt_default_resetsOtherDefaults()
    {
        AiSystemPrompt prompt = prompt(1L, "1", "内容");
        when(aiSystemPromptMapper.updateAiSystemPrompt(prompt)).thenReturn(1);

        assertEquals(1, service.updateAiSystemPrompt(prompt));
        verify(aiSystemPromptMapper).updateDefaultPrompt(1L);
    }

    @Test
    void deleteAiSystemPromptByPromptIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiSystemPromptMapper.deleteAiSystemPromptByPromptIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiSystemPromptByPromptIds(ids));
    }

    @Test
    void deleteAiSystemPromptByPromptId_delegates()
    {
        when(aiSystemPromptMapper.deleteAiSystemPromptByPromptId(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiSystemPromptByPromptId(1L));
    }

    @Test
    void getDefaultAiSystemPrompt_delegates()
    {
        AiSystemPrompt expected = new AiSystemPrompt();
        when(aiSystemPromptMapper.selectDefaultAiSystemPrompt("CHAT", "CONSULT")).thenReturn(expected);

        assertSame(expected, service.getDefaultAiSystemPrompt("CHAT", "CONSULT"));
    }

    @Test
    void setDefaultPrompt_delegates()
    {
        when(aiSystemPromptMapper.updateDefaultPrompt(1L)).thenReturn(1);

        assertEquals(1, service.setDefaultPrompt(1L));
    }

    @Test
    void previewPrompt_missingPrompt_returnsMessage()
    {
        when(aiSystemPromptMapper.selectAiSystemPromptByPromptId(1L)).thenReturn(null);

        assertEquals("提示词不存在", service.previewPrompt(1L, null));
    }

    @Test
    void previewPrompt_noVariables_returnsContent()
    {
        when(aiSystemPromptMapper.selectAiSystemPromptByPromptId(1L))
            .thenReturn(prompt(1L, "0", "你好{name}"));

        assertEquals("你好{name}", service.previewPrompt(1L, ""));
        assertEquals("你好{name}", service.previewPrompt(1L, null));
    }

    @Test
    void previewPrompt_replacesVariables()
    {
        when(aiSystemPromptMapper.selectAiSystemPromptByPromptId(1L))
            .thenReturn(prompt(1L, "0", "你好{name}，您的诉求是{issue}"));

        assertEquals("你好张三，您的诉求是退款",
            service.previewPrompt(1L, "{\"name\":\"张三\",\"issue\":\"退款\"}"));
    }

    @Test
    void previewPrompt_invalidJson_returnsParseError()
    {
        when(aiSystemPromptMapper.selectAiSystemPromptByPromptId(1L))
            .thenReturn(prompt(1L, "0", "内容"));

        String result = service.previewPrompt(1L, "not-json");

        assertEquals(true, result.startsWith("变量解析失败："));
    }
}
