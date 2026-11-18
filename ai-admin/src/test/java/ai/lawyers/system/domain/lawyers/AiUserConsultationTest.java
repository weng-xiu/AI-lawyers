package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 用户咨询实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiUserConsultationTest
{
    @Test
    void 属性读写()
    {
        Date processTime = new Date();
        AiUserConsultation consultation = new AiUserConsultation();
        consultation.setConsultationId(1L);
        consultation.setUserId(2L);
        consultation.setCategory("劳动争议");
        consultation.setContent("公司拖欠工资如何维权");
        consultation.setAttachments("/upload/1.pdf");
        consultation.setAiAnswer("可申请劳动仲裁");
        consultation.setRelatedLaws("劳动合同法第30条");
        consultation.setRelatedCases("（2023）京01民终123号");
        consultation.setConfidence(0.92D);
        consultation.setStatus("COMPLETED");
        consultation.setChannelType("WECHAT_MP");
        consultation.setProcessTime(processTime);

        assertEquals(1L, consultation.getConsultationId());
        assertEquals(2L, consultation.getUserId());
        assertEquals("劳动争议", consultation.getCategory());
        assertEquals("公司拖欠工资如何维权", consultation.getContent());
        assertEquals("/upload/1.pdf", consultation.getAttachments());
        assertEquals("可申请劳动仲裁", consultation.getAiAnswer());
        assertEquals("劳动合同法第30条", consultation.getRelatedLaws());
        assertEquals("（2023）京01民终123号", consultation.getRelatedCases());
        assertEquals(0.92D, consultation.getConfidence().doubleValue());
        assertEquals("COMPLETED", consultation.getStatus());
        assertEquals("WECHAT_MP", consultation.getChannelType());
        assertEquals(processTime, consultation.getProcessTime());
    }

    @Test
    void 默认值为空()
    {
        AiUserConsultation consultation = new AiUserConsultation();

        assertNull(consultation.getConsultationId());
        assertNull(consultation.getUserId());
        assertNull(consultation.getCategory());
        assertNull(consultation.getConfidence());
        assertNull(consultation.getStatus());
        assertNull(consultation.getChannelType());
        assertNull(consultation.getProcessTime());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        AiUserConsultation consultation = new AiUserConsultation();
        consultation.setConsultationId(1L);
        consultation.setCategory("劳动争议");
        consultation.setContent("公司拖欠工资如何维权");
        consultation.setStatus("COMPLETED");

        String text = consultation.toString();

        assertTrue(text.contains("劳动争议"));
        assertTrue(text.contains("COMPLETED"));
        assertTrue(text.contains("consultationId"));
    }
}
