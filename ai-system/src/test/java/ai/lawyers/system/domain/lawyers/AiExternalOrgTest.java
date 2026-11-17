package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 协同外部机构实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiExternalOrgTest
{
    @Test
    void 属性读写()
    {
        AiExternalOrg org = new AiExternalOrg();
        org.setOrgId(1L);
        org.setOrgName("市法律援助中心");
        org.setExternalType("LEGAL_AID");
        org.setProvince("浙江省");
        org.setCity("杭州市");
        org.setDistrict("西湖区");
        org.setAddress("文三路 100 号");
        org.setContactPhone("0571-88888888");
        org.setContactPerson("陈主任");
        org.setAccessMode("API");
        org.setApiUrl("https://example.com/api");
        org.setAppId("app-001");
        org.setAppSecret("secret-001");
        org.setServiceHours("工作日 9:00-17:00");
        org.setApplyMaterials("身份证、经济困难证明");
        org.setStatus("0");

        assertEquals(1L, org.getOrgId());
        assertEquals("市法律援助中心", org.getOrgName());
        assertEquals("LEGAL_AID", org.getExternalType());
        assertEquals("浙江省", org.getProvince());
        assertEquals("杭州市", org.getCity());
        assertEquals("西湖区", org.getDistrict());
        assertEquals("文三路 100 号", org.getAddress());
        assertEquals("0571-88888888", org.getContactPhone());
        assertEquals("陈主任", org.getContactPerson());
        assertEquals("API", org.getAccessMode());
        assertEquals("https://example.com/api", org.getApiUrl());
        assertEquals("app-001", org.getAppId());
        assertEquals("secret-001", org.getAppSecret());
        assertEquals("工作日 9:00-17:00", org.getServiceHours());
        assertEquals("身份证、经济困难证明", org.getApplyMaterials());
        assertEquals("0", org.getStatus());
    }

    @Test
    void 默认值为空()
    {
        AiExternalOrg org = new AiExternalOrg();

        assertNull(org.getOrgId());
        assertNull(org.getOrgName());
        assertNull(org.getExternalType());
        assertNull(org.getProvince());
        assertNull(org.getCity());
        assertNull(org.getDistrict());
        assertNull(org.getAddress());
        assertNull(org.getContactPhone());
        assertNull(org.getContactPerson());
        assertNull(org.getAccessMode());
        assertNull(org.getApiUrl());
        assertNull(org.getAppId());
        assertNull(org.getAppSecret());
        assertNull(org.getServiceHours());
        assertNull(org.getApplyMaterials());
        assertNull(org.getStatus());
    }

    @Test
    void 字符串输出包含机构名称与条线类型()
    {
        AiExternalOrg org = new AiExternalOrg();
        org.setOrgName("人民调解委员会");
        org.setExternalType("MEDIATION");

        String text = org.toString();
        assertTrue(text.contains("人民调解委员会"));
        assertTrue(text.contains("MEDIATION"));
    }
}
