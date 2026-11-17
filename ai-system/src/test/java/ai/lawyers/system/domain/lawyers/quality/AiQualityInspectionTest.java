package ai.lawyers.system.domain.lawyers.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 智能质检记录实体测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AiQualityInspectionTest
{
    @Test
    void 属性读写()
    {
        Date reviewTime = new Date();
        Date appealTime = new Date();
        AiQualityInspection inspection = new AiQualityInspection();
        inspection.setInspectionId(1L);
        inspection.setSourceType("1");
        inspection.setRecordId(2L);
        inspection.setSessionId("sess-001");
        inspection.setAgentId(3L);
        inspection.setCallerNumber("13800000000");
        inspection.setTranscript("您好，请问有什么可以帮您");
        inspection.setTotalScore(new BigDecimal("85.50"));
        inspection.setDimensionJson("{\"service\":90}");
        inspection.setViolationJson("[]");
        inspection.setAiStatus("2");
        inspection.setAiRemark("整体表现良好");
        inspection.setReviewStatus("1");
        inspection.setReviewerId(4L);
        inspection.setReviewerName("李四");
        inspection.setReviewRemark("同意AI结论");
        inspection.setReviewTime(reviewTime);
        inspection.setRiskWarningId(5L);
        inspection.setAppealStatus("2");
        inspection.setAppealReason("评分偏低");
        inspection.setAppealTime(appealTime);
        inspection.setAppealReviewRemark("维持原判");
        inspection.setAdjustedScore(new BigDecimal("88.00"));
        inspection.setAgentName("张三");

        assertEquals(1L, inspection.getInspectionId());
        assertEquals("1", inspection.getSourceType());
        assertEquals(2L, inspection.getRecordId());
        assertEquals("sess-001", inspection.getSessionId());
        assertEquals(3L, inspection.getAgentId());
        assertEquals("13800000000", inspection.getCallerNumber());
        assertEquals("您好，请问有什么可以帮您", inspection.getTranscript());
        assertEquals(new BigDecimal("85.50"), inspection.getTotalScore());
        assertEquals("{\"service\":90}", inspection.getDimensionJson());
        assertEquals("[]", inspection.getViolationJson());
        assertEquals("2", inspection.getAiStatus());
        assertEquals("整体表现良好", inspection.getAiRemark());
        assertEquals("1", inspection.getReviewStatus());
        assertEquals(4L, inspection.getReviewerId());
        assertEquals("李四", inspection.getReviewerName());
        assertEquals("同意AI结论", inspection.getReviewRemark());
        assertEquals(reviewTime, inspection.getReviewTime());
        assertEquals(5L, inspection.getRiskWarningId());
        assertEquals("2", inspection.getAppealStatus());
        assertEquals("评分偏低", inspection.getAppealReason());
        assertEquals(appealTime, inspection.getAppealTime());
        assertEquals("维持原判", inspection.getAppealReviewRemark());
        assertEquals(new BigDecimal("88.00"), inspection.getAdjustedScore());
        assertEquals("张三", inspection.getAgentName());
    }

    @Test
    void 默认值为空()
    {
        AiQualityInspection inspection = new AiQualityInspection();

        assertNull(inspection.getInspectionId());
        assertNull(inspection.getTotalScore());
        assertNull(inspection.getAiStatus());
        assertNull(inspection.getReviewStatus());
        assertNull(inspection.getAppealStatus());
        assertNull(inspection.getAdjustedScore());
    }
}
