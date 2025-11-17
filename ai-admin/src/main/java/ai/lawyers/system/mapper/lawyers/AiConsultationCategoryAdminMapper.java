package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiConsultationCategory;

/**
 * 咨询分类Mapper接口
 * 
 * @author AI Lawyers
 * @date 2025-06-23
 */
public interface AiConsultationCategoryAdminMapper 
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
    public List<AiConsultationCategory> selectAiConsultationCategoryAdminList(AiConsultationCategory aiConsultationCategory);

    /**
     * 新增咨询分类
     * 
     * @param aiConsultationCategory 咨询分类
     * @return 结果
     */
    public int insertAiConsultationCategoryAdmin(AiConsultationCategory aiConsultationCategory);

    /**
     * 修改咨询分类
     * 
     * @param aiConsultationCategory 咨询分类
     * @return 结果
     */
    public int updateAiConsultationCategoryAdmin(AiConsultationCategory aiConsultationCategory);

    /**
     * 删除咨询分类
     * 
     * @param categoryId 咨询分类主键
     * @return 结果
     */
    public int deleteAiConsultationCategoryByCategoryId(Long categoryId);

    /**
     * 批量删除咨询分类
     * 
     * @param categoryIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteAiConsultationCategoryByCategoryIds(Long[] categoryIds);
    
    /**
     * 根据父ID查询子分类数量
     * 
     * @param parentId 父分类ID
     * @return 结果
     */
    public int selectChildrenCountByParentId(Long parentId);
    
    /**
     * 查询所有有效的咨询分类
     * 
     * @return 咨询分类集合
     */
    public List<AiConsultationCategory> selectAllValidCategories();
    
    /**
     * 根据分类名称查询咨询分类
     * 
     * @param categoryName 分类名称
     * @return 咨询分类
     */
    public AiConsultationCategory selectAiConsultationCategoryByCategoryName(String categoryName);
}