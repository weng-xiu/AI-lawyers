package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 有偿法律服务实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiPaidLegalServiceTest
{
    @Test
    void 属性读写()
    {
        AiPaidLegalService service = new AiPaidLegalService();
        service.setPaidId(1L);
        service.setServiceNo("PAID20261118001");
        service.setCustomerName("孙先生");
        service.setCustomerPhone("13822223333");
        service.setServiceType("诉讼代理");
        service.setAmount(5000.00D);
        service.setPaymentStatus("1");
        service.setServiceStatus("1");
        service.setHandlerName("周律师");
        service.setHandlerId(12L);

        assertEquals(1L, service.getPaidId());
        assertEquals("PAID20261118001", service.getServiceNo());
        assertEquals("孙先生", service.getCustomerName());
        assertEquals("13822223333", service.getCustomerPhone());
        assertEquals("诉讼代理", service.getServiceType());
        assertEquals(5000.00D, service.getAmount());
        assertEquals("1", service.getPaymentStatus());
        assertEquals("1", service.getServiceStatus());
        assertEquals("周律师", service.getHandlerName());
        assertEquals(12L, service.getHandlerId());
    }

    @Test
    void 默认值为空()
    {
        AiPaidLegalService service = new AiPaidLegalService();

        assertNull(service.getPaidId());
        assertNull(service.getServiceNo());
        assertNull(service.getCustomerName());
        assertNull(service.getCustomerPhone());
        assertNull(service.getServiceType());
        assertNull(service.getAmount());
        assertNull(service.getPaymentStatus());
        assertNull(service.getServiceStatus());
        assertNull(service.getHandlerName());
        assertNull(service.getHandlerId());
    }

    @Test
    void 字符串输出包含服务编号与客户姓名()
    {
        AiPaidLegalService service = new AiPaidLegalService();
        service.setServiceNo("PAID002");
        service.setCustomerName("钱女士");

        String text = service.toString();
        assertTrue(text.contains("PAID002"));
        assertTrue(text.contains("钱女士"));
    }
}
