package ai.lawyers.common.utils.ip;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ai.lawyers.common.config.RuoYiConfig;

/**
 * IP 地址解析测试：内网地址本地判定、地址开关关闭时返回未知、
 * 非法地址按内网处理、未知地址常量。
 *
 * @author ai-lawyers
 */
class AddressUtilsTest
{
    @BeforeEach
    void 关闭地址查询开关()
    {
        new RuoYiConfig().setAddressEnabled(false);
    }

    @Test
    void 内网IP直接返回内网标识()
    {
        assertEquals("内网IP", AddressUtils.getRealAddressByIP("127.0.0.1"));
        assertEquals("内网IP", AddressUtils.getRealAddressByIP("10.0.0.1"));
        assertEquals("内网IP", AddressUtils.getRealAddressByIP("192.168.1.1"));
        assertEquals("内网IP", AddressUtils.getRealAddressByIP("172.16.0.1"));
    }

    @Test
    void 非法IP按内网处理不发起查询()
    {
        assertEquals("内网IP", AddressUtils.getRealAddressByIP("not-an-ip"));
    }

    @Test
    void 地址查询关闭时外网IP返回未知()
    {
        assertEquals("XX XX", AddressUtils.getRealAddressByIP("8.8.8.8"));
        assertEquals(AddressUtils.UNKNOWN, AddressUtils.getRealAddressByIP("172.32.0.1"));
    }
}
