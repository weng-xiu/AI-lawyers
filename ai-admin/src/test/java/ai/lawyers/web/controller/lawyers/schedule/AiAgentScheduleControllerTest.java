package ai.lawyers.web.controller.lawyers.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;
import ai.lawyers.system.service.lawyers.schedule.IAiAgentScheduleService;

/**
 * 坐席排班接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiAgentScheduleControllerTest
{
    @Mock
    private IAiAgentScheduleService aiAgentScheduleService;

    @Mock
    private IAiCallAgentStatusService aiCallAgentStatusService;

    @InjectMocks
    private AiAgentScheduleController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private AiAgentSchedule schedule()
    {
        AiAgentSchedule schedule = new AiAgentSchedule();
        schedule.setAgentId(5L);
        schedule.setShiftId(2L);
        schedule.setScheduleDate("2026-11-20");
        return schedule;
    }

    @Test
    void 排班列表分页返回()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiAgentScheduleVO query = new AiAgentScheduleVO();
        List<AiAgentScheduleVO> list = new ArrayList<AiAgentScheduleVO>();
        when(aiAgentScheduleService.selectScheduleList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertSame(list, table.getRows());
    }

    @Test
    void 日期范围查询与详情透传()
    {
        List<AiAgentScheduleVO> range = new ArrayList<AiAgentScheduleVO>();
        AiAgentScheduleVO detail = new AiAgentScheduleVO();
        when(aiAgentScheduleService.selectByDateRange("2026-11-01", "2026-11-30")).thenReturn(range);
        when(aiAgentScheduleService.selectAiAgentScheduleByScheduleId(7L)).thenReturn(detail);

        assertSame(range, controller.range("2026-11-01", "2026-11-30").get(AjaxResult.DATA_TAG));
        assertSame(detail, controller.getInfo(7L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 今日排班显式坐席直接查询()
    {
        AiAgentScheduleVO vo = new AiAgentScheduleVO();
        when(aiAgentScheduleService.getMyScheduleToday(5L)).thenReturn(vo);

        assertSame(vo, controller.today(5L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 今日排班未绑定坐席返回错误()
    {
        when(aiCallAgentStatusService.selectAiCallAgentStatusByUserId(1L)).thenReturn(null);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            assertEquals("当前账号未绑定坐席，无法查询今日排班", controller.today(null).get(AjaxResult.MSG_TAG));
        }
    }

    @Test
    void 新增排班写操作人()
    {
        AiAgentSchedule schedule = schedule();
        when(aiAgentScheduleService.insertAiAgentSchedule(schedule)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn("leader");
            assertEquals(200, controller.add(schedule).get(AjaxResult.CODE_TAG));
        }
        assertEquals("leader", schedule.getCreateBy());
    }

    @Test
    void 批量排班拒绝空数据()
    {
        assertEquals("排班数据不能为空", controller.batch(null).get(AjaxResult.MSG_TAG));
        assertEquals("排班数据不能为空", controller.batch(new ArrayList<AiAgentSchedule>()).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 批量排班逐条写操作人()
    {
        List<AiAgentSchedule> list = new ArrayList<AiAgentSchedule>();
        list.add(schedule());
        list.add(schedule());
        when(aiAgentScheduleService.batchInsert(list)).thenReturn(2);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn("leader");
            assertEquals(200, controller.batch(list).get(AjaxResult.CODE_TAG));
        }
        assertEquals("leader", list.get(0).getCreateBy());
        assertEquals("leader", list.get(1).getCreateBy());
    }

    @Test
    void 修改排班写更新人与删除()
    {
        AiAgentSchedule schedule = schedule();
        Long[] ids = new Long[] { 7L };
        when(aiAgentScheduleService.updateAiAgentSchedule(schedule)).thenReturn(1);
        when(aiAgentScheduleService.deleteAiAgentScheduleByScheduleIds(ids)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn("leader");
            assertEquals(200, controller.edit(schedule).get(AjaxResult.CODE_TAG));
        }
        assertEquals("leader", schedule.getUpdateBy());
        assertEquals(200, controller.remove(ids).get(AjaxResult.CODE_TAG));
    }

    @Test
    void 签到成功与无排班()
    {
        when(aiAgentScheduleService.checkIn(5L)).thenReturn(1, 0);

        assertEquals("签到成功", controller.checkIn(5L).get(AjaxResult.MSG_TAG));
        assertEquals("今日无排班或已签到", controller.checkIn(5L).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 签退成功与无排班()
    {
        when(aiAgentScheduleService.checkOut(5L)).thenReturn(1, 0);

        assertEquals("签退成功", controller.checkOut(5L).get(AjaxResult.MSG_TAG));
        assertEquals("今日无排班，无法签退", controller.checkOut(5L).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 未绑定坐席不能签到签退()
    {
        bindAgentNull();

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            assertEquals("当前账号未绑定坐席，无法签到", controller.checkIn(null).get(AjaxResult.MSG_TAG));
            assertEquals("当前账号未绑定坐席，无法签退", controller.checkOut(null).get(AjaxResult.MSG_TAG));
        }
    }

    @Test
    void 未传坐席时按登录账号绑定坐席签到()
    {
        bindAgent(8L);
        when(aiAgentScheduleService.checkIn(8L)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            assertEquals("签到成功", controller.checkIn(null).get(AjaxResult.MSG_TAG));
        }
        org.mockito.Mockito.verify(aiAgentScheduleService).checkIn(8L);
    }

    private void bindAgent(Long agentId)
    {
        AiCallAgentStatus agent = new AiCallAgentStatus();
        agent.setAgentId(agentId);
        when(aiCallAgentStatusService.selectAiCallAgentStatusByUserId(1L)).thenReturn(agent);
    }

    private void bindAgentNull()
    {
        when(aiCallAgentStatusService.selectAiCallAgentStatusByUserId(any())).thenReturn(null);
    }
}
