package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 用户评价实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiUserEvaluationTest
{
    @Test
    void 属性读写()
    {
        AiUserEvaluation evaluation = new AiUserEvaluation();
        evaluation.setEvaluationId(1L);
        evaluation.setConsultationId(2L);
        evaluation.setUserId(3L);
        evaluation.setOverallRating(5);
        evaluation.setProfessionalismRating(4);
        evaluation.setResponsivenessRating(5);
        evaluation.setQualityRating(4);
        evaluation.setFeedback("解答专业及时");

        assertEquals(1L, evaluation.getEvaluationId());
        assertEquals(2L, evaluation.getConsultationId());
        assertEquals(3L, evaluation.getUserId());
        assertEquals(5, evaluation.getOverallRating());
        assertEquals(4, evaluation.getProfessionalismRating());
        assertEquals(5, evaluation.getResponsivenessRating());
        assertEquals(4, evaluation.getQualityRating());
        assertEquals("解答专业及时", evaluation.getFeedback());
    }

    @Test
    void 默认值为空()
    {
        AiUserEvaluation evaluation = new AiUserEvaluation();

        assertNull(evaluation.getEvaluationId());
        assertNull(evaluation.getConsultationId());
        assertNull(evaluation.getOverallRating());
        assertNull(evaluation.getQualityRating());
        assertNull(evaluation.getFeedback());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        AiUserEvaluation evaluation = new AiUserEvaluation();
        evaluation.setEvaluationId(1L);
        evaluation.setConsultationId(2L);
        evaluation.setOverallRating(5);
        evaluation.setFeedback("解答专业及时");

        String text = evaluation.toString();

        assertTrue(text.contains("overallRating"));
        assertTrue(text.contains("解答专业及时"));
        assertTrue(text.contains("evaluationId"));
    }
}
