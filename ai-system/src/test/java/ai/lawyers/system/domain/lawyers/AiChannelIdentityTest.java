package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 多渠道身份绑定实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiChannelIdentityTest
{
    @Test
    void 属性读写()
    {
        Date bindTime = new Date();
        Date unbindTime = new Date(bindTime.getTime() + 3600000L);
        AiChannelIdentity identity = new AiChannelIdentity();
        identity.setId(1L);
        identity.setProfileId(9L);
        identity.setChannelType("WECHAT_MINI");
        identity.setChannelUid("openid-abc");
        identity.setChannelNickname("小李");
        identity.setBindStatus("0");
        identity.setBindConfirmTime(bindTime);
        identity.setUnbindTime(unbindTime);

        assertEquals(1L, identity.getId());
        assertEquals(9L, identity.getProfileId());
        assertEquals("WECHAT_MINI", identity.getChannelType());
        assertEquals("openid-abc", identity.getChannelUid());
        assertEquals("小李", identity.getChannelNickname());
        assertEquals("0", identity.getBindStatus());
        assertEquals(bindTime, identity.getBindConfirmTime());
        assertEquals(unbindTime, identity.getUnbindTime());
    }

    @Test
    void 默认值为空()
    {
        AiChannelIdentity identity = new AiChannelIdentity();

        assertNull(identity.getId());
        assertNull(identity.getProfileId());
        assertNull(identity.getChannelType());
        assertNull(identity.getChannelUid());
        assertNull(identity.getChannelNickname());
        assertNull(identity.getBindStatus());
        assertNull(identity.getBindConfirmTime());
        assertNull(identity.getUnbindTime());
    }

    @Test
    void 字符串输出包含渠道类型与绑定状态()
    {
        AiChannelIdentity identity = new AiChannelIdentity();
        identity.setChannelType("PHONE");
        identity.setChannelUid("13800000000");

        String text = identity.toString();
        assertTrue(text.contains("PHONE"));
        assertTrue(text.contains("13800000000"));
    }
}
