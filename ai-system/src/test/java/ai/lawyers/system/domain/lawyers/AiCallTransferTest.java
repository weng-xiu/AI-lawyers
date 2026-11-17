package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 呼叫转接记录实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiCallTransferTest
{
    @Test
    void 属性读写()
    {
        Date transferTime = new Date();
        AiCallTransfer transfer = new AiCallTransfer();
        transfer.setTransferId(1L);
        transfer.setRecordId(2L);
        transfer.setFromAgentId(3L);
        transfer.setFromAgentName("坐席甲");
        transfer.setToAgentId(4L);
        transfer.setToAgentName("坐席乙");
        transfer.setTransferTime(transferTime);
        transfer.setReason("专业问题转接");

        assertEquals(1L, transfer.getTransferId());
        assertEquals(2L, transfer.getRecordId());
        assertEquals(3L, transfer.getFromAgentId());
        assertEquals("坐席甲", transfer.getFromAgentName());
        assertEquals(4L, transfer.getToAgentId());
        assertEquals("坐席乙", transfer.getToAgentName());
        assertEquals(transferTime, transfer.getTransferTime());
        assertEquals("专业问题转接", transfer.getReason());
    }

    @Test
    void 默认值为空()
    {
        AiCallTransfer transfer = new AiCallTransfer();

        assertNull(transfer.getTransferId());
        assertNull(transfer.getRecordId());
        assertNull(transfer.getFromAgentId());
        assertNull(transfer.getFromAgentName());
        assertNull(transfer.getToAgentId());
        assertNull(transfer.getToAgentName());
        assertNull(transfer.getTransferTime());
        assertNull(transfer.getReason());
    }

    @Test
    void 字符串输出包含转出与转入坐席()
    {
        AiCallTransfer transfer = new AiCallTransfer();
        transfer.setFromAgentName("张三");
        transfer.setToAgentName("李四");

        String text = transfer.toString();
        assertTrue(text.contains("张三"));
        assertTrue(text.contains("李四"));
    }
}
