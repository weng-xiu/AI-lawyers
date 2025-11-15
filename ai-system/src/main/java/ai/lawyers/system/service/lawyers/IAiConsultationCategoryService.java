package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiConsultationCategory;

/**
 * 咨询分类 服务接口
 * 
 * @author AI Lawyers
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
     * 查询所有有效的咨询分类
     * 
     * @return 咨询分类集合
     */
    public List<AiConsultationCategory> selectAllValidCategories();
}