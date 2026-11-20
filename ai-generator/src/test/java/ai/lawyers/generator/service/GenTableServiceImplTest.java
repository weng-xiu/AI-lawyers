package ai.lawyers.generator.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import ai.lawyers.common.constant.Constants;
import ai.lawyers.common.constant.GenConstants;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.generator.domain.GenTable;
import ai.lawyers.generator.domain.GenTableColumn;
import ai.lawyers.generator.mapper.GenTableColumnMapper;
import ai.lawyers.generator.mapper.GenTableMapper;
import ai.lawyers.generator.util.GenUtils;
import ai.lawyers.generator.util.VelocityInitializer;
import ai.lawyers.generator.util.VelocityUtils;

/**
 * 代码生成业务服务单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class GenTableServiceImplTest
{
    @Mock
    private GenTableMapper genTableMapper;

    @Mock
    private GenTableColumnMapper genTableColumnMapper;

    @InjectMocks
    private GenTableServiceImpl genTableService;

    private GenTable table(String tableName)
    {
        GenTable table = new GenTable();
        table.setTableId(1L);
        table.setTableName(tableName);
        table.setClassName("Demo");
        table.setTplCategory(GenConstants.TPL_CRUD);
        table.setColumns(new ArrayList<GenTableColumn>());
        return table;
    }

    private GenTableColumn column(String columnName, boolean pk)
    {
        GenTableColumn column = new GenTableColumn();
        column.setColumnName(columnName);
        column.setJavaField(columnName);
        column.setIsPk(pk ? "1" : "0");
        return column;
    }

    @Test
    void 查询表信息时解析生成选项()
    {
        GenTable table = table("sys_demo");
        table.setOptions("{\"treeCode\":\"id\",\"treeParentCode\":\"parentId\",\"treeName\":\"name\","
                + "\"parentMenuId\":\"100\",\"parentMenuName\":\"系统工具\"}");
        when(genTableMapper.selectGenTableById(1L)).thenReturn(table);

        GenTable result = genTableService.selectGenTableById(1L);

        assertEquals("id", result.getTreeCode());
        assertEquals("parentId", result.getTreeParentCode());
        assertEquals("name", result.getTreeName());
        assertEquals(100L, result.getParentMenuId());
        assertEquals("系统工具", result.getParentMenuName());
    }

    @Test
    void 查询表信息时选项为空不解析()
    {
        GenTable table = table("sys_demo");
        when(genTableMapper.selectGenTableById(1L)).thenReturn(table);

        GenTable result = genTableService.selectGenTableById(1L);

        assertNull(result.getTreeCode());
        assertNull(result.getParentMenuId());
    }

    @Test
    void 各类列表查询委托Mapper()
    {
        GenTable query = new GenTable();
        List<GenTable> list = Collections.singletonList(table("sys_demo"));
        when(genTableMapper.selectGenTableList(query)).thenReturn(list);
        when(genTableMapper.selectDbTableList(query)).thenReturn(list);
        when(genTableMapper.selectDbTableListByNames(any(String[].class))).thenReturn(list);
        when(genTableMapper.selectGenTableAll()).thenReturn(list);

        assertSame(list, genTableService.selectGenTableList(query));
        assertSame(list, genTableService.selectDbTableList(query));
        assertSame(list, genTableService.selectDbTableListByNames(new String[] { "sys_demo" }));
        assertSame(list, genTableService.selectGenTableAll());
    }

    @Test
    void 建表影响行数决定返回结果()
    {
        when(genTableMapper.createTable("create table t1")).thenReturn(0);
        when(genTableMapper.createTable("create table t2")).thenReturn(1);

        assertTrue(genTableService.createTable("create table t1"));
        assertFalse(genTableService.createTable("create table t2"));
    }

    @Test
    void 更新业务成功后同步更新字段()
    {
        GenTable table = table("sys_demo");
        GenTableColumn first = column("id", true);
        GenTableColumn second = column("name", false);
        table.setColumns(Arrays.asList(first, second));
        when(genTableMapper.updateGenTable(table)).thenReturn(1);

        genTableService.updateGenTable(table);

        verify(genTableColumnMapper).updateGenTableColumn(first);
        verify(genTableColumnMapper).updateGenTableColumn(second);
    }

    @Test
    void 更新业务未成功时不更新字段()
    {
        GenTable table = table("sys_demo");
        table.setColumns(Collections.singletonList(column("id", true)));
        when(genTableMapper.updateGenTable(table)).thenReturn(0);

        genTableService.updateGenTable(table);

        verify(genTableColumnMapper, never()).updateGenTableColumn(any(GenTableColumn.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 删除业务同时删除字段()
    {
        Long[] tableIds = new Long[] { 1L, 2L };

        genTableService.deleteGenTableByIds(tableIds);

        ArgumentCaptor<Long[]> captor = ArgumentCaptor.forClass(Long[].class);
        verify(genTableMapper).deleteGenTableByIds(captor.capture());
        assertArrayEquals(tableIds, captor.getValue());
        verify(genTableColumnMapper).deleteGenTableColumnByIds(eq(tableIds));
    }

    @Test
    void 导入表结构保存表与字段()
    {
        GenTable table = table("sys_demo");
        GenTableColumn column = column("id", true);
        when(genTableMapper.insertGenTable(table)).thenReturn(1);
        when(genTableColumnMapper.selectDbTableColumnsByName("sys_demo")).thenReturn(Collections.singletonList(column));

        try (MockedStatic<GenUtils> mocked = mockStatic(GenUtils.class))
        {
            genTableService.importGenTable(Collections.singletonList(table), "admin");

            mocked.verify(() -> GenUtils.initTable(table, "admin"));
            mocked.verify(() -> GenUtils.initColumnField(column, table));
        }
        verify(genTableColumnMapper).insertGenTableColumn(column);
    }

    @Test
    void 导入表结构失败包装为服务异常()
    {
        GenTable table = table("sys_demo");
        when(genTableMapper.insertGenTable(table)).thenThrow(new RuntimeException("数据库异常"));

        try (MockedStatic<GenUtils> ignored = mockStatic(GenUtils.class))
        {
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> genTableService.importGenTable(Collections.singletonList(table), "admin"));
            assertTrue(exception.getMessage().contains("导入失败"));
            assertTrue(exception.getMessage().contains("数据库异常"));
        }
    }

    @Test
    void 预览代码按模板渲染返回内容()
    {
        GenTable table = table("sys_demo");
        table.setColumns(Collections.singletonList(column("id", true)));
        String templateName = "java/domain.java.vm";
        when(genTableMapper.selectGenTableById(1L)).thenReturn(table);

        try (MockedStatic<VelocityInitializer> initializer = mockStatic(VelocityInitializer.class);
                MockedStatic<VelocityUtils> utils = mockStatic(VelocityUtils.class);
                MockedStatic<Velocity> velocity = mockStatic(Velocity.class))
        {
            VelocityContext context = new VelocityContext();
            Template template = mock(Template.class);
            utils.when(() -> VelocityUtils.prepareContext(table)).thenReturn(context);
            utils.when(() -> VelocityUtils.getTemplateList(GenConstants.TPL_CRUD, null))
                    .thenReturn(Collections.singletonList(templateName));
            velocity.when(() -> Velocity.getTemplate(templateName, Constants.UTF8)).thenReturn(template);

            Map<String, String> data = genTableService.previewCode(1L);

            assertEquals(1, data.size());
            assertTrue(data.containsKey(templateName));
            verify(template).merge(eq(context), any(StringWriter.class));
        }
    }

    @Test
    void 预览代码模板为空时返回空集合()
    {
        GenTable table = table("sys_demo");
        table.setColumns(Collections.singletonList(column("id", true)));
        when(genTableMapper.selectGenTableById(1L)).thenReturn(table);

        try (MockedStatic<VelocityInitializer> initializer = mockStatic(VelocityInitializer.class);
                MockedStatic<VelocityUtils> utils = mockStatic(VelocityUtils.class))
        {
            utils.when(() -> VelocityUtils.prepareContext(table)).thenReturn(new VelocityContext());
            utils.when(() -> VelocityUtils.getTemplateList(GenConstants.TPL_CRUD, null))
                    .thenReturn(Collections.emptyList());

            assertTrue(genTableService.previewCode(1L).isEmpty());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void 同步数据库时更新新增并删除字段()
    {
        GenTable table = table("sys_demo");
        GenTableColumn existing = column("id", true);
        existing.setColumnId(11L);
        existing.setIsList("1");
        existing.setDictType("sys_status");
        existing.setQueryType("EQ");
        GenTableColumn removed = column("old_col", false);
        table.setColumns(new ArrayList<>(Arrays.asList(existing, removed)));
        when(genTableMapper.selectGenTableByName("sys_demo")).thenReturn(table);

        GenTableColumn dbExisting = column("id", true);
        dbExisting.setIsList("1");
        GenTableColumn dbNew = column("new_col", false);
        when(genTableColumnMapper.selectDbTableColumnsByName("sys_demo"))
                .thenReturn(Arrays.asList(dbExisting, dbNew));

        try (MockedStatic<GenUtils> ignored = mockStatic(GenUtils.class))
        {
            genTableService.synchDb("sys_demo");
        }

        assertEquals(11L, dbExisting.getColumnId());
        assertEquals("sys_status", dbExisting.getDictType());
        assertEquals("EQ", dbExisting.getQueryType());
        verify(genTableColumnMapper).updateGenTableColumn(dbExisting);
        verify(genTableColumnMapper).insertGenTableColumn(dbNew);

        ArgumentCaptor<List<GenTableColumn>> captor = ArgumentCaptor.forClass(List.class);
        verify(genTableColumnMapper).deleteGenTableColumns(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("old_col", captor.getValue().get(0).getColumnName());
    }

    @Test
    void 同步数据库时原表结构不存在抛出异常()
    {
        GenTable table = table("sys_demo");
        table.setColumns(Collections.singletonList(column("id", true)));
        when(genTableMapper.selectGenTableByName("sys_demo")).thenReturn(table);
        when(genTableColumnMapper.selectDbTableColumnsByName("sys_demo")).thenReturn(Collections.emptyList());

        ServiceException exception = assertThrows(ServiceException.class, () -> genTableService.synchDb("sys_demo"));
        assertEquals("同步数据失败，原表结构不存在", exception.getMessage());
    }

    @Test
    void 修改校验树表必填项()
    {
        GenTable table = new GenTable();
        table.setTplCategory(GenConstants.TPL_TREE);
        assertEquals("树编码字段不能为空",
                assertThrows(ServiceException.class, () -> genTableService.validateEdit(table)).getMessage());

        table.getParams().put(GenConstants.TREE_CODE, "id");
        assertEquals("树父编码字段不能为空",
                assertThrows(ServiceException.class, () -> genTableService.validateEdit(table)).getMessage());

        table.getParams().put(GenConstants.TREE_PARENT_CODE, "parentId");
        assertEquals("树名称字段不能为空",
                assertThrows(ServiceException.class, () -> genTableService.validateEdit(table)).getMessage());
    }

    @Test
    void 修改校验主子表必填项()
    {
        GenTable table = new GenTable();
        table.setTplCategory(GenConstants.TPL_SUB);
        assertEquals("关联子表的表名不能为空",
                assertThrows(ServiceException.class, () -> genTableService.validateEdit(table)).getMessage());

        table.setSubTableName("sub_demo");
        assertEquals("子表关联的外键名不能为空",
                assertThrows(ServiceException.class, () -> genTableService.validateEdit(table)).getMessage());

        table.setSubTableFkName("demo_id");
        genTableService.validateEdit(table);
    }

    @Test
    void 单表模板无需必填校验()
    {
        GenTable table = new GenTable();
        table.setTplCategory(GenConstants.TPL_CRUD);

        genTableService.validateEdit(table);
    }

    @Test
    void 主键列优先取主键字段否则取首列()
    {
        GenTable withPk = table("sys_demo");
        GenTableColumn id = column("id", true);
        withPk.setColumns(Arrays.asList(column("name", false), id));
        genTableService.setPkColumn(withPk);
        assertSame(id, withPk.getPkColumn());

        GenTable withoutPk = table("sys_demo");
        GenTableColumn first = column("name", false);
        withoutPk.setColumns(Collections.singletonList(first));
        genTableService.setPkColumn(withoutPk);
        assertSame(first, withoutPk.getPkColumn());
    }

    @Test
    void 主子表模板同时设置子表主键列()
    {
        GenTable sub = table("sub_demo");
        GenTableColumn subId = column("id", true);
        sub.setColumns(Arrays.asList(column("name", false), subId));

        GenTable table = table("sys_demo");
        table.setTplCategory(GenConstants.TPL_SUB);
        table.setColumns(Collections.singletonList(column("id", true)));
        table.setSubTable(sub);

        genTableService.setPkColumn(table);

        assertSame(subId, sub.getPkColumn());
    }

    @Test
    void 配置子表名时查询子表信息()
    {
        GenTable table = table("sys_demo");
        table.setSubTableName("sub_demo");
        GenTable sub = table("sub_demo");
        when(genTableMapper.selectGenTableByName("sub_demo")).thenReturn(sub);

        genTableService.setSubTable(table);

        assertSame(sub, table.getSubTable());
    }

    @Test
    void 未配置子表名时不查询子表()
    {
        GenTable table = table("sys_demo");

        genTableService.setSubTable(table);

        assertNull(table.getSubTable());
        verify(genTableMapper, never()).selectGenTableByName(any(String.class));
    }

    @Test
    void 生成路径按默认目录或自定义目录拼接()
    {
        GenTable table = table("sys_demo");
        String template = "java/domain.java.vm";

        try (MockedStatic<VelocityUtils> utils = mockStatic(VelocityUtils.class))
        {
            utils.when(() -> VelocityUtils.getFileName(template, table)).thenReturn("Demo.java");

            table.setGenPath("/");
            assertEquals(System.getProperty("user.dir") + File.separator + "src" + File.separator + "Demo.java",
                    GenTableServiceImpl.getGenPath(table, template));

            table.setGenPath("D:" + File.separator + "gen");
            assertEquals("D:" + File.separator + "gen" + File.separator + "Demo.java",
                    GenTableServiceImpl.getGenPath(table, template));
        }
    }
}
