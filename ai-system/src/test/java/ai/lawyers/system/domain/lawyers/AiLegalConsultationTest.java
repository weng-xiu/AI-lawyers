package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 法律咨询记录实体测试：属性读写、查询条件与字符串输出。
 *
 * @author ai-lawyers
 */
class AiLegalConsultationTest
{
    @Test
    void 属性读写()
    {
        AiLegalConsultation consultation = new AiLegalConsultation();
        consultation.setConsultationId(1L);
        consultation.setUserId(2L);
        consultation.setQuestion("试用期可以随时解除劳动合同吗");
        consultation.setCategoryId(3L);
        consultation.setAnswer("需符合法定条件");
        consultation.setConfidence(0.92D);
        consultation.setStatus("1");
        consultation.setConsultationChannel("2");
        consultation.setAttachmentPath("/upload/2026/11/a.pdf");
        consultation.setRating(5);
        consultation.setFeedback("回答专业");

        assertEquals(1L, consultation.getConsultationId());
        assertEquals(2L, consultation.getUserId());
        assertEquals("试用期可以随时解除劳动合同吗", consultation.getQuestion());
        assertEquals(3L, consultation.getCategoryId());
        assertEquals("需符合法定条件", consultation.getAnswer());
        assertEquals(0.92D, consultation.getConfidence());
        assertEquals("1", consultation.getStatus());
        assertEquals("2", consultation.getConsultationChannel());
        assertEquals("/upload/2026/11/a.pdf", consultation.getAttachmentPath());
        assertEquals(5, consultation.getRating());
        assertEquals("回答专业", consultation.getFeedback());
    }

    @Test
    void 查询条件属性读写()
    {
        AiLegalConsultation consultation = new AiLegalConsultation();
        consultation.setQuestionKeyword("劳动合同");
        consultation.setAnswerKeyword("补偿");
        consultation.setMinRating(3.0D);
        consultation.setMaxRating(5.0D);
        consultation.setCategoryName("劳动争议");

        assertEquals("劳动合同", consultation.getQuestionKeyword());
        assertEquals("补偿", consultation.getAnswerKeyword());
        assertEquals(3.0D, consultation.getMinRating());
        assertEquals(5.0D, consultation.getMaxRating());
        assertEquals("劳动争议", consultation.getCategoryName());
    }

    @Test
    void 默认值为空()
    {
        AiLegalConsultation consultation = new AiLegalConsultation();

        assertNull(consultation.getConsultationId());
        assertNull(consultation.getQuestion());
        assertNull(consultation.getAnswer());
        assertNull(consultation.getConfidence());
        assertNull(consultation.getRating());
        assertNull(consultation.getQuestionKeyword());
    }

    @Test
    void 字符串输出包含问题与状态()
    {
        AiLegalConsultation consultation = new AiLegalConsultation();
        consultation.setQuestion("离婚财产如何分割");
        consultation.setStatus("0");

        String text = consultation.toString();
        assertTrue(text.contains("离婚财产如何分割"));
        assertTrue(text.contains("0"));
    }
}
