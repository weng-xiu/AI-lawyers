package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiConsultationCategory;
import ai.lawyers.system.service.lawyers.IAiConsultationCategoryService;

/**
 * 咨询分类接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiConsultationCategoryControllerTest
{
    @Mock
    private IAiConsultationCategoryService aiConsultationCategoryService;

    @InjectMocks
    private AiConsultationCategoryController controller;

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

    private AiConsultationCategory category(Long categoryId, Long parentId, String name)
    {
        AiConsultationCategory category = new AiConsultationCategory();
        category.setCategoryId(categoryId);
        category.setParentId(parentId);
        category.setCategoryName(name);
        return category;
    }

    @Test
    void 分类列表分页返回()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiConsultationCategory query = new AiConsultationCategory();
        List<AiConsultationCategory> list = new ArrayList<AiConsultationCategory>();
        list.add(category(1L, 0L, "合同纠纷"));
        when(aiConsultationCategoryService.selectAiConsultationCategoryList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertSame(list, table.getRows());
    }

    @Test
    void 排除节点列表过滤当前节点()
    {
        List<AiConsultationCategory> list = new ArrayList<AiConsultationCategory>();
        list.add(category(1L, 0L, "合同纠纷"));
        list.add(category(2L, 0L, "劳动纠纷"));
        when(aiConsultationCategoryService.selectAiConsultationCategoryList(any(AiConsultationCategory.class)))
                .thenReturn(list);

        @SuppressWarnings("unchecked")
        List<AiConsultationCategory> result = (List<AiConsultationCategory>) controller.listExcludeChild(1L)
                .get(AjaxResult.DATA_TAG);

        assertTrue(result.isEmpty());
    }

    @Test
    void 分类树与详情及有效列表()
    {
        List<AiConsultationCategory> categories = new ArrayList<AiConsultationCategory>();
        AiConsultationCategory detail = category(1L, 0L, "合同纠纷");
        List<AiConsultationCategory> validList = List.of(category(2L, 0L, "劳动纠纷"));
        when(aiConsultationCategoryService.selectAiConsultationCategoryList(any(AiConsultationCategory.class)))
                .thenReturn(categories);
        when(aiConsultationCategoryService.buildCategoryTree(categories)).thenReturn(categories);
        when(aiConsultationCategoryService.selectAiConsultationCategoryByCategoryId(1L)).thenReturn(detail);
        when(aiConsultationCategoryService.selectAllValidCategories()).thenReturn(validList);

        assertSame(categories, controller.tree().get(AjaxResult.DATA_TAG));
        assertSame(detail, controller.getInfo(1L).get(AjaxResult.DATA_TAG));
        assertSame(validList, controller.valid().get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增分类校验名称唯一并写操作人()
    {
        AiConsultationCategory duplicate = category(null, 0L, "合同纠纷");
        when(aiConsultationCategoryService.checkCategoryNameUnique(duplicate)).thenReturn(false);
        assertEquals("新增分类'合同纠纷'失败，分类名称已存在",
                controller.add(duplicate).get(AjaxResult.MSG_TAG));

        AiConsultationCategory category = category(null, 0L, "劳动纠纷");
        when(aiConsultationCategoryService.checkCategoryNameUnique(category)).thenReturn(true);
        when(aiConsultationCategoryService.insertAiConsultationCategory(category)).thenReturn(1);
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            assertEquals(200, controller.add(category).get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", category.getCreateBy());
    }

    @Test
    void 修改分类校验名称唯一并写更新人()
    {
        AiConsultationCategory duplicate = category(1L, 0L, "合同纠纷");
        when(aiConsultationCategoryService.checkCategoryNameUnique(duplicate)).thenReturn(false);
        assertEquals("修改分类'合同纠纷'失败，分类名称已存在",
                controller.edit(duplicate).get(AjaxResult.MSG_TAG));

        AiConsultationCategory category = category(1L, 0L, "劳动纠纷");
        when(aiConsultationCategoryService.checkCategoryNameUnique(category)).thenReturn(true);
        when(aiConsultationCategoryService.updateAiConsultationCategory(category)).thenReturn(1);
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            assertEquals(200, controller.edit(category).get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", category.getUpdateBy());
    }

    @Test
    void 删除分类与导出Excel()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiConsultationCategoryService.deleteAiConsultationCategoryByCategoryIds(ids)).thenReturn(2);
        when(aiConsultationCategoryService.selectAiConsultationCategoryList(any(AiConsultationCategory.class)))
                .thenReturn(List.of(category(1L, 0L, "合同纠纷")));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertEquals(200, controller.remove(ids).get(AjaxResult.CODE_TAG));
        controller.export(response, new AiConsultationCategory());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }
}
