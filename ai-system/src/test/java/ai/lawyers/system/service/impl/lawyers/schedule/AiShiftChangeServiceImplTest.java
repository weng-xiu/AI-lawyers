package ai.lawyers.system.service.impl.lawyers.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.domain.lawyers.schedule.AiAgentSchedule;
import ai.lawyers.system.domain.lawyers.schedule.AiShiftChangeRequest;
import ai.lawyers.system.domain.lawyers.schedule.AiShiftChangeRequestVO;
import ai.lawyers.system.mapper.lawyers.schedule.AiAgentScheduleMapper;
import ai.lawyers.system.mapper.lawyers.schedule.AiShiftChangeRequestMapper;

/**
 * F9 换班申请与审批 Service 单测：
 * 申请阶段只落单不改排班、审批通过才改写排班、CAS 失败回滚语义。
 */
@ExtendWith(MockitoExtension.class)
public class AiShiftChangeServiceImplTest
{
    private static final Long APPLICANT_AGENT = 101L;

    private static final Long TARGET_AGENT = 202L;

    private static final Long APPLICANT_SCHEDULE = 11L;

    private static final Long TARGET_SCHEDULE = 22L;

    @Mock
    private AiShiftChangeRequestMapper requestMapper;

    @Mock
    private AiAgentScheduleMapper scheduleMapper;

    private AiShiftChangeServiceImpl service;

    private final String tomorrow = LocalDate.now().plusDays(1).toString();

    @BeforeEach
    void setUp()
    {
        service = new AiShiftChangeServiceImpl();
        ReflectionTestUtils.setField(service, "shiftChangeRequestMapper", requestMapper);
        ReflectionTestUtils.setField(service, "agentScheduleMapper", scheduleMapper);
    }

    // ---------------- 申请 ----------------

    @Test
    void apply_swapValid_persistsPendingWithoutTouchingSchedules()
    {
        when(scheduleMapper.selectAiAgentScheduleByScheduleId(APPLICANT_SCHEDULE))
                .thenReturn(schedule(APPLICANT_SCHEDULE, APPLICANT_AGENT, 1L, tomorrow, 1));
        when(scheduleMapper.selectAiAgentScheduleByScheduleId(TARGET_SCHEDULE))
                .thenReturn(schedule(TARGET_SCHEDULE, TARGET_AGENT, 2L, tomorrow, 1));
        when(requestMapper.countPendingByApplicantSchedule(APPLICANT_SCHEDULE)).thenReturn(0);
        when(requestMapper.insertShiftChangeRequest(any())).thenReturn(1);

        AiShiftChangeRequest request = applyRequest("swap", TARGET_SCHEDULE);
        int rows = service.applyShiftChange(request);

        assertEquals(1, rows);
        ArgumentCaptor<AiShiftChangeRequest> captor = ArgumentCaptor.forClass(AiShiftChangeRequest.class);
        verify(requestMapper).insertShiftChangeRequest(captor.capture());
        AiShiftChangeRequest saved = captor.getValue();
        assertEquals(AiShiftChangeRequest.TYPE_SWAP, saved.getChangeType());
        assertEquals(AiShiftChangeRequest.STATUS_PENDING, saved.getStatus());
        assertEquals(APPLICANT_AGENT, saved.getApplicantAgentId());
        assertEquals(TARGET_SCHEDULE, saved.getTargetScheduleId());
        assertEquals(TARGET_AGENT, saved.getTargetAgentId());
        // 申请阶段只读排班做校验，不得改写排班
        verify(scheduleMapper, never()).updateShiftAssignment(any(), any(), any(), any());
        verify(scheduleMapper, never()).transferScheduleToAgent(any(), any(), any());
    }

