package ai.lawyers.web.controller.lawyers.trunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.mapper.lawyers.AiCallRecordArchiveMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 话单归档查询接口测试：列表、详情与归档规模统计。
 *
 * @author ai-lawyers
 */
class CallArchiveControllerTest
{
    private CallArchiveController controller;

    private AiCallRecordArchiveMapper archiveMapper;

    @BeforeEach
    void setUp()
    {
        archiveMapper = mock(AiCallRecordArchiveMapper.class);
        controller = new CallArchiveController();
        ReflectionTestUtils.setField(controller, "archiveMapper", archiveMapper);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 归档列表返回分页数据()
    {
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(1L);
        when(archiveMapper.selectArchiveList(record)).thenReturn(List.of(record));

        TableDataInfo table = controller.list(record);

        assertEquals(1, table.getRows().size());
        assertEquals(1L, table.getTotal());
        assertSame(record, table.getRows().get(0));
    }

    @Test
    void 归档详情按记录ID返回()
    {
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(1L);
        when(archiveMapper.selectArchiveByRecordId(1L)).thenReturn(record);

        AjaxResult result = controller.detail(1L);

        assertSame(record, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 归档规模返回总量()
    {
        when(archiveMapper.countArchive()).thenReturn(128L);

        AjaxResult result = controller.stat();

        assertEquals(128L, result.get(AjaxResult.DATA_TAG));
        assertTrue(result.isSuccess());
    }
}
