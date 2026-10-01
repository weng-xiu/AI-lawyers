package ai.lawyers.web.controller.lawyers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ai.lawyers.system.mapper.lawyers.quality.AiQualityTemplateMapper;
import ai.lawyers.system.service.impl.lawyers.quality.QualityTemplateSupport;
import ai.lawyers.system.service.lawyers.quality.IAiQualityInspectionService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link AiQualityInspectionController} 权限校验——
 * 列表 lawyers:quality:list、模板管理 lawyers:quality:review 两个权限边界分别验证。
 */
public class AiQualityInspectionControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiQualityInspectionService qualityInspectionService;

    @Mock
    private AiQualityTemplateMapper qualityTemplateMapper;

    @Mock
    private QualityTemplateSupport qualityTemplateSupport;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        startMvc(AiQualityInspectionController.class);
    }

    @Override
    protected void registerMocks()
    {
        registerMock("qualityInspectionService", qualityInspectionService);
        registerMock("qualityTemplateMapper", qualityTemplateMapper);
        registerMock("qualityTemplateSupport", qualityTemplateSupport);
    }

    @Test
    void list_unauthenticated_401() throws Exception
    {
        mockMvc.perform(get("/lawyers/quality/list"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_wrongPermission_403() throws Exception
    {
        // 有复核权限但无列表权限，仍应拒绝——验证权限码按端点精确区分
        mockMvc.perform(get("/lawyers/quality/list")
                        .with(loginAs("lawyers:quality:review")))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_withPermission_delegatesService() throws Exception
    {
        when(qualityInspectionService.selectInspectionList(any()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/lawyers/quality/list")
                        .with(loginAs("lawyers:quality:list")))
                .andExpect(status().isOk());

        verify(qualityInspectionService).selectInspectionList(any());
    }

    @Test
    void templateList_requiresReviewPermission() throws Exception
    {
        when(qualityTemplateMapper.selectTemplateList(any()))
                .thenReturn(Collections.emptyList());

        // list 权限不足以管理模板
        mockMvc.perform(get("/lawyers/quality/template/list")
                        .with(loginAs("lawyers:quality:list")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/lawyers/quality/template/list")
                        .with(loginAs("lawyers:quality:review")))
                .andExpect(status().isOk());

        verify(qualityTemplateMapper).selectTemplateList(any());
    }
}
