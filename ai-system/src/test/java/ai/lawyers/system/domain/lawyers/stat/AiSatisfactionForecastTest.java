package ai.lawyers.system.domain.lawyers.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 预测性满意度行测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AiSatisfactionForecastTest
{
    @Test
    void 属性读写()
    {
        Date callTime = new Date();
        AiSatisfactionForecast forecast = new AiSatisfactionForecast();
        forecast.setRecordId(1L);
        forecast.setCallTime(callTime);
        forecast.setDuration(120);
        forecast.setStatus("1");
        forecast.setEmotion("NEGATIVE");
        forecast.setQcTotal(85.5D);
        forecast.setQcEmotion(80.0D);
        forecast.setPredicted(3);
        forecast.setScoreDetail("情绪负面扣分");
        forecast.setActual(4);

        assertEquals(1L, forecast.getRecordId());
        assertEquals(callTime, forecast.getCallTime());
        assertEquals(120, forecast.getDuration());
        assertEquals("1", forecast.getStatus());
        assertEquals("NEGATIVE", forecast.getEmotion());
        assertEquals(85.5D, forecast.getQcTotal().doubleValue());
        assertEquals(80.0D, forecast.getQcEmotion().doubleValue());
        assertEquals(3, forecast.getPredicted());
        assertEquals("情绪负面扣分", forecast.getScoreDetail());
        assertEquals(4, forecast.getActual());
    }

    @Test
    void 默认值为空()
    {
        AiSatisfactionForecast forecast = new AiSatisfactionForecast();

        assertNull(forecast.getRecordId());
        assertNull(forecast.getCallTime());
        assertNull(forecast.getQcTotal());
        assertNull(forecast.getQcEmotion());
        assertNull(forecast.getPredicted());
        assertNull(forecast.getActual());
    }
}
