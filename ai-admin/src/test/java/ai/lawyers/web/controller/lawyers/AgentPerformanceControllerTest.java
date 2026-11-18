package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.system.domain.lawyers.stat.AgentPerformanceVO;
import ai.lawyers.system.service.lawyers.stat.IAgentPerformanceService;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 坐席效能报表接口测试：列表查询参数与导出响应。
 *
 * @author ai-lawyers
 */
class AgentPerformanceControllerTest
{
    private AgentPerformanceController controller;

    private IAgentPerformanceService agentPerformanceService;

    @BeforeEach
    void setUp()
    {
        agentPerformanceService = mock(IAgentPerformanceService.class);
        controller = new AgentPerformanceController();
        ReflectionTestUtils.setField(controller, "agentPerformanceService", agentPerformanceService);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 列表查询透传坐席名与时间范围()
    {
        AgentPerformanceVO vo = new AgentPerformanceVO();
        vo.setAgentName("张三");
        List<AgentPerformanceVO> rows = List.of(vo);
        when(agentPerformanceService.selectAgentPerformanceList("张三",
                DateUtils.parseDate("2026-01-01"), DateUtils.parseDate("2026-01-02"))).thenReturn(rows);

        TableDataInfo table = controller.list("张三", "2026-01-01", "2026-01-02");

        assertEquals(1, table.getRows().size());
        assertEquals(1L, table.getTotal());
    }

    @Test
    void 未指定时间与坐席时按空条件查询()
    {
        when(agentPerformanceService.selectAgentPerformanceList(isNull(), isNull(), isNull())).thenReturn(List.of());

        TableDataInfo table = controller.list(null, null, null);

        assertTrue(table.getRows().isEmpty());
        verify(agentPerformanceService).selectAgentPerformanceList(isNull(), isNull(), isNull());
    }

    @Test
    void 导出报表写入Excel响应()
    {
        AgentPerformanceVO vo = new AgentPerformanceVO();
        vo.setAgentName("张三");
        when(agentPerformanceService.selectAgentPerformanceList(any(), any(), any())).thenReturn(List.of(vo));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, null, null, null);

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
        verify(agentPerformanceService).selectAgentPerformanceList(any(), any(), any());
    }
}
