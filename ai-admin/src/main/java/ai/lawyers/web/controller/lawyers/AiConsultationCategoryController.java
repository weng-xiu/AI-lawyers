package ai.lawyers.web.controller.lawyers;

import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.system.domain.lawyers.AiConsultationCategory;
import ai.lawyers.system.service.lawyers.IAiConsultationCategoryService;
import ai.lawyers.common.utils.poi.ExcelUtil;
import ai.lawyers.common.core.page.TableDataInfo;

/**
 * 咨询分类Controller
 * 
 * @author AI Lawyers
 */
@RestController
@RequestMapping("/lawyers/category")
public class AiConsultationCategoryController extends BaseController
{
    @Autowired
    private IAiConsultationCategoryService aiConsultationCategoryService;

    /**
     * 查询咨询分类列表
     */
    @PreAuthorize("@ss.hasPermi('lawyers:category:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiConsultationCategory aiConsultationCategory)
    {
        startPage();
        List<AiConsultationCategory> list = aiConsultationCategoryService.selectAiConsultationCategoryList(aiConsultationCategory);
        return getDataTable(list);
    }
    
    /**
     * 查询咨询分类列表（排除节点）
     */
    @PreAuthorize("@ss.hasPermi('lawyers:category:list')")
    @GetMapping("/list/exclude/{categoryId}")
    public AjaxResult listExcludeChild(@PathVariable(value = "categoryId", required = false) Long categoryId)
    {
        List<AiConsultationCategory> list = aiConsultationCategoryService.selectAiConsultationCategoryList(new AiConsultationCategory());
        list.removeIf(d -> d.getCategoryId().equals(categoryId) 
            || isChild(d, categoryId, list));
        return success(list);
    }
    
    /**
     * 判断是否存在子节点
     */
    private boolean isChild(AiConsultationCategory category, Long categoryId, List<AiConsultationCategory> list)
    {
        if (category.getParentId().equals(categoryId))
        {
            return true;
        }
        for (AiConsultationCategory item : list)
        {
            if (item.getCategoryId().equals(categoryId))
            {
                return isChild(category, item.getParentId(), list);
            }
        }
        return false;
    }

    /**
     * 导出咨询分类列表
     */
    @PreAuthorize("@ss.hasPermi('lawyers:category:export')")
    @Log(title = "咨询分类", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiConsultationCategory aiConsultationCategory)
    {
        List<AiConsultationCategory> list = aiConsultationCategoryService.selectAiConsultationCategoryList(aiConsultationCategory);
        ExcelUtil<AiConsultationCategory> util = new ExcelUtil<AiConsultationCategory>(AiConsultationCategory.class);
        util.exportExcel(response, list, "咨询分类数据");
    }

    /**
     * 获取咨询分类树形结构
     */
    @PreAuthorize("@ss.hasPermi('lawyers:category:list')")
    @GetMapping("/tree")
    public AjaxResult tree()
    {
        List<AiConsultationCategory> categories = aiConsultationCategoryService.selectAiConsultationCategoryList(new AiConsultationCategory());
        return success(aiConsultationCategoryService.buildCategoryTree(categories));
    }
    
    /**
     * 获取咨询分类详细信息
     */
    @PreAuthorize("@ss.hasPermi('lawyers:category:query')")
    @GetMapping(value = "/{categoryId}")
    public AjaxResult getInfo(@PathVariable("categoryId") Long categoryId)
    {
        return success(aiConsultationCategoryService.selectAiConsultationCategoryByCategoryId(categoryId));
    }

    /**
     * 新增咨询分类
     */
    @PreAuthorize("@ss.hasPermi('lawyers:category:add')")
    @Log(title = "咨询分类", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiConsultationCategory aiConsultationCategory)
    {
        if (!aiConsultationCategoryService.checkCategoryNameUnique(aiConsultationCategory))
        {
            return error("新增分类'" + aiConsultationCategory.getCategoryName() + "'失败，分类名称已存在");
        }
        aiConsultationCategory.setCreateBy(getUsername());
        return toAjax(aiConsultationCategoryService.insertAiConsultationCategory(aiConsultationCategory));
    }

    /**
     * 修改咨询分类
     */
    @PreAuthorize("@ss.hasPermi('lawyers:category:edit')")
    @Log(title = "咨询分类", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiConsultationCategory aiConsultationCategory)
    {
        if (!aiConsultationCategoryService.checkCategoryNameUnique(aiConsultationCategory))
        {
            return error("修改分类'" + aiConsultationCategory.getCategoryName() + "'失败，分类名称已存在");
        }
        aiConsultationCategory.setUpdateBy(getUsername());
        return toAjax(aiConsultationCategoryService.updateAiConsultationCategory(aiConsultationCategory));
    }

    /**
     * 删除咨询分类
     */
    @PreAuthorize("@ss.hasPermi('lawyers:category:remove')")
    @Log(title = "咨询分类", businessType = BusinessType.DELETE)
    @DeleteMapping("/{categoryIds}")
    public AjaxResult remove(@PathVariable Long[] categoryIds)
    {
        return toAjax(aiConsultationCategoryService.deleteAiConsultationCategoryByCategoryIds(categoryIds));
    }
    
    /**
     * 获取所有有效的咨询分类
     */
    @PreAuthorize("@ss.hasPermi('lawyers:category:list')")
    @GetMapping("/valid")
    public AjaxResult valid()
    {
        return success(aiConsultationCategoryService.selectAllValidCategories());
    }
}