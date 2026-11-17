package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 工单跨域转办流水实体测试：属性读写、幂等字段与字符串输出。
 *
 * @author ai-lawyers
 */
class AiTicketTransferTest
{
    @Test
    void 属性读写()
    {
        Date transferTime = new Date();
        Date callbackTime = new Date();
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTransferId(1L);
        transfer.setTicketId(2L);
        transfer.setTicketNo("TK20261118001");
        transfer.setDirection("OUT");
        transfer.setExternalType("LEGAL_AID");
        transfer.setOrgId(3L);
        transfer.setOrgName("市法律援助中心");
        transfer.setExternalTicketNo("EXT-001");
        transfer.setExternalStatus("ACCEPTED");
        transfer.setTransferStatus("1");
        transfer.setRequestPayload("{\"ticketNo\":\"TK20261118001\"}");
        transfer.setCallbackPayload("{\"status\":\"ACCEPTED\"}");
        transfer.setIdempotentKey("idem-001");
        transfer.setFailReason("网络超时");
        transfer.setRetryCount(2);
        transfer.setNextRetryTime(transferTime);
        transfer.setTransferTime(transferTime);
        transfer.setCallbackTime(callbackTime);

        assertEquals(1L, transfer.getTransferId());
        assertEquals(2L, transfer.getTicketId());
        assertEquals("TK20261118001", transfer.getTicketNo());
        assertEquals("OUT", transfer.getDirection());
        assertEquals("LEGAL_AID", transfer.getExternalType());
        assertEquals(3L, transfer.getOrgId());
        assertEquals("市法律援助中心", transfer.getOrgName());
        assertEquals("EXT-001", transfer.getExternalTicketNo());
        assertEquals("ACCEPTED", transfer.getExternalStatus());
        assertEquals("1", transfer.getTransferStatus());
        assertEquals("{\"ticketNo\":\"TK20261118001\"}", transfer.getRequestPayload());
        assertEquals("{\"status\":\"ACCEPTED\"}", transfer.getCallbackPayload());
        assertEquals("idem-001", transfer.getIdempotentKey());
        assertEquals("网络超时", transfer.getFailReason());
        assertEquals(2, transfer.getRetryCount());
        assertEquals(transferTime, transfer.getNextRetryTime());
        assertEquals(transferTime, transfer.getTransferTime());
        assertEquals(callbackTime, transfer.getCallbackTime());
    }

    @Test
    void 默认值为空()
    {
        AiTicketTransfer transfer = new AiTicketTransfer();

        assertNull(transfer.getTransferId());
        assertNull(transfer.getTicketNo());
        assertNull(transfer.getDirection());
        assertNull(transfer.getTransferStatus());
        assertNull(transfer.getIdempotentKey());
        assertNull(transfer.getRetryCount());
        assertNull(transfer.getTransferTime());
    }

    @Test
    void 字符串输出包含工单号与幂等键()
    {
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTicketNo("TK20261118002");
        transfer.setIdempotentKey("idem-002");

        String text = transfer.toString();
        assertTrue(text.contains("TK20261118002"));
        assertTrue(text.contains("idem-002"));
    }
}
