package ai.lawyers.web.controller.lawyers;

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
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiSignRecord;
import ai.lawyers.system.service.lawyers.IAiSignRecordService;

/**
 * 签署记录接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiSignRecordControllerTest
{
    @Mock
    private IAiSignRecordService aiSignRecordService;

    @InjectMocks
    private AiSignRecordController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiSignRecord query = new AiSignRecord();
        List<AiSignRecord> records = new ArrayList<AiSignRecord>();
        records.add(new AiSignRecord());
        when(aiSignRecordService.selectAiSignRecordList(query)).thenReturn(records);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(records, table.getRows());
    }

    @Test
    void 详情透传服务结果()
    {
        AiSignRecord record = new AiSignRecord();
        record.setSignId(12L);
        when(aiSignRecordService.selectAiSignRecordBySignId(12L)).thenReturn(record);

        AjaxResult result = controller.getInfo(12L);

        assertSame(record, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增写入操作人()
    {
        SysUser user = new SysUser();
        user.setUserName("sign-admin");
        AiSignRecord record = new AiSignRecord();
        when(aiSignRecordService.insertAiSignRecord(any(AiSignRecord.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(new LoginUser(5L, 6L, user, null));
            AjaxResult result = controller.add(record);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("sign-admin", record.getCreateBy());
        verify(aiSignRecordService).insertAiSignRecord(record);
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] signIds = new Long[] { 3L, 4L };
        when(aiSignRecordService.deleteAiSignRecordBySignIds(signIds)).thenReturn(2);

        AjaxResult result = controller.remove(signIds);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiSignRecordService).deleteAiSignRecordBySignIds(signIds);
    }
}
