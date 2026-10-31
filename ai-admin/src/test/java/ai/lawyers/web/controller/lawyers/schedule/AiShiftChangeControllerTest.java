package ai.lawyers.web.controller.lawyers.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.domain.lawyers.schedule.AiAgentSchedule;
import ai.lawyers.system.domain.lawyers.schedule.AiAgentScheduleVO;
import ai.lawyers.system.domain.lawyers.schedule.AiShiftChangeRequest;
import ai.lawyers.system.domain.lawyers.schedule.AiShiftChangeRequestVO;
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;
import ai.lawyers.system.service.lawyers.schedule.IAiAgentScheduleService;
import ai.lawyers.system.service.lawyers.schedule.IAiShiftChangeService;
import ai.lawyers.web.controller.lawyers.AiShiftChangeController;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * F9 {@link AiShiftChangeController} 业务语义回归：
 * <ol>
 *   <li>申请人身份强制取当前登录账号绑定的坐席，忽略请求体（防越权代申请）；</li>
 *   <li>未绑定坐席时 mine / my-schedules 返回空列表、申请直接报错，不触达 Service；</li>
 *   <li>审批人取当前登录用户名，审批意见可选；撤销固定用本人坐席。</li>
 * </ol>
 * 权限 401/403/200 由 {@link AiShiftChangeControllerPermTest} 覆盖，本类只断言业务语义。
 */
public class AiShiftChangeControllerTest extends AbstractPermMvcTest
{
    private static final String BASE = "/lawyers/shift-change";

    /** 与基类 {@link #loginAs} 构造的用户ID/用户名一致 */
    private static final Long CURRENT_USER_ID = 1001L;

    private static final String CURRENT_USERNAME = "perm-tester";

    private static final Long BOUND_AGENT_ID = 101L;

    @Mock
    private IAiShiftChangeService shiftChangeService;

    @Mock
    private IAiCallAgentStatusService aiCallAgentStatusService;

