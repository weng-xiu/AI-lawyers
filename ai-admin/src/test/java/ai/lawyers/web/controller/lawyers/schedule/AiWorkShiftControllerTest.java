package ai.lawyers.web.controller.lawyers.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
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
import ai.lawyers.system.domain.lawyers.schedule.AiWorkShift;
import ai.lawyers.system.service.lawyers.schedule.IAiWorkShiftService;

/**
 * 班次定义接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiWorkShiftControllerTest
{
    @Mock
    private IAiWorkShiftService aiWorkShiftService;

    @InjectMocks
    private AiWorkShiftController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiWorkShift query = new AiWorkShift();
        List<AiWorkShift> shifts = new ArrayList<AiWorkShift>();
        shifts.add(new AiWorkShift());
        when(aiWorkShiftService.selectAiWorkShiftList(query)).thenReturn(shifts);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(shifts, table.getRows());
    }

    @Test
    void 启用班次透传服务结果()
    {
        List<AiWorkShift> shifts = new ArrayList<AiWorkShift>();
        shifts.add(new AiWorkShift());
        when(aiWorkShiftService.selectAllEnabled()).thenReturn(shifts);

        AjaxResult result = controller.enabled();

        assertSame(shifts, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 详情透传服务结果()
    {
        AiWorkShift shift = new AiWorkShift();
        shift.setShiftId(4L);
        when(aiWorkShiftService.selectAiWorkShiftByShiftId(4L)).thenReturn(shift);

        AjaxResult result = controller.getInfo(4L);

        assertSame(shift, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增班次写入操作人()
    {
        AiWorkShift shift = new AiWorkShift();
        when(aiWorkShiftService.insertAiWorkShift(any(AiWorkShift.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn("admin");
            AjaxResult result = controller.add(shift);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", shift.getCreateBy());
    }

    @Test
    void 修改班次写入更新人()
    {
        AiWorkShift shift = new AiWorkShift();
        shift.setShiftId(6L);
        when(aiWorkShiftService.updateAiWorkShift(any(AiWorkShift.class))).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn("scheduler");
            AjaxResult result = controller.edit(shift);
            assertEquals(500, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("scheduler", shift.getUpdateBy());
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] shiftIds = new Long[] { 1L, 2L };
        when(aiWorkShiftService.deleteAiWorkShiftByShiftIds(shiftIds)).thenReturn(2);

        AjaxResult result = controller.remove(shiftIds);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiWorkShiftService).deleteAiWorkShiftByShiftIds(shiftIds);
    }
}
