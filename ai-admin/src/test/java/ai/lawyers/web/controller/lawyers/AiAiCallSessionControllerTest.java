package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.system.domain.lawyers.AiAiCallSession;
import ai.lawyers.system.service.lawyers.IAiAiCallSessionService;

/**
 * AI 辅助会话接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiAiCallSessionControllerTest
{
    @Mock
    private IAiAiCallSessionService aiAiCallSessionService;

    @InjectMocks
    private AiAiCallSessionController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiAiCallSession query = new AiAiCallSession();
        List<AiAiCallSession> list = new ArrayList<AiAiCallSession>();
        list.add(new AiAiCallSession());
        when(aiAiCallSessionService.selectAiAiCallSessionList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(list, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(aiAiCallSessionService.selectAiAiCallSessionList(any(AiAiCallSession.class)))
                .thenReturn(List.of(new AiAiCallSession()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiAiCallSession());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情与按通话记录查询透传服务结果()
    {
        AiAiCallSession session = new AiAiCallSession();
        session.setSessionId(8L);
        AiAiCallSession byRecord = new AiAiCallSession();
        when(aiAiCallSessionService.selectAiAiCallSessionBySessionId(8L)).thenReturn(session);
        when(aiAiCallSessionService.selectAiAiCallSessionByRecordId(100L)).thenReturn(byRecord);

        assertSame(session, controller.getInfo(8L).get(AjaxResult.DATA_TAG));
        assertSame(byRecord, controller.getByRecord(100L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 分析推荐解析会话与通话小结()
    {
        Map<String, Object> param = new HashMap<String, Object>();
        param.put("sessionId", 8);
        param.put("callSummary", "客户咨询劳动仲裁时效");
        AiAiCallSession analyzed = new AiAiCallSession();
        when(aiAiCallSessionService.analyzeAndRecommend(8L, "客户咨询劳动仲裁时效")).thenReturn(analyzed);

        AjaxResult result = controller.analyze(param);

        assertSame(analyzed, result.get(AjaxResult.DATA_TAG));
        verify(aiAiCallSessionService).analyzeAndRecommend(8L, "客户咨询劳动仲裁时效");
    }

    @Test
    void 小结结束在缺省参数时传空值()
    {
        AiAiCallSession summarized = new AiAiCallSession();
        when(aiAiCallSessionService.summarizeAndEnd(null, null)).thenReturn(summarized);

        AjaxResult result = controller.summarize(new HashMap<String, Object>());

        assertSame(summarized, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] sessionIds = new Long[] { 4L, 5L };
        when(aiAiCallSessionService.deleteAiAiCallSessionBySessionIds(sessionIds)).thenReturn(2);

        AjaxResult result = controller.remove(sessionIds);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiAiCallSessionService).deleteAiAiCallSessionBySessionIds(sessionIds);
    }
}
