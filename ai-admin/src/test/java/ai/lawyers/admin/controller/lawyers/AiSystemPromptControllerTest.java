package ai.lawyers.admin.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.system.domain.lawyers.AiSystemPrompt;
import ai.lawyers.system.service.lawyers.IAiSystemPromptService;

/**
 * 系统提示词配置接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiSystemPromptControllerTest
{
    @Mock
    private IAiSystemPromptService aiSystemPromptService;

    @InjectMocks
    private AiSystemPromptController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private AiSystemPrompt prompt(Long promptId)
    {
        AiSystemPrompt prompt = new AiSystemPrompt();
        prompt.setPromptId(promptId);
        prompt.setPromptName("默认咨询提示词");
        prompt.setPromptType("CHAT");
        prompt.setSceneType("CONSULTATION");
        return prompt;
    }

    @Test
    void 提示词列表分页返回()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiSystemPrompt query = new AiSystemPrompt();
        List<AiSystemPrompt> list = new ArrayList<AiSystemPrompt>();
        list.add(prompt(1L));
        when(aiSystemPromptService.selectAiSystemPromptList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertSame(list, table.getRows());
    }

    @Test
    void 导出提示词Excel()
    {
        when(aiSystemPromptService.selectAiSystemPromptList(any(AiSystemPrompt.class)))
                .thenReturn(List.of(prompt(1L)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiSystemPrompt());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情新增修改与删除()
    {
        AiSystemPrompt prompt = prompt(1L);
        Long[] ids = new Long[] { 1L };
        when(aiSystemPromptService.selectAiSystemPromptByPromptId(1L)).thenReturn(prompt);
        when(aiSystemPromptService.insertAiSystemPrompt(prompt)).thenReturn(1);
        when(aiSystemPromptService.updateAiSystemPrompt(prompt)).thenReturn(0);
        when(aiSystemPromptService.deleteAiSystemPromptByPromptIds(ids)).thenReturn(1);

        assertSame(prompt, controller.getInfo(1L).get(AjaxResult.DATA_TAG));
        assertEquals(200, controller.add(prompt).get(AjaxResult.CODE_TAG));
        assertEquals(500, controller.edit(prompt).get(AjaxResult.CODE_TAG));
        assertEquals(200, controller.remove(ids).get(AjaxResult.CODE_TAG));
    }

    @Test
    void 默认提示词查询与设置()
    {
        AiSystemPrompt prompt = prompt(1L);
        when(aiSystemPromptService.getDefaultAiSystemPrompt("CHAT", "CONSULTATION")).thenReturn(prompt);
        when(aiSystemPromptService.setDefaultPrompt(1L)).thenReturn(1);

        assertSame(prompt, controller.getDefault("CHAT", "CONSULTATION").get(AjaxResult.DATA_TAG));
        assertEquals(200, controller.setDefault(1L).get(AjaxResult.CODE_TAG));
    }

    @Test
    void 预览提示词返回渲染结果()
    {
        when(aiSystemPromptService.previewPrompt(1L, "{\"name\":\"张三\"}")).thenReturn("您好，张三");

        AjaxResult result = controller.preview(1L, "{\"name\":\"张三\"}");

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        assertEquals("您好，张三", result.get(AjaxResult.MSG_TAG));
    }
}
