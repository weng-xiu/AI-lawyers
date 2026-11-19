package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiMissedCall;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.IAiMissedCallService;

/**
 * 未接来电接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiMissedCallControllerTest
{
    @Mock
    private IAiMissedCallService missedCallService;

    @Mock
    private IAiCallTicketService callTicketService;

    @InjectMocks
    private AiMissedCallController controller;

    @TempDir
    Path tempDir;

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

    private AiMissedCall missedCall()
    {
        AiMissedCall missedCall = new AiMissedCall();
        missedCall.setMissedCallId(7L);
        missedCall.setCallerNumber("13800000000");
        missedCall.setCallerName("张三");
        missedCall.setCallTime(new Date());
        missedCall.setRecordId(100L);
        return missedCall;
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiMissedCall query = new AiMissedCall();
        List<AiMissedCall> list = new ArrayList<AiMissedCall>();
        list.add(missedCall());
        when(missedCallService.selectAiMissedCallList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(list, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(missedCallService.selectAiMissedCallList(any(AiMissedCall.class))).thenReturn(List.of(missedCall()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiMissedCall());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情透传服务结果()
    {
        AiMissedCall missedCall = missedCall();
        when(missedCallService.selectAiMissedCallByMissedCallId(7L)).thenReturn(missedCall);

        assertSame(missedCall, controller.getInfo(7L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增写操作人()
    {
        AiMissedCall missedCall = missedCall();
        when(missedCallService.insertAiMissedCall(missedCall)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            assertEquals(200, controller.add(missedCall).get(AjaxResult.CODE_TAG));
        }
        assertEquals("agent", missedCall.getCreateBy());
    }

    @Test
    void 修改写更新人()
    {
        AiMissedCall missedCall = missedCall();
        when(missedCallService.updateAiMissedCall(missedCall)).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            assertEquals(500, controller.edit(missedCall).get(AjaxResult.CODE_TAG));
        }
        assertEquals("agent", missedCall.getUpdateBy());
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(missedCallService.deleteAiMissedCallByMissedCallIds(ids)).thenReturn(2);

        assertEquals(200, controller.remove(ids).get(AjaxResult.CODE_TAG));
        verify(missedCallService).deleteAiMissedCallByMissedCallIds(ids);
    }

    @Test
    void 统计透传服务结果()
    {
        Map<String, Object> stats = new HashMap<String, Object>();
        stats.put("today", 2);
        when(missedCallService.selectMissedCallStats()).thenReturn(stats);

        assertSame(stats, controller.getStats().get(AjaxResult.DATA_TAG));
    }

    @Test
    void 回拨标记写入回调信息()
    {
        when(missedCallService.updateAiMissedCall(any(AiMissedCall.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            assertEquals(200, controller.callback(7L).get(AjaxResult.CODE_TAG));
        }
        ArgumentCaptor<AiMissedCall> captor = ArgumentCaptor.forClass(AiMissedCall.class);
        verify(missedCallService).updateAiMissedCall(captor.capture());
        assertEquals("1", captor.getValue().getStatus());
        assertEquals("agent", captor.getValue().getCallbackBy());
        assertNotNull(captor.getValue().getCallbackTime());
        assertEquals("agent", captor.getValue().getUpdateBy());
    }

    @Test
    void 语音留言转工单记录不存在()
    {
        when(missedCallService.selectAiMissedCallByMissedCallId(7L)).thenReturn(null);

        AjaxResult result = controller.transferVoiceToTicket(7L);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("未接来电记录不存在", result.get(AjaxResult.MSG_TAG));
        verify(callTicketService, never()).insertAiCallTicket(any(AiCallTicket.class));
    }

    @Test
    void 语音留言为空拒绝转工单()
    {
        AiMissedCall missedCall = missedCall();
        when(missedCallService.selectAiMissedCallByMissedCallId(7L)).thenReturn(missedCall);

        AjaxResult result = controller.transferVoiceToTicket(7L);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("该记录无语音留言，无需转工单", result.get(AjaxResult.MSG_TAG));
        verify(callTicketService, never()).insertAiCallTicket(any(AiCallTicket.class));
    }

    @Test
    void 语音留言转工单成功并回写备注()
    {
        AiMissedCall missedCall = missedCall();
        missedCall.setVoiceContent("需要回访合同问题");
        missedCall.setVoiceDuration(15);
        when(missedCallService.selectAiMissedCallByMissedCallId(7L)).thenReturn(missedCall);
        when(callTicketService.insertAiCallTicket(any(AiCallTicket.class))).thenAnswer(invocation -> {
            AiCallTicket ticket = invocation.getArgument(0);
            ticket.setTicketNo("TK-1001");
            return 1;
        });

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            AjaxResult result = controller.transferVoiceToTicket(7L);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            assertEquals("TK-1001", result.get(AjaxResult.MSG_TAG));
        }
        ArgumentCaptor<AiCallTicket> ticketCaptor = ArgumentCaptor.forClass(AiCallTicket.class);
        verify(callTicketService).insertAiCallTicket(ticketCaptor.capture());
        assertEquals("语音留言转办-张三", ticketCaptor.getValue().getTitle());
        assertEquals("13800000000", ticketCaptor.getValue().getCallerNumber());
        assertEquals("2", ticketCaptor.getValue().getPriority());
        assertEquals("agent", ticketCaptor.getValue().getCreateBy());
        assertTrue(ticketCaptor.getValue().getContent().contains("留言内容：需要回访合同问题"));
        assertTrue(ticketCaptor.getValue().getContent().contains("留言时长：15秒"));

        ArgumentCaptor<AiMissedCall> updateCaptor = ArgumentCaptor.forClass(AiMissedCall.class);
        verify(missedCallService).updateAiMissedCall(updateCaptor.capture());
        assertEquals("已转工单：TK-1001", updateCaptor.getValue().getRemark());
    }

    @Test
    void 播放留言记录不存在返回404() throws Exception
    {
        when(missedCallService.selectAiMissedCallByMissedCallId(7L)).thenReturn(null);

        ResponseEntity<?> response = controller.playVoice(7L, new MockHttpServletRequest());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void 播放留言文件不存在返回404() throws Exception
    {
        AiMissedCall missedCall = missedCall();
        missedCall.setVoiceFileUrl(tempDir.resolve("missing.wav").toString());
        when(missedCallService.selectAiMissedCallByMissedCallId(7L)).thenReturn(missedCall);

        ResponseEntity<?> response = controller.playVoice(7L, new MockHttpServletRequest());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void 播放留言返回完整文件流() throws Exception
    {
        Path file = tempDir.resolve("voice.wav");
        Files.write(file, new byte[] { 1, 2, 3, 4 });
        AiMissedCall missedCall = missedCall();
        missedCall.setVoiceFileUrl(file.toString());
        when(missedCallService.selectAiMissedCallByMissedCallId(7L)).thenReturn(missedCall);

        ResponseEntity<?> response = controller.playVoice(7L, new MockHttpServletRequest());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("bytes", response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES));
        assertEquals(4L, response.getHeaders().getContentLength());
    }

    @Test
    void 播放留言支持Range返回206() throws Exception
    {
        Path file = tempDir.resolve("voice.wav");
        Files.write(file, new byte[] { 1, 2, 3, 4 });
        AiMissedCall missedCall = missedCall();
        missedCall.setVoiceFileUrl(file.toString());
        when(missedCallService.selectAiMissedCallByMissedCallId(7L)).thenReturn(missedCall);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.RANGE, "bytes=0-1");

        ResponseEntity<?> response = controller.playVoice(7L, request);

        assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());
        assertEquals("bytes 0-1/4", response.getHeaders().getFirst("Content-Range"));
        assertEquals(2L, response.getHeaders().getContentLength());
    }

    @Test
    void 播放留言Range越界返回416() throws Exception
    {
        Path file = tempDir.resolve("voice.wav");
        Files.write(file, new byte[] { 1, 2, 3, 4 });
        AiMissedCall missedCall = missedCall();
        missedCall.setVoiceFileUrl(file.toString());
        when(missedCallService.selectAiMissedCallByMissedCallId(7L)).thenReturn(missedCall);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.RANGE, "bytes=99-100");

        ResponseEntity<?> response = controller.playVoice(7L, request);

        assertEquals(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, response.getStatusCode());
        assertEquals("bytes */4", response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));
    }

    @Test
    void 播放留言相对路径按录音目录解析() throws Exception
    {
        ReflectionTestUtils.setField(controller, "recordingBasePath", tempDir.toString());
        Files.write(tempDir.resolve("sub.wav"), new byte[] { 1, 2 });
        AiMissedCall missedCall = missedCall();
        missedCall.setVoiceFileUrl("sub.wav");
        when(missedCallService.selectAiMissedCallByMissedCallId(7L)).thenReturn(missedCall);

        ResponseEntity<?> response = controller.playVoice(7L, new MockHttpServletRequest());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2L, response.getHeaders().getContentLength());
    }
}
