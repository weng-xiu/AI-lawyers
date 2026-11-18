package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.risk.AiCallRiskRecord;
import ai.lawyers.system.service.lawyers.IAiCallBehaviorRiskService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 呼叫行为风控接口测试：列表、手动扫描与复核人记录。
 *
 * @author ai-lawyers
 */
class AiCallBehaviorRiskControllerTest
{
    private AiCallBehaviorRiskController controller;

    private IAiCallBehaviorRiskService riskService;

    @BeforeEach
    void setUp()
    {
        riskService = mock(IAiCallBehaviorRiskService.class);
        controller = new AiCallBehaviorRiskController();
        ReflectionTestUtils.setField(controller, "riskService", riskService);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 风控列表返回分页数据()
    {
        AiCallRiskRecord record = new AiCallRiskRecord();
        record.setRiskId(1L);
        when(riskService.selectRiskList(record)).thenReturn(List.of(record));

        TableDataInfo table = controller.list(record);

        assertEquals(1, table.getRows().size());
        assertSame(record, table.getRows().get(0));
    }

    @Test
    void 手动扫描返回扫描条数()
    {
        when(riskService.scanRisk()).thenReturn(3);

        AjaxResult result = controller.scan();

        assertEquals(3, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 复核记录当前登录人并返回影响行数()
    {
        AiCallRiskRecord review = new AiCallRiskRecord();
        review.setRiskId(1L);
        when(riskService.reviewRisk(review)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = Mockito.mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn("admin");

            AjaxResult result = controller.review(review);

            assertTrue(result.isSuccess());
            assertEquals("admin", review.getReviewBy());
            verify(riskService).reviewRisk(review);
        }
    }
}
