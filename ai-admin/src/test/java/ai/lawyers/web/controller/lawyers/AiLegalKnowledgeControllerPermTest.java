package ai.lawyers.web.controller.lawyers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ai.lawyers.system.service.lawyers.KnowledgeDraftService;
import ai.lawyers.system.service.lawyers.rag.IRagIndexService;
import ai.lawyers.system.service.lawyers.rag.RagSearchService;
import ai.lawyers.system.service.lawyers.rag.VectorIndexRouter;
import ai.lawyers.system.service.lawyers.IAiLegalKnowledgeService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link AiLegalKnowledgeController} 权限校验——
 * 列表 lawyers:knowledge:list、知识沉淀 lawyers:knowledge:add 两个权限边界分别验证。
 */
public class AiLegalKnowledgeControllerPermTest extends AbstractPermMvcTest
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
    }

    @Test
    void list_unauthenticated_401() throws Exception
    {
        mockMvc.perform(get("/lawyers/knowledge/list"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_wrongPermission_403() throws Exception
    {
        mockMvc.perform(get("/lawyers/knowledge/list")
                        .with(loginAs("lawyers:knowledge:add")))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_withPermission_delegatesService() throws Exception
    {
        when(aiLegalKnowledgeService.selectAiLegalKnowledgeList(any()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/lawyers/knowledge/list")
                        .with(loginAs("lawyers:knowledge:list")))
                .andExpect(status().isOk());

        verify(aiLegalKnowledgeService).selectAiLegalKnowledgeList(any());
    }

    @Test
    void draftFromCall_requiresAddPermission() throws Exception
    {
        when(knowledgeDraftService.draftFromCall(1L)).thenReturn(1L);

        mockMvc.perform(post("/lawyers/knowledge/draft/call/1")
                        .with(loginAs("lawyers:knowledge:list")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/lawyers/knowledge/draft/call/1")
                        .with(loginAs("lawyers:knowledge:add")))
                .andExpect(status().isOk());

        verify(knowledgeDraftService).draftFromCall(1L);
    }
}
