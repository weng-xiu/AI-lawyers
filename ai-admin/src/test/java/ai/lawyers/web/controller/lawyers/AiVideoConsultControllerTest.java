package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiVideoConsult;
import ai.lawyers.system.domain.lawyers.AiVideoConsultLog;
import ai.lawyers.system.service.lawyers.IAiVideoConsultLogService;
import ai.lawyers.system.service.lawyers.IAiVideoConsultService;

/**
 * 视频咨询接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiVideoConsultControllerTest
{
    @Mock
    private IAiVideoConsultService aiVideoConsultService;

    @Mock
    private IAiVideoConsultLogService aiVideoConsultLogService;

    @InjectMocks
    private AiVideoConsultController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private LoginUser loginUser(String username)
    {
        SysUser user = new SysUser();
        user.setUserName(username);
        return new LoginUser(1L, 2L, user, null);
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiVideoConsult query = new AiVideoConsult();
        List<AiVideoConsult> list = new ArrayList<AiVideoConsult>();
        list.add(new AiVideoConsult());
        when(aiVideoConsultService.selectAiVideoConsultList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(list, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(aiVideoConsultService.selectAiVideoConsultList(any(AiVideoConsult.class)))
                .thenReturn(List.of(new AiVideoConsult()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiVideoConsult());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情透传服务结果()
    {
        AiVideoConsult consult = new AiVideoConsult();
        consult.setConsultId(6L);
        when(aiVideoConsultService.selectAiVideoConsultByConsultId(6L)).thenReturn(consult);

        assertSame(consult, controller.getInfo(6L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增补齐受理人状态与编号()
    {
        AiVideoConsult consult = new AiVideoConsult();
        when(aiVideoConsultService.insertAiVideoConsult(any(AiVideoConsult.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("lawyer"));
            AjaxResult result = controller.add(consult);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("lawyer", consult.getCreateBy());
        assertEquals("lawyer", consult.getAgentName());
        assertEquals("0", consult.getStatus());
        assertNotNull(consult.getStartTime());
        assertTrue(consult.getConsultNo().startsWith("VIDEO"));
    }

    @Test
    void 结束状态时补充结束时间()
    {
        AiVideoConsult consult = new AiVideoConsult();
        consult.setStatus("2");
        when(aiVideoConsultService.updateAiVideoConsult(consult)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("lawyer"));
            controller.edit(consult);
        }
        assertNotNull(consult.getEndTime());
        assertEquals("lawyer", consult.getUpdateBy());
    }

    @Test
    void 非结束状态不补结束时间()
    {
        AiVideoConsult consult = new AiVideoConsult();
        consult.setStatus("1");
        when(aiVideoConsultService.updateAiVideoConsult(consult)).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("lawyer"));
            controller.edit(consult);
        }
        assertEquals(null, consult.getEndTime());
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] consultIds = new Long[] { 1L, 2L };
        when(aiVideoConsultService.deleteAiVideoConsultByConsultIds(consultIds)).thenReturn(2);

        AjaxResult result = controller.remove(consultIds);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiVideoConsultService).deleteAiVideoConsultByConsultIds(consultIds);
    }

    @Test
    void 事件日志查询与记录()
    {
        List<AiVideoConsultLog> logs = new ArrayList<AiVideoConsultLog>();
        when(aiVideoConsultLogService.selectAiVideoConsultLogList(any(AiVideoConsultLog.class)))
                .thenReturn(logs);
        AiVideoConsultLog log = new AiVideoConsultLog();
        when(aiVideoConsultLogService.insertAiVideoConsultLog(log)).thenReturn(1);

        assertSame(logs, controller.getLogs(6L).get(AjaxResult.DATA_TAG));
        assertEquals(200, controller.addLog(log).get(AjaxResult.CODE_TAG));
        assertNotNull(log.getEventTime());
    }
}
