package ai.lawyers.system.domain.lawyers.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 呼叫行为风控评估记录实体测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AiCallRiskRecordTest
{
    @Test
    void 属性读写()
    {
        Date reviewTime = new Date();
        AiCallRiskRecord record = new AiCallRiskRecord();
        record.setRiskId(1L);
        record.setCallerNumberIndex("hmac-sm3-001");
        record.setMaskedNumber("138****5678");
        record.setWindowHours(24);
        record.setCallCount(10);
        record.setShortCount(6);
        record.setNightCount(3);
        record.setMissedCount(1);
        record.setRiskScore(85);
        record.setScoreDetail("频次10x+超短6y+夜间3z+未接1w");
        record.setRiskLevel("HIGH");
        record.setReviewStatus("1");
        record.setSuppressId(2L);
        record.setReviewBy("张三");
        record.setReviewTime(reviewTime);
        record.setReviewRemark("确认高频骚扰");
        record.setMaskedNumberLike("138****");

        assertEquals(1L, record.getRiskId());
        assertEquals("hmac-sm3-001", record.getCallerNumberIndex());
        assertEquals("138****5678", record.getMaskedNumber());
        assertEquals(24, record.getWindowHours());
        assertEquals(10, record.getCallCount());
        assertEquals(6, record.getShortCount());
        assertEquals(3, record.getNightCount());
        assertEquals(1, record.getMissedCount());
        assertEquals(85, record.getRiskScore());
        assertEquals("频次10x+超短6y+夜间3z+未接1w", record.getScoreDetail());
        assertEquals("HIGH", record.getRiskLevel());
        assertEquals("1", record.getReviewStatus());
        assertEquals(2L, record.getSuppressId());
        assertEquals("张三", record.getReviewBy());
        assertEquals(reviewTime, record.getReviewTime());
        assertEquals("确认高频骚扰", record.getReviewRemark());
        assertEquals("138****", record.getMaskedNumberLike());
    }

    @Test
    void 默认值为空()
    {
        AiCallRiskRecord record = new AiCallRiskRecord();

        assertNull(record.getRiskId());
        assertNull(record.getCallerNumberIndex());
        assertNull(record.getRiskScore());
        assertNull(record.getRiskLevel());
        assertNull(record.getReviewStatus());
        assertNull(record.getReviewTime());
    }
}
