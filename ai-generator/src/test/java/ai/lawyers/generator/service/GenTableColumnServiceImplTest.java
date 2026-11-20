package ai.lawyers.generator.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ai.lawyers.generator.domain.GenTableColumn;
import ai.lawyers.generator.mapper.GenTableColumnMapper;

/**
 * 代码生成字段服务单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class GenTableColumnServiceImplTest
{
    @Mock
    private GenTableColumnMapper genTableColumnMapper;

    @InjectMocks
    private GenTableColumnServiceImpl genTableColumnService;

    private GenTableColumn column(String columnName)
    {
        GenTableColumn column = new GenTableColumn();
        column.setColumnName(columnName);
        column.setJavaField(columnName);
        return column;
    }

    @Test
    void 查询字段列表委托Mapper()
    {
        List<GenTableColumn> list = Arrays.asList(column("id"), column("name"));
        when(genTableColumnMapper.selectGenTableColumnListByTableId(1L)).thenReturn(list);

        assertSame(list, genTableColumnService.selectGenTableColumnListByTableId(1L));
    }

    @Test
    void 新增与修改字段返回影响行数()
    {
        GenTableColumn column = column("name");
        when(genTableColumnMapper.insertGenTableColumn(column)).thenReturn(1);
        when(genTableColumnMapper.updateGenTableColumn(column)).thenReturn(2);

        assertEquals(1, genTableColumnService.insertGenTableColumn(column));
        assertEquals(2, genTableColumnService.updateGenTableColumn(column));
    }

    @Test
    void 按逗号串删除字段时转换为ID数组()
    {
        when(genTableColumnMapper.deleteGenTableColumnByIds(any(Long[].class))).thenReturn(2);

        assertEquals(2, genTableColumnService.deleteGenTableColumnByIds("1,2"));

        ArgumentCaptor<Long[]> captor = ArgumentCaptor.forClass(Long[].class);
        verify(genTableColumnMapper).deleteGenTableColumnByIds(captor.capture());
        assertArrayEquals(new Long[] { 1L, 2L }, captor.getValue());
    }
}
