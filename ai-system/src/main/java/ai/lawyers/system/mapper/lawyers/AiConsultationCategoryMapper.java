package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiConsultationCategory;

/**
 * 咨询分类 数据层
 * 
 * @author AI Lawyers
 */
public interface AiConsultationCategoryMapper
{
    /**
     * 查询咨询分类
     * 
     * @param categoryId 咨询分类ID
     * @return 咨询分类
     */
    public AiConsultationCategory selectAiConsultationCategoryById(Long categoryId);

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
     * 删除咨询分类
     * 
     * @param categoryId 咨询分类ID
     * @return 结果
     */
    public int deleteAiConsultationCategoryById(Long categoryId);

    /**
     * 批量删除咨询分类
     * 
     * @param categoryIds 需要删除的数据ID
     * @return 结果
     */
    public int deleteAiConsultationCategoryByIds(Long[] categoryIds);
}