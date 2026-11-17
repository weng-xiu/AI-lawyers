package ai.lawyers.system.domain.lawyers.trunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 线路告警实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiTrunkAlarmTest
{
    @Test
    void 属性读写()
    {
        Date alarmTime = new Date();
        Date recoverTime = new Date();
        Date handleTime = new Date();
        AiTrunkAlarm alarm = new AiTrunkAlarm();
        alarm.setAlarmId(1L);
        alarm.setTrunkId(2L);
        alarm.setTrunkCode("TRUNK-01");
        alarm.setCarrier("CM");
        alarm.setAlarmType("CONNECT_RATE_LOW");
        alarm.setAlarmLevel("3");
        alarm.setAlarmTitle("接通率偏低");
        alarm.setAlarmContent("近5分钟接通率低于阈值");
        alarm.setMetricValue("65");
        alarm.setThresholdValue("80");
        alarm.setAlarmStatus("1");
        alarm.setAlarmTime(alarmTime);
        alarm.setRecoverTime(recoverTime);
        alarm.setHandleBy("张三");
        alarm.setHandleTime(handleTime);
        alarm.setHandleRemark("已确认，等待恢复");

        assertEquals(1L, alarm.getAlarmId());
        assertEquals(2L, alarm.getTrunkId());
        assertEquals("TRUNK-01", alarm.getTrunkCode());
        assertEquals("CM", alarm.getCarrier());
        assertEquals("CONNECT_RATE_LOW", alarm.getAlarmType());
        assertEquals("3", alarm.getAlarmLevel());
        assertEquals("接通率偏低", alarm.getAlarmTitle());
        assertEquals("近5分钟接通率低于阈值", alarm.getAlarmContent());
        assertEquals("65", alarm.getMetricValue());
        assertEquals("80", alarm.getThresholdValue());
        assertEquals("1", alarm.getAlarmStatus());
        assertEquals(alarmTime, alarm.getAlarmTime());
        assertEquals(recoverTime, alarm.getRecoverTime());
        assertEquals("张三", alarm.getHandleBy());
        assertEquals(handleTime, alarm.getHandleTime());
        assertEquals("已确认，等待恢复", alarm.getHandleRemark());
    }

    @Test
    void 默认值为空()
    {
        AiTrunkAlarm alarm = new AiTrunkAlarm();

        assertNull(alarm.getAlarmId());
        assertNull(alarm.getTrunkCode());
        assertNull(alarm.getAlarmType());
        assertNull(alarm.getAlarmStatus());
        assertNull(alarm.getAlarmTime());
        assertNull(alarm.getRecoverTime());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        AiTrunkAlarm alarm = new AiTrunkAlarm();
        alarm.setAlarmId(1L);
        alarm.setTrunkCode("TRUNK-01");
        alarm.setAlarmType("TRUNK_DOWN");
        alarm.setAlarmTitle("线路中断");

        String text = alarm.toString();

        assertTrue(text.contains("TRUNK-01"));
        assertTrue(text.contains("TRUNK_DOWN"));
        assertTrue(text.contains("alarmTitle"));
    }
}
