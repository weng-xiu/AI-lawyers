package ai.lawyers.system.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 运营商枚举测试：编码解析、忽略大小写、空值与未知兜底。
 *
 * @author ai-lawyers
 */
class CarrierEnumTest
{
    @Test
    void fromCode_按编码解析()
    {
        assertSame(CarrierEnum.CHINA_MOBILE, CarrierEnum.fromCode("CM"));
        assertSame(CarrierEnum.CHINA_UNICOM, CarrierEnum.fromCode("cu"));
        assertSame(CarrierEnum.CHINA_TELECOM, CarrierEnum.fromCode("CT"));
        assertSame(CarrierEnum.CHINA_BROADNET, CarrierEnum.fromCode("cb"));
        assertSame(CarrierEnum.VIRTUAL, CarrierEnum.fromCode("VI"));
        assertSame(CarrierEnum.UNKNOWN, CarrierEnum.fromCode("00"));
    }

    @Test
    void fromCode_空值与未知编码回退UNKNOWN()
    {
        assertSame(CarrierEnum.UNKNOWN, CarrierEnum.fromCode(null));
        assertSame(CarrierEnum.UNKNOWN, CarrierEnum.fromCode(""));
        assertSame(CarrierEnum.UNKNOWN, CarrierEnum.fromCode("XX"));
    }

    @Test
    void infoOf_返回中文名称()
    {
        assertEquals("中国移动", CarrierEnum.infoOf("CM"));
        assertEquals("虚拟运营商", CarrierEnum.infoOf("VI"));
        assertEquals("未知", CarrierEnum.infoOf("XX"));
        assertEquals("未知", CarrierEnum.infoOf(null));
    }

    @Test
    void 编码与名称均不重复()
    {
        Set<String> codes = new HashSet<>();
        Set<String> infos = new HashSet<>();
        for (CarrierEnum carrier : CarrierEnum.values())
        {
            codes.add(carrier.getCode());
            infos.add(carrier.getInfo());
        }
        assertEquals(CarrierEnum.values().length, codes.size());
        assertEquals(CarrierEnum.values().length, infos.size());
    }
}
