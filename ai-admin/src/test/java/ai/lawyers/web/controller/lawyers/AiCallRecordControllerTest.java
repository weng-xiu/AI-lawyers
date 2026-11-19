package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
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
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;
import ai.lawyers.system.service.lawyers.storage.RecordingStorageService;
import ai.lawyers.system.service.lawyers.summary.IAiCallSummaryService;

/**
 * 来电记录接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiCallRecordControllerTest
{
    @Mock
    private IAiCallRecordService callRecordService;

    @Mock
    private IAiCallSummaryService callSummaryService;

    @Mock
    private RecordingStorageService recordingStorageService;

    @InjectMocks
    private AiCallRecordController controller;

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

    private AiCallRecord record()
    {
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(7L);
        record.setRecordFile("a.wav");
        return record;
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiCallRecord query = new AiCallRecord();
        List<AiCallRecord> list = new ArrayList<AiCallRecord>();
        list.add(record());
        when(callRecordService.selectAiCallRecordList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(list, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(callRecordService.selectAiCallRecordList(any(AiCallRecord.class))).thenReturn(List.of(record()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiCallRecord());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情透传服务结果()
    {
        AiCallRecord record = record();
        when(callRecordService.selectAiCallRecordByRecordId(7L)).thenReturn(record);

        assertSame(record, controller.getInfo(7L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增写操作人()
    {
        AiCallRecord record = record();
        when(callRecordService.insertAiCallRecord(record)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            assertEquals(200, controller.add(record).get(AjaxResult.CODE_TAG));
        }
        assertEquals("agent", record.getCreateBy());
    }

    @Test
    void 修改写更新人()
    {
        AiCallRecord record = record();
        when(callRecordService.updateAiCallRecord(record)).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            assertEquals(500, controller.edit(record).get(AjaxResult.CODE_TAG));
        }
        assertEquals("agent", record.getUpdateBy());
    }

    @Test
    void 批量删除按坐席查询与统计透传()
    {
        Long[] ids = new Long[] { 1L, 2L };
        List<AiCallRecord> agentRecords = List.of(record());
        Map<String, Object> statistics = new HashMap<String, Object>();
        List<Map<String, Object>> byAgent = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> byCategory = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> byDate = new ArrayList<Map<String, Object>>();
        Map<String, Object> workbench = new HashMap<String, Object>();
        when(callRecordService.deleteAiCallRecordByRecordIds(ids)).thenReturn(2);
        when(callRecordService.selectAiCallRecordByAgentId(3L)).thenReturn(agentRecords);
        when(callRecordService.getCallStatistics()).thenReturn(statistics);
        when(callRecordService.getCallStatisticsByAgent()).thenReturn(byAgent);
        when(callRecordService.getCallStatisticsByCategory()).thenReturn(byCategory);
        when(callRecordService.getCallStatisticsByDate(7)).thenReturn(byDate);
        when(callRecordService.getWorkbenchSummary()).thenReturn(workbench);

        assertEquals(200, controller.remove(ids).get(AjaxResult.CODE_TAG));
        assertSame(agentRecords, controller.getRecordsByAgentId(3L).get(AjaxResult.DATA_TAG));
        assertSame(statistics, controller.getStatistics().get(AjaxResult.DATA_TAG));
        assertSame(byAgent, controller.getStatisticsByAgent().get(AjaxResult.DATA_TAG));
        assertSame(byCategory, controller.getStatisticsByCategory().get(AjaxResult.DATA_TAG));
        assertSame(byDate, controller.getStatisticsByDate(7).get(AjaxResult.DATA_TAG));
        assertSame(workbench, controller.getWorkbenchSummary().get(AjaxResult.DATA_TAG));
    }

    @Test
    void AI小结话单不存在()
    {
        when(callSummaryService.generateSummary(7L, false)).thenReturn(null);

        AjaxResult result = controller.generateAiSummary(7L, false);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("话单不存在：7", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void AI小结失败返回失败原因()
    {
        AiCallRecord record = record();
        record.setAiSummaryStatus("3");
        record.setAiSummaryFailReason("模型超时");
        when(callSummaryService.generateSummary(7L, true)).thenReturn(record);

        assertEquals("模型超时", controller.generateAiSummary(7L, true).get(AjaxResult.MSG_TAG));
    }

    @Test
    void AI小结失败无原因返回默认提示()
    {
        AiCallRecord record = record();
        record.setAiSummaryStatus("3");
        when(callSummaryService.generateSummary(7L, false)).thenReturn(record);

        assertEquals("AI 小结生成失败，请稍后重试",
                controller.generateAiSummary(7L, false).get(AjaxResult.MSG_TAG));
    }

    @Test
    void AI小结生成中()
    {
        AiCallRecord record = record();
        record.setAiSummaryStatus("1");
        when(callSummaryService.generateSummary(7L, false)).thenReturn(record);

        assertEquals("小结正在生成中，请稍后刷新查看",
                controller.generateAiSummary(7L, false).get(AjaxResult.MSG_TAG));
    }

    @Test
    void AI小结成功返回话单()
    {
        AiCallRecord record = record();
        record.setAiSummaryStatus("2");
        when(callSummaryService.generateSummary(7L, false)).thenReturn(record);

        assertSame(record, controller.generateAiSummary(7L, false).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 播放录音记录不存在返回404() throws Exception
    {
        when(callRecordService.selectAiCallRecordByRecordId(7L)).thenReturn(null);

        ResponseEntity<?> response = controller.play(7L, new MockHttpServletRequest());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void 播放录音对象存储返回302() throws Exception
    {
        AiCallRecord record = record();
        when(callRecordService.selectAiCallRecordByRecordId(7L)).thenReturn(record);
        when(recordingStorageService.isObjectStorage()).thenReturn(true);
        when(recordingStorageService.getPlayUrl("a.wav", 3600)).thenReturn("https://oss.example.com/a.wav");

        ResponseEntity<?> response = controller.play(7L, new MockHttpServletRequest());

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals("https://oss.example.com/a.wav", response.getHeaders().getFirst(HttpHeaders.LOCATION));
    }

    @Test
    void 播放录音存储解密后流式返回() throws Exception
    {
        AiCallRecord record = record();
        when(callRecordService.selectAiCallRecordByRecordId(7L)).thenReturn(record);
        when(recordingStorageService.isObjectStorage()).thenReturn(false);
        when(recordingStorageService.load("a.wav")).thenReturn(new ByteArrayInputStream(new byte[] { 1, 2, 3, 4 }));

        ResponseEntity<?> response = controller.play(7L, new MockHttpServletRequest());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(4L, response.getHeaders().getContentLength());
        assertEquals("bytes", response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES));
    }

    @Test
    void 播放录音本地文件Range返回206() throws Exception
    {
        Path file = tempDir.resolve("local.wav");
        Files.write(file, new byte[] { 1, 2, 3, 4 });
        AiCallRecord record = record();
        record.setRecordFile(file.toString());
        when(callRecordService.selectAiCallRecordByRecordId(7L)).thenReturn(record);
        ReflectionTestUtils.setField(controller, "recordingStorageService", null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.RANGE, "bytes=0-1");

        ResponseEntity<?> response = controller.play(7L, request);

        assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());
        assertEquals("bytes 0-1/4", response.getHeaders().getFirst("Content-Range"));
    }

    @Test
    void 播放录音Range越界返回416() throws Exception
    {
        Path file = tempDir.resolve("local.wav");
        Files.write(file, new byte[] { 1, 2, 3, 4 });
        AiCallRecord record = record();
        record.setRecordFile(file.toString());
        when(callRecordService.selectAiCallRecordByRecordId(7L)).thenReturn(record);
        ReflectionTestUtils.setField(controller, "recordingStorageService", null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.RANGE, "bytes=99-");

        ResponseEntity<?> response = controller.play(7L, request);

        assertEquals(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, response.getStatusCode());
        assertEquals("bytes */4", response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));
    }

    @Test
    void 播放录音存储读取失败且本地缺失返回404() throws Exception
    {
        AiCallRecord record = record();
        record.setRecordFile(tempDir.resolve("missing.wav").toString());
        when(callRecordService.selectAiCallRecordByRecordId(7L)).thenReturn(record);
        when(recordingStorageService.isObjectStorage()).thenReturn(false);
        when(recordingStorageService.load(anyString())).thenThrow(new IOException("解密失败"));

        ResponseEntity<?> response = controller.play(7L, new MockHttpServletRequest());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void 下载录音对象存储返回302() throws Exception
    {
        AiCallRecord record = record();
        when(callRecordService.selectAiCallRecordByRecordId(7L)).thenReturn(record);
        when(recordingStorageService.isObjectStorage()).thenReturn(true);
        when(recordingStorageService.getPlayUrl("a.wav", 3600)).thenReturn("https://oss.example.com/a.wav");

        ResponseEntity<?> response = controller.download(7L);

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals("https://oss.example.com/a.wav", response.getHeaders().getFirst(HttpHeaders.LOCATION));
    }

    @Test
    void 下载录音存储解密后带附件头() throws Exception
    {
        AiCallRecord record = record();
        when(callRecordService.selectAiCallRecordByRecordId(7L)).thenReturn(record);
        when(recordingStorageService.isObjectStorage()).thenReturn(false);
        when(recordingStorageService.load("a.wav")).thenReturn(new ByteArrayInputStream(new byte[] { 1, 2 }));

        ResponseEntity<?> response = controller.download(7L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("attachment"));
        assertEquals(2L, response.getHeaders().getContentLength());
    }

    @Test
    void 下载录音本地文件直接返回() throws Exception
    {
        Path file = tempDir.resolve("报告.wav");
        Files.write(file, new byte[] { 1, 2, 3 });
        AiCallRecord record = record();
        record.setRecordFile(file.toString());
        when(callRecordService.selectAiCallRecordByRecordId(7L)).thenReturn(record);
        ReflectionTestUtils.setField(controller, "recordingStorageService", null);

        ResponseEntity<?> response = controller.download(7L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("attachment"));
        assertEquals(3L, response.getHeaders().getContentLength());
    }

    @Test
    void 下载录音无文件返回404() throws Exception
    {
        AiCallRecord record = record();
        record.setRecordFile(tempDir.resolve("missing.wav").toString());
        when(callRecordService.selectAiCallRecordByRecordId(7L)).thenReturn(record);
        ReflectionTestUtils.setField(controller, "recordingStorageService", null);

        ResponseEntity<?> response = controller.download(7L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