    @Test
    void apply_transferValid_clearsStaleTargetScheduleAndSetsTargetAgent()
    {
        when(scheduleMapper.selectAiAgentScheduleByScheduleId(APPLICANT_SCHEDULE))
                .thenReturn(schedule(APPLICANT_SCHEDULE, APPLICANT_AGENT, 1L, tomorrow, 1));
        when(scheduleMapper.selectByAgentAndDate(TARGET_AGENT, tomorrow)).thenReturn(null);
        when(requestMapper.countPendingByApplicantSchedule(APPLICANT_SCHEDULE)).thenReturn(0);
        when(requestMapper.insertShiftChangeRequest(any())).thenReturn(1);

        AiShiftChangeRequest request = new AiShiftChangeRequest();
        request.setApplicantAgentId(APPLICANT_AGENT);
        request.setApplicantScheduleId(APPLICANT_SCHEDULE);
        request.setChangeType("transfer");
        request.setTargetAgentId(TARGET_AGENT);
        request.setTargetScheduleId(TARGET_SCHEDULE);
        request.setReason("临时外出");

        int rows = service.applyShiftChange(request);

        assertEquals(1, rows);
        assertEquals(AiShiftChangeRequest.TYPE_TRANSFER, request.getChangeType());
        assertNull(request.getTargetScheduleId(), "转班不应保留对方排班ID");
        assertEquals(TARGET_AGENT, request.getTargetAgentId());
    }