    @Mock
    private IAiAgentScheduleService aiAgentScheduleService;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(shiftChangeService.selectShiftChangeList(any())).thenReturn(new ArrayList<>());
        when(shiftChangeService.selectMyShiftChangeList(any(), any())).thenReturn(new ArrayList<>());
        when(aiAgentScheduleService.selectScheduleList(any())).thenReturn(new ArrayList<>());
        startMvc(AiShiftChangeController.class);
    }

    @Override
    protected void registerMocks()
    {
        registerMock("shiftChangeService", shiftChangeService);
        registerMock("aiCallAgentStatusService", aiCallAgentStatusService);
        registerMock("aiAgentScheduleService", aiAgentScheduleService);
    }

    private void bindAgent(Long agentId)
    {
        AiCallAgentStatus agent = new AiCallAgentStatus();
        agent.setAgentId(agentId);
        agent.setUserId(CURRENT_USER_ID);
        when(aiCallAgentStatusService.selectAiCallAgentStatusByUserId(CURRENT_USER_ID)).thenReturn(agent);
    }

    // ---------------- 申请 ----------------

    @Test
    void apply_ignoresBodyApplicantAndUsesBoundAgent() throws Exception
    {
        bindAgent(BOUND_AGENT_ID);
        when(shiftChangeService.applyShiftChange(any())).thenReturn(1);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicantAgentId\":999,\"applicantScheduleId\":11,"
                                + "\"changeType\":\"SWAP\",\"targetScheduleId\":22,\"reason\":\"临时调休\"}")
                        .with(loginAs("lawyers:schedule:add")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<AiShiftChangeRequest> captor = ArgumentCaptor.forClass(AiShiftChangeRequest.class);
        verify(shiftChangeService).applyShiftChange(captor.capture());
        AiShiftChangeRequest saved = captor.getValue();
        assertEquals(BOUND_AGENT_ID, saved.getApplicantAgentId(), "申请人必须来自登录账号绑定的坐席");
        assertEquals(CURRENT_USERNAME, saved.getCreateBy());
        assertEquals(11L, saved.getApplicantScheduleId());
    }

    @Test
    void apply_withoutAgentBinding_rejectedWithoutTouchingService() throws Exception
    {
        when(aiCallAgentStatusService.selectAiCallAgentStatusByUserId(CURRENT_USER_ID)).thenReturn(null);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicantScheduleId\":11}")
                        .with(loginAs("lawyers:schedule:add")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("当前账号未绑定坐席，无法申请换班"));

        verifyNoInteractions(shiftChangeService);
    }

    // ---------------- 审批 ----------------

    @Test
    void approve_passesCurrentUsernameAndRemark() throws Exception
    {
        when(shiftChangeService.approveShiftChange(5L, CURRENT_USERNAME, "同意互换")).thenReturn(1);

        mockMvc.perform(put(BASE + "/approve/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approveRemark\":\"同意互换\"}")
                        .with(loginAs("lawyers:schedule:edit")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(shiftChangeService).approveShiftChange(5L, CURRENT_USERNAME, "同意互换");
    }

    @Test
    void reject_allowsEmptyBodyWithNullRemark() throws Exception
    {
        when(shiftChangeService.rejectShiftChange(5L, CURRENT_USERNAME, null)).thenReturn(1);

        mockMvc.perform(put(BASE + "/reject/5").with(loginAs("lawyers:schedule:edit")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(shiftChangeService).rejectShiftChange(eq(5L), eq(CURRENT_USERNAME), isNull());
    }

    @Test
    void cancel_usesBoundAgentSoOnlyOwnRequestCanBeCancelled() throws Exception
    {
        bindAgent(BOUND_AGENT_ID);
        when(shiftChangeService.cancelShiftChange(5L, BOUND_AGENT_ID, CURRENT_USERNAME)).thenReturn(1);

        mockMvc.perform(put(BASE + "/cancel/5").with(loginAs("lawyers:schedule:add")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(shiftChangeService).cancelShiftChange(5L, BOUND_AGENT_ID, CURRENT_USERNAME);
    }

    @Test
    void cancel_withoutAgentBinding_rejected() throws Exception
    {
        when(aiCallAgentStatusService.selectAiCallAgentStatusByUserId(CURRENT_USER_ID)).thenReturn(null);

        mockMvc.perform(put(BASE + "/cancel/5").with(loginAs("lawyers:schedule:add")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("当前账号未绑定坐席，无法撤销换班申请"));

        verify(shiftChangeService, never()).cancelShiftChange(any(), any(), any());
    }

    // ---------------- 查询 ----------------

    @Test
    void mine_forcesApplicantAgentIdOfCurrentUser() throws Exception
    {
        bindAgent(BOUND_AGENT_ID);

        mockMvc.perform(get(BASE + "/mine")
                        .param("applicantAgentId", "999")
                        .param("status", "0")
                        .with(loginAs("lawyers:schedule:list")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<AiShiftChangeRequest> captor = ArgumentCaptor.forClass(AiShiftChangeRequest.class);
        verify(shiftChangeService).selectMyShiftChangeList(eq(BOUND_AGENT_ID), captor.capture());
        AiShiftChangeRequest filter = captor.getValue();
        assertNotNull(filter);
        assertEquals("0", filter.getStatus());
    }

    @Test
    void mine_withoutAgentBinding_returnsEmptyRows() throws Exception
    {
        when(aiCallAgentStatusService.selectAiCallAgentStatusByUserId(CURRENT_USER_ID)).thenReturn(null);

        mockMvc.perform(get(BASE + "/mine").with(loginAs("lawyers:schedule:list")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(0));

        verifyNoInteractions(shiftChangeService);
    }

    @Test
    void mySchedules_defaultsToTodayAnd14DaysLaterAndOnlyOwnRows() throws Exception
    {
        bindAgent(BOUND_AGENT_ID);

        mockMvc.perform(get(BASE + "/my-schedules").with(loginAs("lawyers:schedule:list")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<AiAgentScheduleVO> captor = ArgumentCaptor.forClass(AiAgentScheduleVO.class);
        verify(aiAgentScheduleService).selectScheduleList(captor.capture());
        AiAgentScheduleVO query = captor.getValue();
        assertEquals(BOUND_AGENT_ID, query.getAgentId());
        assertEquals(Integer.valueOf(1), query.getStatus());
        assertEquals(LocalDate.now().toString(), query.getParams().get("beginDate"));
        assertEquals(LocalDate.now().plusDays(14).toString(), query.getParams().get("endDate"));
    }

    @Test
    void mySchedules_honoursExplicitDateRange() throws Exception
    {
        bindAgent(BOUND_AGENT_ID);

        mockMvc.perform(get(BASE + "/my-schedules")
                        .param("startDate", "2026-11-02")
                        .param("endDate", "2026-11-06")
                        .with(loginAs("lawyers:schedule:list")))
                .andExpect(status().isOk());

        ArgumentCaptor<AiAgentScheduleVO> captor = ArgumentCaptor.forClass(AiAgentScheduleVO.class);
        verify(aiAgentScheduleService).selectScheduleList(captor.capture());
        assertEquals("2026-11-02", captor.getValue().getParams().get("beginDate"));
        assertEquals("2026-11-06", captor.getValue().getParams().get("endDate"));
    }

    @Test
    void mySchedules_withoutAgentBinding_returnsEmptyRows() throws Exception
    {
        when(aiCallAgentStatusService.selectAiCallAgentStatusByUserId(CURRENT_USER_ID)).thenReturn(null);

        mockMvc.perform(get(BASE + "/my-schedules").with(loginAs("lawyers:schedule:list")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(0));

        verifyNoInteractions(aiAgentScheduleService);
    }

    @Test
    void swapTargets_returnsOnlyOtherAgentsSchedulesOnSameDate() throws Exception
    {
        bindAgent(BOUND_AGENT_ID);
        AiAgentSchedule mine = schedule(11L, BOUND_AGENT_ID, "2026-11-05");
        when(aiAgentScheduleService.selectAiAgentScheduleByScheduleId(11L)).thenReturn(mine);

        AiAgentScheduleVO own = scheduleVo(11L, BOUND_AGENT_ID);
        AiAgentScheduleVO other = scheduleVo(22L, 202L);
        other.setAgentName("李律师");
        AiAgentScheduleVO broken = scheduleVo(33L, null);
        ArrayList<AiAgentScheduleVO> rows = new ArrayList<>();
        rows.add(own);
        rows.add(other);
        rows.add(broken);
        when(aiAgentScheduleService.selectScheduleList(any())).thenReturn(rows);

        mockMvc.perform(get(BASE + "/swap-targets")
                        .param("applicantScheduleId", "11")
                        .with(loginAs("lawyers:schedule:list")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].agentId").value(202));

        ArgumentCaptor<AiAgentScheduleVO> captor = ArgumentCaptor.forClass(AiAgentScheduleVO.class);
        verify(aiAgentScheduleService).selectScheduleList(captor.capture());
        assertEquals("2026-11-05", captor.getValue().getParams().get("beginDate"));
        assertEquals("2026-11-05", captor.getValue().getParams().get("endDate"));
    }

    @Test
    void swapTargets_rejectsScheduleOfAnotherAgent() throws Exception
    {
        bindAgent(BOUND_AGENT_ID);
        when(aiAgentScheduleService.selectAiAgentScheduleByScheduleId(11L))
                .thenReturn(schedule(11L, 303L, "2026-11-05"));

        mockMvc.perform(get(BASE + "/swap-targets")
                        .param("applicantScheduleId", "11")
                        .with(loginAs("lawyers:schedule:list")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("排班不存在或不属于当前坐席"));

        verify(aiAgentScheduleService, never()).selectScheduleList(any());
    }

    @Test
    void list_returnsServiceRows() throws Exception
    {
        AiShiftChangeRequestVO row = new AiShiftChangeRequestVO();
        row.setRequestId(9L);
        row.setApplicantAgentId(BOUND_AGENT_ID);
        row.setChangeType(AiShiftChangeRequest.TYPE_SWAP);
        row.setStatus(AiShiftChangeRequest.STATUS_PENDING);
        ArrayList<AiShiftChangeRequestVO> rows = new ArrayList<>();
        rows.add(row);
        when(shiftChangeService.selectShiftChangeList(any())).thenReturn(rows);

        mockMvc.perform(get(BASE + "/list").with(loginAs("lawyers:schedule:list")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].requestId").value(9))
                .andExpect(jsonPath("$.rows[0].status").value("0"));
    }

    @Test
    void getInfo_returnsDetail() throws Exception
    {
        AiShiftChangeRequestVO detail = new AiShiftChangeRequestVO();
        detail.setRequestId(9L);
        detail.setApplicantAgentName("张律师");
        when(shiftChangeService.selectShiftChangeById(9L)).thenReturn(detail);

        mockMvc.perform(get(BASE + "/9").with(loginAs("lawyers:schedule:query")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.requestId").value(9))
                .andExpect(jsonPath("$.data.applicantAgentName").value("张律师"));
    }

    // ---------------- helpers ----------------

    private static AiAgentSchedule schedule(Long scheduleId, Long agentId, String scheduleDate)
    {
        AiAgentSchedule row = new AiAgentSchedule();
        row.setScheduleId(scheduleId);
        row.setAgentId(agentId);
        row.setScheduleDate(scheduleDate);
        return row;
    }

    private static AiAgentScheduleVO scheduleVo(Long scheduleId, Long agentId)
    {
        AiAgentScheduleVO row = new AiAgentScheduleVO();
        row.setScheduleId(scheduleId);
        row.setAgentId(agentId);
        row.setScheduleDate("2026-11-05");
        return row;
    }
}
