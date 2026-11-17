package ai.lawyers.common.utils.ip;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;

/**
 * IP 工具测试：请求头解析、内网判定、地址转换、通配与网段匹配。
 *
 * @author ai-lawyers
 */
class IpUtilsTest
{
    @Test
    void getIpAddr_请求为空返回unknown()
    {
        assertEquals("unknown", IpUtils.getIpAddr(null));
    }

    @Test
    void getIpAddr_按优先级读取代理请求头()
    {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-forwarded-for", "1.1.1.1");
        headers.put("Proxy-Client-IP", "2.2.2.2");
        headers.put("X-Forwarded-For", "3.3.3.3");
        headers.put("WL-Proxy-Client-IP", "4.4.4.4");
        headers.put("X-Real-IP", "5.5.5.5");

        assertEquals("1.1.1.1", IpUtils.getIpAddr(request(headers, "9.9.9.9")));

        headers.remove("x-forwarded-for");
        assertEquals("2.2.2.2", IpUtils.getIpAddr(request(headers, "9.9.9.9")));

        headers.remove("Proxy-Client-IP");
        assertEquals("3.3.3.3", IpUtils.getIpAddr(request(headers, "9.9.9.9")));

        headers.remove("X-Forwarded-For");
        assertEquals("4.4.4.4", IpUtils.getIpAddr(request(headers, "9.9.9.9")));

        headers.remove("WL-Proxy-Client-IP");
        assertEquals("5.5.5.5", IpUtils.getIpAddr(request(headers, "9.9.9.9")));
    }

    @Test
    void getIpAddr_无请求头时回退远程地址()
    {
        assertEquals("9.9.9.9", IpUtils.getIpAddr(request(new HashMap<>(), "9.9.9.9")));

        // 未知请求头同样回退远程地址
        Map<String, String> headers = new HashMap<>();
        headers.put("x-forwarded-for", "unknown");
        assertEquals("9.9.9.9", IpUtils.getIpAddr(request(headers, "9.9.9.9")));
    }

    @Test
    void getIpAddr_IPv6回环地址转换为IPv4()
    {
        assertEquals("127.0.0.1", IpUtils.getIpAddr(request(new HashMap<>(), "0:0:0:0:0:0:0:1")));
    }

    @Test
    void getIpAddr_多级代理取第一个有效地址()
    {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-forwarded-for", "unknown,10.0.0.2,10.0.0.3");

        assertEquals("10.0.0.2", IpUtils.getIpAddr(request(headers, "9.9.9.9")));
    }

    @Test
    void internalIp_常见内网网段()
    {
        assertTrue(IpUtils.internalIp("10.1.2.3"));
        assertTrue(IpUtils.internalIp("172.16.0.1"));
        assertTrue(IpUtils.internalIp("172.31.255.255"));
        assertTrue(IpUtils.internalIp("192.168.1.1"));
        assertTrue(IpUtils.internalIp("127.0.0.1"));

        assertFalse(IpUtils.internalIp("172.15.0.1"));
        assertFalse(IpUtils.internalIp("172.32.0.1"));
        assertFalse(IpUtils.internalIp("192.169.1.1"));
        assertFalse(IpUtils.internalIp("8.8.8.8"));
    }

    @Test
    void internalIp_无法解析的地址按内网处理()
    {
        // 解析失败返回 null，内部方法将 null 视为内网
        assertTrue(IpUtils.internalIp("not-an-ip"));
        assertTrue(IpUtils.internalIp(""));
    }

    @Test
    void textToNumericFormatV4_标准四段地址()
    {
        assertArrayEquals(new byte[] { (byte) 192, (byte) 168, 1, 1 },
                IpUtils.textToNumericFormatV4("192.168.1.1"));
    }

    @Test
    void textToNumericFormatV4_简写形式()
    {
        assertArrayEquals(new byte[] { (byte) 192, (byte) 168, 1, 1 },
                IpUtils.textToNumericFormatV4("3232235777"));
        assertArrayEquals(new byte[] { (byte) 192, (byte) 168, 1, 1 },
                IpUtils.textToNumericFormatV4("192.168.257"));
        assertArrayEquals(new byte[] { (byte) 192, (byte) 168, 1, 1 },
                IpUtils.textToNumericFormatV4("192.11010305"));
    }

