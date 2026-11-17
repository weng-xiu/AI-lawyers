package ai.lawyers.framework.websocket.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * PBX 媒体接入配置测试：未注入配置时各字段为初始值。
 *
 * @author ai-lawyers
 */
class PbxMediaForkPropertiesTest
{
    @Test
    void 未注入配置_开关关闭且无密钥()
    {
        PbxMediaForkProperties properties = new PbxMediaForkProperties();

        assertFalse(properties.isEnabled());
        assertFalse(properties.isDownlinkEnabled());
        assertEquals(0, properties.getDefaultRate());
        assertEquals(0, properties.getDownlinkBufferMs());
        assertNull(properties.getAuthKey());
    }
}
