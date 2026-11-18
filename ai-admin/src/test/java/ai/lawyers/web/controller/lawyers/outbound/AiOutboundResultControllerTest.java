package ai.lawyers.web.controller.lawyers.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundResult;
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundResultService;

/**
 * 外呼结果接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiOutboundResultControllerTest
{
    @Mock
    private IAiOutboundResultService aiOutboundResultService;

    @InjectMocks
    private AiOutboundResultController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiOutboundResult query = new AiOutboundResult();
        List<AiOutboundResult> results = new ArrayList<AiOutboundResult>();
        results.add(new AiOutboundResult());
        when(aiOutboundResultService.selectAiOutboundResultList(query)).thenReturn(results);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(results, table.getRows());
    }

    @Test
    void 任务被叫与统计查询透传()
    {
        AiOutboundResult result = new AiOutboundResult();
        result.setResultId(7L);
        List<AiOutboundResult> byTask = Arrays.asList(result);
        Map<String, Object> statistics = new HashMap<String, Object>();
        statistics.put("total", 10);
        when(aiOutboundResultService.selectAiOutboundResultByTaskId(5L)).thenReturn(byTask);
        when(aiOutboundResultService.selectAiOutboundResultByCalleeId(6L)).thenReturn(result);
        when(aiOutboundResultService.getTaskResultStatistics(5L)).thenReturn(statistics);

        assertSame(byTask, controller.getByTaskId(5L).get(AjaxResult.DATA_TAG));
        assertSame(result, controller.getByCalleeId(6L).get(AjaxResult.DATA_TAG));
        assertSame(statistics, controller.getTaskStatistics(5L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增写入操作人()
    {
        SysUser user = new SysUser();
        user.setUserName("outbound-bot");
        AiOutboundResult result = new AiOutboundResult();
        when(aiOutboundResultService.insertAiOutboundResult(any(AiOutboundResult.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(new LoginUser(11L, 12L, user, null));
            AjaxResult ajax = controller.add(result);
            assertEquals(200, ajax.get(AjaxResult.CODE_TAG));
        }
        assertEquals("outbound-bot", result.getCreateBy());
        verify(aiOutboundResultService).insertAiOutboundResult(result);
    }

    @Test
    void 导出写入Excel响应()
    {
        when(aiOutboundResultService.selectAiOutboundResultList(any(AiOutboundResult.class)))
                .thenReturn(Arrays.asList(new AiOutboundResult()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiOutboundResult());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }
}