    @Test
    void textToNumericFormatV4_非法地址返回null()
    {
        assertNull(IpUtils.textToNumericFormatV4(""));
        assertNull(IpUtils.textToNumericFormatV4("256.1.1.1"));
        assertNull(IpUtils.textToNumericFormatV4("1.2.3.4.5"));
        assertNull(IpUtils.textToNumericFormatV4("1.2.3.a"));
        assertNull(IpUtils.textToNumericFormatV4("4294967296"));
    }

    @Test
    void isIP与isIpWildCard与isIPSegment()
    {
        assertTrue(IpUtils.isIP("192.168.1.1"));
        assertFalse(IpUtils.isIP("192.168.1.256"));
        assertFalse(IpUtils.isIP(""));
        assertFalse(IpUtils.isIP(null));

        assertTrue(IpUtils.isIpWildCard("192.168.*.*"));
        assertTrue(IpUtils.isIpWildCard("192.*.*.*"));
        assertTrue(IpUtils.isIpWildCard("1.2.3.*"));
        assertTrue(IpUtils.isIpWildCard("*.*.*.*"));
        assertFalse(IpUtils.isIpWildCard("192.168.1.1"));

        assertTrue(IpUtils.isIPSegment("10.0.0.1-10.0.0.100"));
        assertFalse(IpUtils.isIPSegment("10.0.0.1"));
        assertFalse(IpUtils.isIPSegment(null));
    }

    @Test
    void ipIsInWildCardNoCheck_前缀匹配()
    {
        assertTrue(IpUtils.ipIsInWildCardNoCheck("192.168.*.*", "192.168.1.5"));
        assertTrue(IpUtils.ipIsInWildCardNoCheck("*.*.*.*", "8.8.8.8"));
        assertFalse(IpUtils.ipIsInWildCardNoCheck("192.168.*.*", "192.169.1.5"));
    }

    @Test
    void ipIsInNetNoCheck_网段包含与倒序区间()
    {
        assertTrue(IpUtils.ipIsInNetNoCheck("10.0.0.1-10.0.0.100", "10.0.0.50"));
        assertFalse(IpUtils.ipIsInNetNoCheck("10.0.0.1-10.0.0.100", "10.0.0.101"));
        // 起止地址倒序时自动交换
        assertTrue(IpUtils.ipIsInNetNoCheck("10.0.0.100-10.0.0.1", "10.0.0.50"));
    }

    @Test
    void isMatchedIp_支持单IP通配与网段()
    {
        String filter = "192.168.1.1;192.168.2.*;10.0.0.1-10.0.0.9";

        assertTrue(IpUtils.isMatchedIp(filter, "192.168.1.1"));
        assertTrue(IpUtils.isMatchedIp(filter, "192.168.2.55"));
        assertTrue(IpUtils.isMatchedIp(filter, "10.0.0.9"));
        assertFalse(IpUtils.isMatchedIp(filter, "10.0.0.10"));
        assertFalse(IpUtils.isMatchedIp("", "192.168.1.1"));
        assertFalse(IpUtils.isMatchedIp(filter, ""));
    }

    @Test
    void isUnknown_空白与unknown不区分大小写()
    {
        assertTrue(IpUtils.isUnknown(null));
        assertTrue(IpUtils.isUnknown(""));
        assertTrue(IpUtils.isUnknown("   "));
        assertTrue(IpUtils.isUnknown("unknown"));
        assertTrue(IpUtils.isUnknown("UNKNOWN"));

        assertFalse(IpUtils.isUnknown("127.0.0.1"));
    }

    @Test
    void getMultistageReverseProxyIp_单地址与空值()
    {
        assertEquals("1.1.1.1", IpUtils.getMultistageReverseProxyIp("1.1.1.1"));
        // 工具类对 null 统一返回空串
        assertEquals("", IpUtils.getMultistageReverseProxyIp(null));

        String longIp = "1".repeat(300);
        assertEquals(255, IpUtils.getMultistageReverseProxyIp(longIp).length());
    }

    @Test
    void getHostIp与getHostName_返回本地信息()
    {
        assertNotNull(IpUtils.getHostIp());
        assertNotNull(IpUtils.getHostName());
    }

    private static HttpServletRequest request(Map<String, String> headers, String remoteAddr)
    {
        return (HttpServletRequest) Proxy.newProxyInstance(IpUtilsTest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, (proxy, method, args) -> {
                    if ("getHeader".equals(method.getName()))
                    {
                        return headers.get(args[0]);
                    }
                    if ("getRemoteAddr".equals(method.getName()))
                    {
                        return remoteAddr;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
