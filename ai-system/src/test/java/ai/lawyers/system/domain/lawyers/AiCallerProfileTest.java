package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 来电人档案实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiCallerProfileTest
{
    @Test
    void 属性读写()
    {
        Date lastCallTime = new Date();
        AiCallerProfile profile = new AiCallerProfile();
        profile.setProfileId(1L);
        profile.setCallerNumber("13900000000");
        profile.setCallerName("王五");
        profile.setCallerGender("0");
        profile.setCallerAge(35);
        profile.setCallerIdCard("110101199001010000");
        profile.setCallerAddress("北京市东城区");
        profile.setCustomerLevel("VIP");
        profile.setTags("劳动纠纷,高频来电");

        assertEquals(1L, profile.getProfileId());
        assertEquals("13900000000", profile.getCallerNumber());
        assertEquals("王五", profile.getCallerName());
        assertEquals("0", profile.getCallerGender());
        assertEquals(35, profile.getCallerAge());
        assertEquals("110101199001010000", profile.getCallerIdCard());
        assertEquals("北京市东城区", profile.getCallerAddress());
        assertEquals("VIP", profile.getCustomerLevel());
        assertEquals("劳动纠纷,高频来电", profile.getTags());
    }

    @Test
    void 意图与情绪字段读写()
    {
        Date lastCallTime = new Date();
        AiCallerProfile profile = new AiCallerProfile();
        profile.setIntentPrediction("劳动仲裁");
        profile.setIntentConfidence(88);
        profile.setConsultPreference("电话");
        profile.setHighFreqProblem("拖欠工资");
        profile.setFreqMentionCount(5);
        profile.setRiskLevel("1");
        profile.setEmotionStatus("焦虑");
        profile.setEmotionWarning("情绪波动较大");
        profile.setLastCallTime(lastCallTime);
        profile.setLanguagePreference("yue-CN");
        profile.setCareMode(1);
        profile.setCallerNumberIndex("idx-number");
        profile.setCallerIdCardIndex("idx-idcard");

        assertEquals("劳动仲裁", profile.getIntentPrediction());
        assertEquals(88, profile.getIntentConfidence());
        assertEquals("电话", profile.getConsultPreference());
        assertEquals("拖欠工资", profile.getHighFreqProblem());
        assertEquals(5, profile.getFreqMentionCount());
        assertEquals("1", profile.getRiskLevel());
        assertEquals("焦虑", profile.getEmotionStatus());
        assertEquals("情绪波动较大", profile.getEmotionWarning());
        assertEquals(lastCallTime, profile.getLastCallTime());
        assertEquals("yue-CN", profile.getLanguagePreference());
        assertEquals(1, profile.getCareMode());
        assertEquals("idx-number", profile.getCallerNumberIndex());
        assertEquals("idx-idcard", profile.getCallerIdCardIndex());
    }

    @Test
    void 默认值为空()
    {
        AiCallerProfile profile = new AiCallerProfile();

        assertNull(profile.getProfileId());
        assertNull(profile.getCallerNumber());
        assertNull(profile.getCallerName());
        assertNull(profile.getIntentConfidence());
        assertNull(profile.getRiskLevel());
        assertNull(profile.getCareMode());
        assertNull(profile.getTokenGroups());
    }

    @Test
    void 字符串输出包含姓名与风险等级()
    {
        AiCallerProfile profile = new AiCallerProfile();
        profile.setCallerName("赵六");
        profile.setRiskLevel("2");

        String text = profile.toString();
        assertTrue(text.contains("赵六"));
        assertTrue(text.contains("2"));
    }
}
