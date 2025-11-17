package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiConsultationCategory;

/**
 * 咨询分类Service接口
 * 
 * @author AI Lawyers
 * @date 2025-06-23
 */
public interface IAiConsultationCategoryService 
{
    /**
     * 查询咨询分类
     * 
     * @param categoryId 咨询分类主键
     * @return 咨询分类
     */
    public AiConsultationCategory selectAiConsultationCategoryByCategoryId(Long categoryId);

    /**
     * 查询咨询分类列表
     * 
     * @param aiConsultationCategory 咨询分类
     * @return 咨询分类集合
     */
    public List<AiConsultationCategory> selectAiConsultationCategoryList(AiConsultationCategory aiConsultationCategory);

    /**
     * 新增咨询分类
     * 
     * @param aiConsultationCategory 咨询分类
     * @return 结果
     */
    public int insertAiConsultationCategory(AiConsultationCategory aiConsultationCategory);

    /**
     * 修改咨询分类
     * 
     * @param aiConsultationCategory 咨询分类
     * @return 结果
     */
    public int updateAiConsultationCategory(AiConsultationCategory aiConsultationCategory);

    /**
     * 批量删除咨询分类
     * 
     * @param categoryIds 需要删除的咨询分类主键集合
     * @return 结果
     */
    public int deleteAiConsultationCategoryByCategoryIds(Long[] categoryIds);

    /**
     * 删除咨询分类信息
     * 
     * @param categoryId 咨询分类主键
     * @return 结果
     */
    public int deleteAiConsultationCategoryByCategoryId(Long categoryId);
    
    /**
     * 构建前端所需要树结构
     * 
     * @param categories 分类列表
     * @return 树结构列表
     */
    public List<AiConsultationCategory> buildCategoryTree(List<AiConsultationCategory> categories);
    
    /**
     * 查询所有有效的咨询分类
     * 
     * @return 咨询分类集合
     */
    public List<AiConsultationCategory> selectAllValidCategories();
    
    /**
     * 校验分类名称是否唯一
     * 
     * @param category 分类信息
     * @return 结果
     */
    public boolean checkCategoryNameUnique(AiConsultationCategory category);
    
    /**
     * 导入咨询分类数据
     *
     * @param categoryList 咨询分类数据列表
     * @param isUpdateSupport 是否更新支持，如果已存在，则进行更新数据
     * @param operName 操作用户
     * @return 结果
     */
    public String importCategory(List<AiConsultationCategory> categoryList, Boolean isUpdateSupport, String operName);
}