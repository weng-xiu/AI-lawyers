package ai.lawyers.system.domain.lawyers.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 坐席换班申请实体测试：常量、属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiShiftChangeRequestTest
{
    @Test
    void 类型与状态常量()
    {
        assertEquals("SWAP", AiShiftChangeRequest.TYPE_SWAP);
        assertEquals("TRANSFER", AiShiftChangeRequest.TYPE_TRANSFER);
        assertEquals("0", AiShiftChangeRequest.STATUS_PENDING);
        assertEquals("1", AiShiftChangeRequest.STATUS_APPROVED);
        assertEquals("2", AiShiftChangeRequest.STATUS_REJECTED);
        assertEquals("3", AiShiftChangeRequest.STATUS_CANCELLED);
    }

    @Test
    void 属性读写()
    {
        Date approveTime = new Date();
        AiShiftChangeRequest request = new AiShiftChangeRequest();
        request.setRequestId(1L);
        request.setApplicantAgentId(2L);
        request.setApplicantScheduleId(3L);
        request.setChangeType(AiShiftChangeRequest.TYPE_SWAP);
        request.setTargetAgentId(4L);
        request.setTargetScheduleId(5L);
        request.setReason("家中有事需要换班");
        request.setStatus(AiShiftChangeRequest.STATUS_APPROVED);
        request.setApprover("张三");
        request.setApproveTime(approveTime);
        request.setApproveRemark("同意换班");

        assertEquals(1L, request.getRequestId());
        assertEquals(2L, request.getApplicantAgentId());
        assertEquals(3L, request.getApplicantScheduleId());
        assertEquals(AiShiftChangeRequest.TYPE_SWAP, request.getChangeType());
        assertEquals(4L, request.getTargetAgentId());
        assertEquals(5L, request.getTargetScheduleId());
        assertEquals("家中有事需要换班", request.getReason());
        assertEquals(AiShiftChangeRequest.STATUS_APPROVED, request.getStatus());
        assertEquals("张三", request.getApprover());
        assertEquals(approveTime, request.getApproveTime());
        assertEquals("同意换班", request.getApproveRemark());
    }

    @Test
    void 默认值为空()
    {
        AiShiftChangeRequest request = new AiShiftChangeRequest();

        assertNull(request.getRequestId());
        assertNull(request.getChangeType());
        assertNull(request.getStatus());
        assertNull(request.getApprover());
        assertNull(request.getApproveTime());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        AiShiftChangeRequest request = new AiShiftChangeRequest();
        request.setRequestId(1L);
        request.setChangeType(AiShiftChangeRequest.TYPE_TRANSFER);
        request.setReason("家中有事需要换班");
        request.setStatus(AiShiftChangeRequest.STATUS_PENDING);

        String text = request.toString();

        assertTrue(text.contains("TRANSFER"));
        assertTrue(text.contains("家中有事需要换班"));
        assertTrue(text.contains("requestId"));
    }
}
