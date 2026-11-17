package ai.lawyers.system.domain.lawyers.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 换班申请列表展示对象测试：扩展字段读写与继承字段。
 *
 * @author ai-lawyers
 */
class AiShiftChangeRequestVOTest
{
    @Test
    void 属性读写()
    {
        AiShiftChangeRequestVO vo = new AiShiftChangeRequestVO();
        vo.setRequestId(1L);
        vo.setApplicantAgentId(2L);
        vo.setChangeType(AiShiftChangeRequest.TYPE_SWAP);
        vo.setStatus(AiShiftChangeRequest.STATUS_PENDING);
        vo.setApplicantAgentName("张三");
        vo.setTargetAgentName("李四");
        vo.setApplicantShiftName("早班");
        vo.setTargetShiftName("晚班");
        vo.setApplicantScheduleDate("2026-01-05");
        vo.setTargetScheduleDate("2026-01-06");

        assertEquals(1L, vo.getRequestId());
        assertEquals(2L, vo.getApplicantAgentId());
        assertEquals(AiShiftChangeRequest.TYPE_SWAP, vo.getChangeType());
        assertEquals(AiShiftChangeRequest.STATUS_PENDING, vo.getStatus());
        assertEquals("张三", vo.getApplicantAgentName());
        assertEquals("李四", vo.getTargetAgentName());
        assertEquals("早班", vo.getApplicantShiftName());
        assertEquals("晚班", vo.getTargetShiftName());
        assertEquals("2026-01-05", vo.getApplicantScheduleDate());
        assertEquals("2026-01-06", vo.getTargetScheduleDate());
    }

    @Test
    void 默认值为空()
    {
        AiShiftChangeRequestVO vo = new AiShiftChangeRequestVO();

        assertNull(vo.getRequestId());
        assertNull(vo.getApplicantAgentName());
        assertNull(vo.getTargetAgentName());
        assertNull(vo.getApplicantShiftName());
        assertNull(vo.getTargetScheduleDate());
    }
}
