package ai.lawyers.web.controller.lawyers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import ai.lawyers.system.service.lawyers.KnowledgeDraftService;
import ai.lawyers.system.service.lawyers.KnowledgeOpsService;
import ai.lawyers.system.service.lawyers.IAiLegalKnowledgeService;
import ai.lawyers.system.service.lawyers.rag.IRagIndexService;
import ai.lawyers.system.service.lawyers.rag.RagSearchService;
import ai.lawyers.system.service.lawyers.rag.VectorIndexRouter;
import ai.lawyers.web.support.AbstractPermMvcTest;
import ai.lawyers.web.support.PermMvcValidationConfig;

/**
 * P2-14：{@link AiLegalKnowledgeController} 请求体 Bean Validation 校验路径——
 * 启用真实 Validator，验证 add/edit 在 {@code @NotBlank/@Size} 违约时返回 400、
 * 合法体放行到 Service 返回 200。
 */
public class AiLegalKnowledgeControllerValidationTest extends AbstractPermMvcTest
{
    @Mock
    private IAiLegalKnowledgeService aiLegalKnowledgeService;

    @Mock
    private IRagIndexService ragIndexService;

    @Mock
    private VectorIndexRouter vectorIndexRouter;

    @Mock
    private RagSearchService ragSearchService;

    @Mock
    private KnowledgeDraftService knowledgeDraftService;

    /** F8 知识库运营分析 */
    @Mock
    private KnowledgeOpsService knowledgeOpsService;

    @Override
    protected Class<?> webMvcConfig()
    {
        return PermMvcValidationConfig.class;
    }

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        startMvc(AiLegalKnowledgeController.class);
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiLegalKnowledgeService", aiLegalKnowledgeService);
        registerMock("ragIndexService", ragIndexService);
        registerMock("vectorIndexRouter", vectorIndexRouter);
        registerMock("ragSearchService", ragSearchService);
        registerMock("knowledgeDraftService", knowledgeDraftService);
        registerMock("knowledgeOpsService", knowledgeOpsService);
    }

    @Test
    void add_blankTitle_400() throws Exception
    {
        mockMvc.perform(post("/lawyers/knowledge")
                        .with(loginAs("lawyers:knowledge:add"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"content\":\"正文内容\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void add_blankContent_400() throws Exception
    {
        mockMvc.perform(post("/lawyers/knowledge")
                        .with(loginAs("lawyers:knowledge:add"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"知识标题\",\"content\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void add_titleTooLong_400() throws Exception
    {
        String longTitle = "超".repeat(201);
        mockMvc.perform(post("/lawyers/knowledge")
                        .with(loginAs("lawyers:knowledge:add"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + longTitle + "\",\"content\":\"正文内容\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void add_validBody_200() throws Exception
    {
        when(aiLegalKnowledgeService.insertAiLegalKnowledge(any())).thenReturn(1);

        mockMvc.perform(post("/lawyers/knowledge")
                        .with(loginAs("lawyers:knowledge:add"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"知识标题\",\"content\":\"正文内容\"}"))
                .andExpect(status().isOk());

        verify(aiLegalKnowledgeService).insertAiLegalKnowledge(any());
    }

    @Test
    void edit_blankTitle_400() throws Exception
    {
        mockMvc.perform(put("/lawyers/knowledge")
                        .with(loginAs("lawyers:knowledge:edit"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledgeId\":1,\"title\":\"\",\"content\":\"正文内容\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void edit_validBody_200() throws Exception
    {
        when(aiLegalKnowledgeService.updateAiLegalKnowledge(any())).thenReturn(1);

        mockMvc.perform(put("/lawyers/knowledge")
                        .with(loginAs("lawyers:knowledge:edit"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledgeId\":1,\"title\":\"知识标题\",\"content\":\"正文内容\"}"))
                .andExpect(status().isOk());

        verify(aiLegalKnowledgeService).updateAiLegalKnowledge(any());
    }
}
