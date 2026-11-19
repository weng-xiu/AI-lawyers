package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.domain.lawyers.schedule.AiAgentSchedule;
import ai.lawyers.system.domain.lawyers.schedule.AiAgentScheduleVO;
import ai.lawyers.system.domain.lawyers.schedule.AiShiftChangeRequest;
import ai.lawyers.system.domain.lawyers.schedule.AiShiftChangeRequestVO;
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;
import ai.lawyers.system.service.lawyers.schedule.IAiAgentScheduleService;
import ai.lawyers.system.service.lawyers.schedule.IAiShiftChangeService;

/**
 * 换班申请接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiShiftChangeControllerTest
{
    @Mock
    private IAiShiftChangeService shiftChangeService;

    @Mock
    private IAiCallAgentStatusService aiCallAgentStatusService;

    @Mock
    private IAiAgentScheduleService aiAgentScheduleService;

    @InjectMocks
    private AiShiftChangeController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private AiCallAgentStatus agent(Long agentId)
    {
        AiCallAgentStatus agent = new AiCallAgentStatus();
        agent.setAgentId(agentId);
        return agent;
    }

    private AiAgentSchedule schedule(Long agentId, String date)
    {
        AiAgentSchedule schedule = new AiAgentSchedule();
        schedule.setAgentId(agentId);
        schedule.setScheduleDate(date);
        return schedule;
    }

    private AiAgentScheduleVO scheduleVO(Long agentId)
    {
        AiAgentScheduleVO vo = new AiAgentScheduleVO();
        vo.setAgentId(agentId);
        return vo;
    }

    @Test
    void 申请列表分页返回()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiShiftChangeRequest query = new AiShiftChangeRequest();
        List<AiShiftChangeRequestVO> list = new ArrayList<AiShiftChangeRequestVO>();
        when(shiftChangeService.selectShiftChangeList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertSame(list, table.getRows());
    }

    @Test
    void 我的申请未绑定坐席返回空()
    {
        when(aiCallAgentStatusService.selectAiCallAgentStatusByUserId(1L)).thenReturn(null);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            TableDataInfo table = controller.mine(new AiShiftChangeRequest());

            assertEquals(200, table.getCode());
            assertEquals(0, table.getTotal());
        }
    }

    @Test
    void 我的申请按绑定坐席分页()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiShiftChangeRequest query = new AiShiftChangeRequest();
        List<AiShiftChangeRequestVO> list = new ArrayList<AiShiftChangeRequestVO>();
        list.add(new AiShiftChangeRequestVO());
        when(aiCallAgentStatusService.selectAiCallAgentStatusByUserId(1L)).thenReturn(agent(5L));
        when(shiftChangeService.selectMyShiftChangeList(5L, query)).thenReturn(list);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            assertEquals(list, controller.mine(query).getRows());
        }
    }

    @Test
    void 申请详情透传()
    {
        AiShiftChangeRequestVO vo = new AiShiftChangeRequestVO();
        when(shiftChangeService.selectShiftChangeById(9L)).thenReturn(vo);

        assertSame(vo, controller.getInfo(9L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 我的排班未绑定坐席返回空()
    {
        when(aiCallAgentStatusService.selectAiCallAgentStatusByUserId(1L)).thenReturn(null);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            Object data = controller.mySchedules(null, null).get(AjaxResult.DATA_TAG);

            assertEquals(new ArrayList<Object>(), data);
        }
    }

    @Test
    void 我的排班默认查询今天起十四天()
    {
        List<AiAgentScheduleVO> list = new ArrayList<AiAgentScheduleVO>();
        when(aiCallAgentStatusService.selectAiCallAgentStatusByUserId(1L)).thenReturn(agent(5L));
        when(aiAgentScheduleService.selectScheduleList(any(AiAgentScheduleVO.class))).thenReturn(list);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            assertSame(list, controller.mySchedules(null, null).get(AjaxResult.DATA_TAG));
        }
        ArgumentCaptor<AiAgentScheduleVO> captor = ArgumentCaptor.forClass(AiAgentScheduleVO.class);
        org.mockito.Mockito.verify(aiAgentScheduleService).selectScheduleList(captor.capture());
        AiAgentScheduleVO query = captor.getValue();
        assertEquals(5L, query.getAgentId());
        assertEquals(1, query.getStatus());
        assertEquals(LocalDate.now().format(fmt), query.getParams().get("beginDate"));
        assertEquals(LocalDate.now().plusDays(14).format(fmt), query.getParams().get("endDate"));
    }

    @Test
    void 我的排班指定日期透传()
    {
        List<AiAgentScheduleVO> list = new ArrayList<AiAgentScheduleVO>();
        when(aiCallAgentStatusService.selectAiCallAgentStatusByUserId(1L)).thenReturn(agent(5L));
        when(aiAgentScheduleService.selectScheduleList(any(AiAgentScheduleVO.class))).thenReturn(list);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            assertSame(list, controller.mySchedules("2026-11-01", "2026-11-05").get(AjaxResult.DATA_TAG));
        }
        ArgumentCaptor<AiAgentScheduleVO> captor = ArgumentCaptor.forClass(AiAgentScheduleVO.class);
        org.mockito.Mockito.verify(aiAgentScheduleService).selectScheduleList(captor.capture());
        assertEquals("2026-11-01", captor.getValue().getParams().get("beginDate"));
        assertEquals("2026-11-05", captor.getValue().getParams().get("endDate"));
    }

    @Test
    void 互换目标未绑定坐席返回空()
    {
        when(aiCallAgentStatusService.selectAiCallAgentStatusByUserId(1L)).thenReturn(null);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            Object data = controller.swapTargets(8L).get(AjaxResult.DATA_TAG);

            assertEquals(new ArrayList<Object>(), data);
        }
    }

    @Test
    void 互换目标排班不属于当前坐席()
    {
        when(aiCallAgentStatusService.selectAiCallAgentStatusByUserId(1L)).thenReturn(agent(5L));
        when(aiAgentScheduleService.selectAiAgentScheduleByScheduleId(8L)).thenReturn(schedule(6L, "2026-11-20"));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            assertEquals(500, controller.swapTargets(8L).get(AjaxResult.CODE_TAG));
            assertEquals("排班不存在或不属于当前坐席", controller.swapTargets(8L).get(AjaxResult.MSG_TAG));
        }
    }

    @Test
    void 互换目标仅保留同一天他人排班()
    {
        List<AiAgentScheduleVO> rows = new ArrayList<AiAgentScheduleVO>();
        rows.add(scheduleVO(5L));
        rows.add(scheduleVO(6L));
        when(aiCallAgentStatusService.selectAiCallAgentStatusByUserId(1L)).thenReturn(agent(5L));
        when(aiAgentScheduleService.selectAiAgentScheduleByScheduleId(8L)).thenReturn(schedule(5L, "2026-11-20"));
        when(aiAgentScheduleService.selectScheduleList(any(AiAgentScheduleVO.class))).thenReturn(rows);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            @SuppressWarnings("unchecked")
            List<AiAgentScheduleVO> targets = (List<AiAgentScheduleVO>) controller.swapTargets(8L)
                    .get(AjaxResult.DATA_TAG);

            assertEquals(1, targets.size());
            assertEquals(6L, targets.get(0).getAgentId());
        }
    }

    @Test
    void 提交换班申请未绑定坐席被拒绝()
    {
        when(aiCallAgentStatusService.selectAiCallAgentStatusByUserId(1L)).thenReturn(null);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            assertEquals("当前账号未绑定坐席，无法申请换班",
                    controller.apply(new AiShiftChangeRequest()).get(AjaxResult.MSG_TAG));
        }
    }

    @Test
    void 提交换班申请覆盖申请人()
    {
        AiShiftChangeRequest request = new AiShiftChangeRequest();
        request.setApplicantAgentId(99L);
        when(aiCallAgentStatusService.selectAiCallAgentStatusByUserId(1L)).thenReturn(agent(5L));
        when(shiftChangeService.applyShiftChange(request)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            mocked.when(SecurityUtils::getUsername).thenReturn("operator");
            assertEquals(200, controller.apply(request).get(AjaxResult.CODE_TAG));
        }
        assertEquals(5L, request.getApplicantAgentId());
        assertEquals("operator", request.getCreateBy());
    }

    @Test
    void 审批通过与驳回使用当前审批人()
    {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("approveRemark", "同意换班");
        when(shiftChangeService.approveShiftChange(9L, "leader", "同意换班")).thenReturn(1);
        when(shiftChangeService.rejectShiftChange(10L, "leader", null)).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn("leader");
            assertEquals(200, controller.approve(9L, body).get(AjaxResult.CODE_TAG));
            assertEquals(500, controller.reject(10L, null).get(AjaxResult.CODE_TAG));
        }
    }

    @Test
    void 撤销本人申请成功()
    {
        when(aiCallAgentStatusService.selectAiCallAgentStatusByUserId(1L)).thenReturn(agent(5L));
        when(shiftChangeService.cancelShiftChange(9L, 5L, "agent")).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            mocked.when(SecurityUtils::getUsername).thenReturn("agent");
            assertEquals(200, controller.cancel(9L).get(AjaxResult.CODE_TAG));
        }
    }

    @Test
    void 撤销申请未绑定坐席被拒绝()
    {
        when(aiCallAgentStatusService.selectAiCallAgentStatusByUserId(1L)).thenReturn(null);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            assertEquals("当前账号未绑定坐席，无法撤销换班申请",
                    controller.cancel(9L).get(AjaxResult.MSG_TAG));
        }
    }
}
