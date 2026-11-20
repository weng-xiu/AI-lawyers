package ai.lawyers.generator.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.generator.config.GenConfig;
import ai.lawyers.generator.domain.GenTable;
import ai.lawyers.generator.domain.GenTableColumn;
import ai.lawyers.generator.service.IGenTableColumnService;
import ai.lawyers.generator.service.IGenTableService;

/**
 * 代码生成接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class GenControllerTest
{
    @Mock
    private IGenTableService genTableService;

    @Mock
    private IGenTableColumnService genTableColumnService;

    @InjectMocks
    private GenController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 生成表列表分页返回()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        GenTable query = new GenTable();
        List<GenTable> list = new ArrayList<GenTable>();
        list.add(new GenTable());
        when(genTableService.selectGenTableList(query)).thenReturn(list);

        TableDataInfo table = controller.genList(query);

        assertEquals(200, table.getCode());
        assertSame(list, table.getRows());
    }

    @Test
    void 数据库表列表分页返回()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        GenTable query = new GenTable();
        List<GenTable> list = new ArrayList<GenTable>();
        when(genTableService.selectDbTableList(query)).thenReturn(list);

        assertSame(list, controller.dataList(query).getRows());
    }

    @Test
    void 生成信息聚合详情()
    {
        GenTable table = new GenTable();
        List<GenTable> tables = new ArrayList<GenTable>();
        List<GenTableColumn> columns = new ArrayList<GenTableColumn>();
        when(genTableService.selectGenTableById(3L)).thenReturn(table);
        when(genTableService.selectGenTableAll()).thenReturn(tables);
        when(genTableColumnService.selectGenTableColumnListByTableId(3L)).thenReturn(columns);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) controller.getInfo(3L).get(AjaxResult.DATA_TAG);

        assertSame(table, data.get("info"));
        assertSame(columns, data.get("rows"));
        assertSame(tables, data.get("tables"));
    }

    @Test
    void 字段列表返回不分页总数()
    {
        List<GenTableColumn> columns = new ArrayList<GenTableColumn>();
        columns.add(new GenTableColumn());
        when(genTableColumnService.selectGenTableColumnListByTableId(3L)).thenReturn(columns);

        TableDataInfo table = controller.columnList(3L);

        assertSame(columns, table.getRows());
        assertEquals(1, table.getTotal());
    }

    @Test
    void 导入表结构写入操作人()
    {
        List<GenTable> tableList = new ArrayList<GenTable>();
        when(genTableService.selectDbTableListByNames(any(String[].class))).thenReturn(tableList);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn("admin");
            assertEquals(200, controller.importTableSave("a,b").get(AjaxResult.CODE_TAG));
        }
        ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(genTableService).selectDbTableListByNames(captor.capture());
        assertArrayEquals(new String[] { "a", "b" }, captor.getValue());
        verify(genTableService).importGenTable(tableList, "admin");
    }

    @Test
    void 创建表结构成功与异常()
    {
        List<GenTable> tableList = new ArrayList<GenTable>();
        when(genTableService.createTable(anyString())).thenReturn(true);
        when(genTableService.selectDbTableListByNames(any(String[].class))).thenReturn(tableList);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn("admin");
            assertEquals(200, controller.createTableSave("CREATE TABLE `gen_test` (id bigint)").get(AjaxResult.CODE_TAG));
        }
        verify(genTableService).importGenTable(tableList, "admin");

        assertEquals("创建表结构异常", controller.createTableSave("DROP TABLE gen_test").get(AjaxResult.MSG_TAG));
    }

    @Test
    void 修改与删除生成配置()
    {
        GenTable table = new GenTable();
        Long[] ids = new Long[] { 3L };

        assertEquals(200, controller.editSave(table).get(AjaxResult.CODE_TAG));
        verify(genTableService).validateEdit(table);
        verify(genTableService).updateGenTable(table);

        assertEquals(200, controller.remove(ids).get(AjaxResult.CODE_TAG));
        verify(genTableService).deleteGenTableByIds(ids);
    }

    @Test
    void 预览代码透传模板内容() throws Exception
    {
        Map<String, String> code = Map.of("domain.java", "class Demo {}");
        when(genTableService.previewCode(3L)).thenReturn(code);

        assertSame(code, controller.preview(3L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 下载代码返回zip响应() throws Exception
    {
        byte[] data = "zip-bytes".getBytes();
        when(genTableService.downloadCode("gen_test")).thenReturn(data);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.download(response, "gen_test");

        assertEquals("application/octet-stream; charset=UTF-8", response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").contains("ruoyi.zip"));
        assertArrayEquals(data, response.getContentAsByteArray());
    }

    @Test
    void 生成代码受覆盖开关控制()
    {
        try (MockedStatic<GenConfig> mocked = mockStatic(GenConfig.class))
        {
            mocked.when(GenConfig::isAllowOverwrite).thenReturn(false);
            assertEquals("【系统预设】不允许生成文件覆盖到本地",
                    controller.genCode("gen_test").get(AjaxResult.MSG_TAG));
        }

        try (MockedStatic<GenConfig> mocked = mockStatic(GenConfig.class))
        {
            mocked.when(GenConfig::isAllowOverwrite).thenReturn(true);
            assertEquals(200, controller.genCode("gen_test").get(AjaxResult.CODE_TAG));
        }
        verify(genTableService).generatorCode("gen_test");
    }

    @Test
    void 同步数据库与批量下载代码() throws Exception
    {
        byte[] data = "batch-zip".getBytes();
        when(genTableService.downloadCode(any(String[].class))).thenReturn(data);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertEquals(200, controller.synchDb("gen_test").get(AjaxResult.CODE_TAG));
        verify(genTableService).synchDb("gen_test");

        controller.batchGenCode(response, "a,b");

        assertArrayEquals(data, response.getContentAsByteArray());
        ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(genTableService).downloadCode(captor.capture());
        assertArrayEquals(new String[] { "a", "b" }, captor.getValue());
    }
}
