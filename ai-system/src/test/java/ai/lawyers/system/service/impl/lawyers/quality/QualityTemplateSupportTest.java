package ai.lawyers.system.service.impl.lawyers.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.quality.AiQualityTemplate;
import ai.lawyers.system.mapper.lawyers.quality.AiQualityTemplateMapper;
import ai.lawyers.system.service.impl.lawyers.quality.QualityTemplateSupport.ActiveTemplate;
import ai.lawyers.system.service.impl.lawyers.quality.QualityTemplateSupport.Dimension;

/**
 * P1-7：质检模板支撑单测——维度解析、加权总分、生效加载。
 *
 * @author ai-lawyers
 */
class QualityTemplateSupportTest
{
    private QualityTemplateSupport support;

    @BeforeEach
    void setUp()
    {
        support = new QualityTemplateSupport();
    }

    @Test
    void parseDimensions_validFour_allKept()
    {
        List<Dimension> dims = support.parseDimensions(
                "[{\"key\":\"serviceNorm\",\"weight\":1.0},{\"key\":\"answerAccuracy\",\"weight\":2.0},"
                + "{\"key\":\"emotionAttitude\",\"weight\":1.0},{\"key\":\"compliance\",\"weight\":1.0}]");
        assertEquals(4, dims.size());
        assertEquals("answerAccuracy", dims.get(1).key);
        assertEquals(2.0, dims.get(1).weight, 0.001);
    }

    @Test
    void parseDimensions_unknownAndDupAndBadWeight_skipped()
    {
        List<Dimension> dims = support.parseDimensions(
                "[{\"key\":\"unknownKey\",\"weight\":1.0},"
                + "{\"key\":\"serviceNorm\",\"weight\":0},"
                + "{\"key\":\"compliance\",\"weight\":1.0},"
                + "{\"key\":\"compliance\",\"weight\":1.0}]");
        assertEquals(1, dims.size());
        assertEquals("compliance", dims.get(0).key);
    }

    @Test
    void parseDimensions_badJson_empty()
    {
        assertEquals(0, support.parseDimensions("not json").size());
        assertEquals(0, support.parseDimensions(null).size());
    }

    @Test
    void weightedTotal_weightsRenormalizedOverPresent() throws Exception
    {
        ActiveTemplate template = new ActiveTemplate(1L,
                support.parseDimensions(
                        "[{\"key\":\"serviceNorm\",\"weight\":3.0},{\"key\":\"compliance\",\"weight\":1.0}]"),
                null);
        com.fasterxml.jackson.databind.node.ObjectNode scores =
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        scores.put("serviceNorm", 80d);
        scores.put("compliance", 100d);
        // (80*3 + 100*1) / 4 = 85
        assertEquals(85d, support.weightedTotal(template, scores), 0.001);

        // compliance 缺失 → 仅按 serviceNorm 计
        com.fasterxml.jackson.databind.node.ObjectNode partial =
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        partial.put("serviceNorm", 60d);
        partial.putNull("compliance");
        assertEquals(60d, support.weightedTotal(template, partial), 0.001);
    }

    @Test
    void loadActive_templateFound_resolved()
    {
        ReflectionTestUtils.setField(support, "templateMapper", fakeMapper(
                "1", "[{\"key\":\"serviceNorm\",\"weight\":1.0},{\"key\":\"compliance\",\"weight\":1.0}]",
                "custom prompt"));
        ActiveTemplate template = support.loadActive();
        assertEquals(2, template.dimensions.size());
        assertEquals("custom prompt", template.scorePrompt);
        assertTrue(template.hasKey("serviceNorm"));
    }

    @Test
    void loadActive_none_null()
    {
        ReflectionTestUtils.setField(support, "templateMapper", fakeMapper(null, null, null));
        assertNull(support.loadActive());
    }

    @Test
    void validate_blankNameOrDims_rejected()
    {
        AiQualityTemplate t = new AiQualityTemplate();
        assertThrows(IllegalArgumentException.class, () -> support.validate(t));
        t.setTemplateName("模板A");
        t.setDimensions("bad");
        assertThrows(IllegalArgumentException.class, () -> support.validate(t));
    }

    /** 构造返回固定生效模板的 fake mapper */
    private AiQualityTemplateMapper fakeMapper(String isDefault, String dimensions, String prompt)
    {
        AiQualityTemplate active = isDefault == null ? null : new AiQualityTemplate();
        if (active != null)
        {
            active.setTemplateId(7L);
            active.setIsDefault(isDefault);
            active.setDimensions(dimensions);
            active.setScorePrompt(prompt);
        }
        return new AiQualityTemplateMapper()
        {
            @Override
            public List<AiQualityTemplate> selectTemplateList(AiQualityTemplate query)
            {
                return Collections.emptyList();
            }

            @Override
            public AiQualityTemplate selectActiveTemplate() { return active; }

            @Override
            public AiQualityTemplate selectTemplateById(Long templateId) { return null; }

            @Override
            public int insertTemplate(AiQualityTemplate template) { return 0; }

            @Override
            public int updateTemplate(AiQualityTemplate template) { return 0; }

            @Override
            public int clearDefault() { return 0; }
        };
    }
}
