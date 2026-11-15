package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.domain.lawyers.AiConsultationCategory;
import ai.lawyers.system.mapper.lawyers.CategoryMapper;

/**
 * 咨询分类服务单测。
 *
 * <p>覆盖分类增删改查、树形结构构建、名称唯一校验，以及导入时新增/更新/已存在/异常分支。</p>
 *
 * @author ai-lawyers
 */
class AiConsultationCategoryServiceImplTest
{
    private AiConsultationCategoryServiceImpl service;

    @Mock private CategoryMapper categoryMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiConsultationCategoryServiceImpl();
        ReflectionTestUtils.setField(service, "categoryMapper", categoryMapper);
    }

    private static AiConsultationCategory category(Long id, String name, Long parentId)
    {
        AiConsultationCategory category = new AiConsultationCategory();
        category.setCategoryId(id);
        category.setCategoryName(name);
        category.setParentId(parentId);
        return category;
    }

    @Test
    void selectAiConsultationCategoryByCategoryId_delegates()
    {
        AiConsultationCategory expected = new AiConsultationCategory();
        when(categoryMapper.selectCategoryById(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiConsultationCategoryByCategoryId(1L));
    }

    @Test
    void selectAiConsultationCategoryList_delegates()
    {
        AiConsultationCategory query = new AiConsultationCategory();
        List<AiConsultationCategory> list = Collections.singletonList(new AiConsultationCategory());
        when(categoryMapper.selectCategoryList(query)).thenReturn(list);

        assertSame(list, service.selectAiConsultationCategoryList(query));
    }

    @Test
    void insertAiConsultationCategory_delegates()
    {
        AiConsultationCategory category = new AiConsultationCategory();
        when(categoryMapper.insertCategory(category)).thenReturn(1);

        assertEquals(1, service.insertAiConsultationCategory(category));
    }

    @Test
    void updateAiConsultationCategory_delegates()
    {
        AiConsultationCategory category = new AiConsultationCategory();
        when(categoryMapper.updateCategory(category)).thenReturn(1);

        assertEquals(1, service.updateAiConsultationCategory(category));
    }

    @Test
    void deleteByCategoryIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(categoryMapper.deleteCategoryByIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiConsultationCategoryByCategoryIds(ids));
    }

    @Test
    void deleteByCategoryId_hasChildren_throws()
    {
        when(categoryMapper.countChildrenByParentId(1L)).thenReturn(1);

        assertEquals("该分类下存在子分类，不允许删除",
            assertThrows(ServiceException.class,
                () -> service.deleteAiConsultationCategoryByCategoryId(1L)).getMessage());
        verify(categoryMapper, never()).deleteCategoryById(any());
    }

    @Test
    void deleteByCategoryId_noChildren_delegates()
    {
        when(categoryMapper.countChildrenByParentId(1L)).thenReturn(0);
        when(categoryMapper.deleteCategoryById(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiConsultationCategoryByCategoryId(1L));
    }

    @Test
    void selectAllValidCategories_delegates()
    {
        List<AiConsultationCategory> list = Collections.singletonList(new AiConsultationCategory());
        when(categoryMapper.selectAllValidCategories()).thenReturn(list);

        assertSame(list, service.selectAllValidCategories());
    }

    @Test
    void buildCategoryTree_buildsNestedTree()
    {
        AiConsultationCategory root = category(1L, "法律", 0L);
        AiConsultationCategory child = category(2L, "婚姻", 1L);
        AiConsultationCategory grandChild = category(3L, "离婚", 2L);

        List<AiConsultationCategory> result =
            service.buildCategoryTree(Arrays.asList(root, child, grandChild));

        assertEquals(1, result.size());
        assertSame(root, result.get(0));
        assertEquals(1, root.getChildren().size());
        assertEquals(1, child.getChildren().size());
        assertSame(grandChild, child.getChildren().get(0));
    }

    @Test
    void buildCategoryTree_noRoots_returnsOriginalList()
    {
        List<AiConsultationCategory> circular = Arrays.asList(category(1L, "A", 2L), category(2L, "B", 1L));

        List<AiConsultationCategory> result = service.buildCategoryTree(circular);

        assertEquals(2, result.size());
    }

    @Test
    void checkCategoryNameUnique_sameIdOrAbsent_returnsTrue()
    {
        AiConsultationCategory category = category(1L, "婚姻", 0L);
        when(categoryMapper.selectCategoryByName("婚姻")).thenReturn(category);

        assertTrue(service.checkCategoryNameUnique(category));

        when(categoryMapper.selectCategoryByName("新分类")).thenReturn(null);
        assertTrue(service.checkCategoryNameUnique(category(2L, "新分类", 0L)));
    }

    @Test
    void checkCategoryNameUnique_duplicated_returnsFalse()
    {
        when(categoryMapper.selectCategoryByName("婚姻")).thenReturn(category(2L, "婚姻", 0L));

        assertFalse(service.checkCategoryNameUnique(category(1L, "婚姻", 0L)));
    }

    @Test
    void importCategory_empty_throws()
    {
        assertEquals("导入咨询分类数据不能为空！",
            assertThrows(ServiceException.class,
                () -> service.importCategory(Collections.emptyList(), false, "admin")).getMessage());
    }

    @Test
    void importCategory_newCategories_inserts()
    {
        AiConsultationCategory first = category(null, "婚姻", 0L);
        AiConsultationCategory second = category(null, "劳动", 0L);
        when(categoryMapper.selectCategoryByName("婚姻")).thenReturn(null);
        when(categoryMapper.selectCategoryByName("劳动")).thenReturn(null);
        when(categoryMapper.insertCategory(any(AiConsultationCategory.class))).thenReturn(1);

        String msg = service.importCategory(Arrays.asList(first, second), false, "admin");

        assertTrue(msg.contains("共 2 条"));
        assertEquals("admin", first.getCreateBy());
        verify(categoryMapper, never()).updateCategory(any());
    }

    @Test
    void importCategory_existingWithUpdateSupport_updates()
    {
        AiConsultationCategory category = category(null, "婚姻", 0L);
        when(categoryMapper.selectCategoryByName("婚姻")).thenReturn(category(9L, "婚姻", 0L));
        when(categoryMapper.updateCategory(category)).thenReturn(1);

        String msg = service.importCategory(Collections.singletonList(category), true, "admin");

        assertTrue(msg.contains("更新成功"));
        assertEquals(9L, category.getCategoryId());
        assertEquals("admin", category.getUpdateBy());
        verify(categoryMapper, never()).insertCategory(any());
    }

    @Test
    void importCategory_existingWithoutUpdate_throwsWithFailure()
    {
        AiConsultationCategory category = category(null, "婚姻", 0L);
        when(categoryMapper.selectCategoryByName("婚姻")).thenReturn(category(9L, "婚姻", 0L));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.importCategory(Collections.singletonList(category), false, "admin"));
        assertTrue(ex.getMessage().contains("导入失败"));
        verify(categoryMapper, never()).insertCategory(any());
    }

    @Test
    void importCategory_insertThrows_collectsFailure()
    {
        AiConsultationCategory category = category(null, "婚姻", 0L);
        when(categoryMapper.selectCategoryByName("婚姻")).thenReturn(null);
        when(categoryMapper.insertCategory(category)).thenThrow(new IllegalStateException("db down"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.importCategory(Collections.singletonList(category), false, "admin"));
        assertTrue(ex.getMessage().contains("db down"));
    }
}
