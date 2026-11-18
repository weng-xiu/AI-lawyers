package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.system.domain.lawyers.stat.AiSatisfactionForecast;
import ai.lawyers.system.service.lawyers.IAiSatisfactionForecastService;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 预测性满意度接口测试：区间汇总与明细分页。
 *
 * @author ai-lawyers
 */
class AiSatisfactionForecastControllerTest
{
    private AiSatisfactionForecastController controller;

    private IAiSatisfactionForecastService forecastService;

    @BeforeEach
    void setUp()
    {
        forecastService = mock(IAiSatisfactionForecastService.class);
        controller = new AiSatisfactionForecastController();
        ReflectionTestUtils.setField(controller, "forecastService", forecastService);
    }

    @Test
    void 区间汇总返回服务结果()
    {
        Date begin = new Date(1000L);
        Date end = new Date(2000L);
        Map<String, Object> summary = Map.of("avgScore", 4.6D);
        when(forecastService.summary(begin, end)).thenReturn(summary);

        AjaxResult result = controller.summary(begin, end);

        assertSame(summary, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 明细分页透传条数与分页参数()
    {
        Date begin = new Date(1000L);
        Date end = new Date(2000L);
        List<AiSatisfactionForecast> rows = List.of(new AiSatisfactionForecast(), new AiSatisfactionForecast());
        when(forecastService.list(begin, end, 2, 10)).thenReturn(rows);

        TableDataInfo table = controller.list(begin, end, 2, 10);

        assertEquals(2, table.getRows().size());
        assertEquals(2L, table.getTotal());
        verify(forecastService).list(begin, end, 2, 10);
    }
}