    @Test
    void apply_rejectsOtherAgentSchedule()
    {
        when(scheduleMapper.selectAiAgentScheduleByScheduleId(APPLICANT_SCHEDULE))
                .thenReturn(schedule(APPLICANT_SCHEDULE, 303L, 1L, tomorrow, 1));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.applyShiftChange(applyRequest(AiShiftChangeRequest.TYPE_SWAP, TARGET_SCHEDULE)));
        assertEquals("只能申请本人的排班换班", ex.getMessage());
        verify(requestMapper, never()).insertShiftChangeRequest(any());
    }

    @Test
    void apply_rejectsPastSchedule()
    {
        when(scheduleMapper.selectAiAgentScheduleByScheduleId(APPLICANT_SCHEDULE))
                .thenReturn(schedule(APPLICANT_SCHEDULE, APPLICANT_AGENT, 1L,
                        LocalDate.now().minusDays(1).toString(), 1));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.applyShiftChange(applyRequest(AiShiftChangeRequest.TYPE_SWAP, TARGET_SCHEDULE)));
        assertEquals("仅可申请今天及以后的排班换班", ex.getMessage());
    }

    @Test
    void apply_rejectsCancelledSchedule()
    {
        when(scheduleMapper.selectAiAgentScheduleByScheduleId(APPLICANT_SCHEDULE))
                .thenReturn(schedule(APPLICANT_SCHEDULE, APPLICANT_AGENT, 1L, tomorrow, 0));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.applyShiftChange(applyRequest(AiShiftChangeRequest.TYPE_SWAP, TARGET_SCHEDULE)));
        assertEquals("该排班已取消，无法申请换班", ex.getMessage());
    }

    @Test
    void apply_rejectsDuplicatePendingRequest()
    {
        when(scheduleMapper.selectAiAgentScheduleByScheduleId(APPLICANT_SCHEDULE))
                .thenReturn(schedule(APPLICANT_SCHEDULE, APPLICANT_AGENT, 1L, tomorrow, 1));
        when(requestMapper.countPendingByApplicantSchedule(APPLICANT_SCHEDULE)).thenReturn(1);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.applyShiftChange(applyRequest(AiShiftChangeRequest.TYPE_SWAP, TARGET_SCHEDULE)));
        assertEquals("该排班已有待审批的换班申请，请等待处理", ex.getMessage());
    }

    @Test
    void apply_rejectsSwapWithSelf()
    {
        when(scheduleMapper.selectAiAgentScheduleByScheduleId(APPLICANT_SCHEDULE))
                .thenReturn(schedule(APPLICANT_SCHEDULE, APPLICANT_AGENT, 1L, tomorrow, 1));
        when(scheduleMapper.selectAiAgentScheduleByScheduleId(TARGET_SCHEDULE))
                .thenReturn(schedule(TARGET_SCHEDULE, APPLICANT_AGENT, 2L, tomorrow, 1));
        when(requestMapper.countPendingByApplicantSchedule(APPLICANT_SCHEDULE)).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.applyShiftChange(applyRequest(AiShiftChangeRequest.TYPE_SWAP, TARGET_SCHEDULE)));
        assertEquals("不能与自己换班", ex.getMessage());
    }

    @Test
    void apply_rejectsSwapAcrossDifferentDates()
    {
        when(scheduleMapper.selectAiAgentScheduleByScheduleId(APPLICANT_SCHEDULE))
                .thenReturn(schedule(APPLICANT_SCHEDULE, APPLICANT_AGENT, 1L, tomorrow, 1));
        when(requestMapper.countPendingByApplicantSchedule(APPLICANT_SCHEDULE)).thenReturn(0);
        // 对方排班在其它日期
        when(scheduleMapper.selectAiAgentScheduleByScheduleId(TARGET_SCHEDULE))
                .thenReturn(schedule(TARGET_SCHEDULE, TARGET_AGENT, 2L,
                        LocalDate.now().plusDays(2).toString(), 1));

        AiShiftChangeRequest request = applyRequest(AiShiftChangeRequest.TYPE_SWAP, TARGET_SCHEDULE);
        request.setTargetAgentId(TARGET_AGENT);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.applyShiftChange(request));
        assertEquals("互换换班需选择同一天的对方排班", ex.getMessage());
    }

    @Test
    void apply_rejectsTransferWhenTargetHasSchedule()
    {
        when(scheduleMapper.selectAiAgentScheduleByScheduleId(APPLICANT_SCHEDULE))
                .thenReturn(schedule(APPLICANT_SCHEDULE, APPLICANT_AGENT, 1L, tomorrow, 1));
        when(scheduleMapper.selectByAgentAndDate(TARGET_AGENT, tomorrow))
                .thenReturn(schedule(33L, TARGET_AGENT, 3L, tomorrow, 1));
        when(requestMapper.countPendingByApplicantSchedule(APPLICANT_SCHEDULE)).thenReturn(0);

        AiShiftChangeRequest request = applyRequest(AiShiftChangeRequest.TYPE_TRANSFER, null);
        request.setTargetAgentId(TARGET_AGENT);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.applyShiftChange(request));
        assertEquals("接收坐席当日已有排班，无法转班", ex.getMessage());
    }

    @Test
    void apply_rejectsInvalidChangeType()
    {
        when(scheduleMapper.selectAiAgentScheduleByScheduleId(APPLICANT_SCHEDULE))
                .thenReturn(schedule(APPLICANT_SCHEDULE, APPLICANT_AGENT, 1L, tomorrow, 1));
        when(requestMapper.countPendingByApplicantSchedule(APPLICANT_SCHEDULE)).thenReturn(0);

        AiShiftChangeRequest request = applyRequest("COPY", TARGET_SCHEDULE);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.applyShiftChange(request));
        assertEquals("换班类型非法（SWAP 互换 / TRANSFER 转班）", ex.getMessage());
    }

    // ---------------- 审批 ----------------

    @Test
    void approve_swapExchangesShiftIds()
    {
        when(requestMapper.selectShiftChangeRequestById(9L)).thenReturn(pending(AiShiftChangeRequest.TYPE_SWAP));
        when(scheduleMapper.selectAiAgentScheduleByScheduleId(APPLICANT_SCHEDULE))
                .thenReturn(schedule(APPLICANT_SCHEDULE, APPLICANT_AGENT, 1L, tomorrow, 1));
        when(scheduleMapper.selectAiAgentScheduleByScheduleId(TARGET_SCHEDULE))
                .thenReturn(schedule(TARGET_SCHEDULE, TARGET_AGENT, 2L, tomorrow, 1));
        when(requestMapper.updateApproval(eq(9L), eq(AiShiftChangeRequest.STATUS_APPROVED), anyString(),
                any(), anyString())).thenReturn(1);

        int rows = service.approveShiftChange(9L, "leader", "同意互换");

        assertEquals(1, rows);
        verify(scheduleMapper).updateShiftAssignment(eq(APPLICANT_SCHEDULE), eq(2L), isNull(), eq("leader"));
        verify(scheduleMapper).updateShiftAssignment(eq(TARGET_SCHEDULE), eq(1L), isNull(), eq("leader"));
    }

    @Test
    void approve_swapRejectsInvalidTargetSchedule()
    {
        when(requestMapper.selectShiftChangeRequestById(9L)).thenReturn(pending(AiShiftChangeRequest.TYPE_SWAP));
        when(scheduleMapper.selectAiAgentScheduleByScheduleId(APPLICANT_SCHEDULE))
                .thenReturn(schedule(APPLICANT_SCHEDULE, APPLICANT_AGENT, 1L, tomorrow, 1));
        when(scheduleMapper.selectAiAgentScheduleByScheduleId(TARGET_SCHEDULE))
                .thenReturn(schedule(TARGET_SCHEDULE, TARGET_AGENT, 2L, tomorrow, 0));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.approveShiftChange(9L, "leader", null));
        assertEquals("对方排班已失效，无法通过审批", ex.getMessage());
        verify(scheduleMapper, never()).updateShiftAssignment(any(), any(), any(), any());
    }

    @Test
    void approve_transferMovesScheduleToTargetAgent()
    {
        when(requestMapper.selectShiftChangeRequestById(9L)).thenReturn(pending(AiShiftChangeRequest.TYPE_TRANSFER));
        when(scheduleMapper.selectAiAgentScheduleByScheduleId(APPLICANT_SCHEDULE))
                .thenReturn(schedule(APPLICANT_SCHEDULE, APPLICANT_AGENT, 1L, tomorrow, 1));
        when(scheduleMapper.selectByAgentAndDate(TARGET_AGENT, tomorrow)).thenReturn(null);
        when(requestMapper.updateApproval(eq(9L), eq(AiShiftChangeRequest.STATUS_APPROVED), anyString(),
                any(), anyString())).thenReturn(1);

        int rows = service.approveShiftChange(9L, "leader", null);

        assertEquals(1, rows);
        verify(scheduleMapper).transferScheduleToAgent(APPLICANT_SCHEDULE, TARGET_AGENT, "leader");
        verify(scheduleMapper, never()).updateShiftAssignment(any(), any(), any(), any());
    }

    @Test
    void approve_transferRejectsWhenTargetScheduleAppeared()
    {
        when(requestMapper.selectShiftChangeRequestById(9L)).thenReturn(pending(AiShiftChangeRequest.TYPE_TRANSFER));
        when(scheduleMapper.selectAiAgentScheduleByScheduleId(APPLICANT_SCHEDULE))
                .thenReturn(schedule(APPLICANT_SCHEDULE, APPLICANT_AGENT, 1L, tomorrow, 1));
        when(scheduleMapper.selectByAgentAndDate(TARGET_AGENT, tomorrow))
                .thenReturn(schedule(33L, TARGET_AGENT, 3L, tomorrow, 1));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.approveShiftChange(9L, "leader", null));
        assertEquals("接收坐席当日已有排班，无法转班", ex.getMessage());
        verify(scheduleMapper, never()).transferScheduleToAgent(any(), any(), any());
    }

    @Test
    void approve_rejectsAlreadyHandledRequest()
    {
        AiShiftChangeRequestVO handled = pending(AiShiftChangeRequest.TYPE_SWAP);
        handled.setStatus(AiShiftChangeRequest.STATUS_APPROVED);
        when(requestMapper.selectShiftChangeRequestById(9L)).thenReturn(handled);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.approveShiftChange(9L, "leader", null));
        assertEquals("该申请已处理，请勿重复操作", ex.getMessage());
        verifyNoInteractions(scheduleMapper);
    }

    @Test
    void approve_casFailurePropagatesSoTransactionRollsBack()
    {
        when(requestMapper.selectShiftChangeRequestById(9L)).thenReturn(pending(AiShiftChangeRequest.TYPE_TRANSFER));
        when(scheduleMapper.selectAiAgentScheduleByScheduleId(APPLICANT_SCHEDULE))
                .thenReturn(schedule(APPLICANT_SCHEDULE, APPLICANT_AGENT, 1L, tomorrow, 1));
        when(scheduleMapper.selectByAgentAndDate(TARGET_AGENT, tomorrow)).thenReturn(null);
        when(requestMapper.updateApproval(eq(9L), eq(AiShiftChangeRequest.STATUS_APPROVED), anyString(),
                any(), anyString())).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.approveShiftChange(9L, "leader", null));
        assertEquals("该申请已被处理，请刷新后重试", ex.getMessage());
    }

    @Test
    void reject_marksRejected()
    {
        when(requestMapper.selectShiftChangeRequestById(9L)).thenReturn(pending(AiShiftChangeRequest.TYPE_SWAP));
        when(requestMapper.updateApproval(eq(9L), eq(AiShiftChangeRequest.STATUS_REJECTED), anyString(), any(), anyString()))
                .thenReturn(1);

        int rows = service.rejectShiftChange(9L, "leader", "人员不足");

        assertEquals(1, rows);
        verifyNoInteractions(scheduleMapper);
    }

    @Test
    void cancel_onlyApplicantCanCancel()
    {
        when(requestMapper.selectShiftChangeRequestById(9L)).thenReturn(pending(AiShiftChangeRequest.TYPE_SWAP));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.cancelShiftChange(9L, TARGET_AGENT, "other"));
        assertEquals("只能撤销本人的换班申请", ex.getMessage());

        when(requestMapper.updateApproval(eq(9L), eq(AiShiftChangeRequest.STATUS_CANCELLED), anyString(), any(), anyString()))
                .thenReturn(1);
        assertEquals(1, service.cancelShiftChange(9L, APPLICANT_AGENT, "applicant"));
        verifyNoInteractions(scheduleMapper);
    }

    @Test
    void myList_withoutAgentBindingReturnsEmpty()
    {
        assertEquals(0, service.selectMyShiftChangeList(null, new AiShiftChangeRequest()).size());
        verifyNoInteractions(requestMapper);
    }

    @Test
    void list_defaultsToEmptyQueryInsteadOfNull()
    {
        when(requestMapper.selectShiftChangeRequestList(any())).thenReturn(new ArrayList<>());
        assertEquals(0, service.selectShiftChangeList(null).size());
        verify(requestMapper).selectShiftChangeRequestList(any());
    }

    // ---------------- helpers ----------------

    private AiShiftChangeRequest applyRequest(String changeType, Long targetScheduleId)
    {
        AiShiftChangeRequest request = new AiShiftChangeRequest();
        request.setApplicantAgentId(APPLICANT_AGENT);
        request.setApplicantScheduleId(APPLICANT_SCHEDULE);
        request.setChangeType(changeType);
        request.setTargetScheduleId(targetScheduleId);
        request.setReason("临时调休");
        return request;
    }

    private AiShiftChangeRequestVO pending(String changeType)
    {
        AiShiftChangeRequestVO vo = new AiShiftChangeRequestVO();
        vo.setRequestId(9L);
        vo.setApplicantAgentId(APPLICANT_AGENT);
        vo.setApplicantScheduleId(APPLICANT_SCHEDULE);
        vo.setChangeType(changeType);
        vo.setTargetAgentId(TARGET_AGENT);
        vo.setTargetScheduleId(AiShiftChangeRequest.TYPE_SWAP.equals(changeType) ? TARGET_SCHEDULE : null);
        vo.setStatus(AiShiftChangeRequest.STATUS_PENDING);
        return vo;
    }

    private static AiAgentSchedule schedule(Long scheduleId, Long agentId, Long shiftId, String date, Integer status)
    {
        AiAgentSchedule schedule = new AiAgentSchedule();
        schedule.setScheduleId(scheduleId);
        schedule.setAgentId(agentId);
        schedule.setShiftId(shiftId);
        schedule.setScheduleDate(date);
        schedule.setStatus(status);
        return schedule;
    }
}
