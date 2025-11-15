package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.core.text.Convert;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.system.domain.lawyers.AiConsultationCategory;
import ai.lawyers.system.mapper.lawyers.AiConsultationCategoryMapper;
import ai.lawyers.system.service.lawyers.IAiConsultationCategoryService;

/**
 * 咨询分类 服务层实现
 * 
 * @author AI Lawyers
 */
@Service
public class AiConsultationCategoryServiceImpl implements IAiConsultationCategoryService 
{
    @Autowired
    private AiConsultationCategoryMapper aiConsultationCategoryMapper;

    /**
     * 查询咨询分类
     * 
     * @param categoryId 咨询分类主键
     * @return 咨询分类
     */
    @Override
    public AiConsultationCategory selectAiConsultationCategoryByCategoryId(Long categoryId)
    {
        return aiConsultationCategoryMapper.selectAiConsultationCategoryById(categoryId);
    }

    /**
     * 查询咨询分类列表
     * 
     * @param aiConsultationCategory 咨询分类
     * @return 咨询分类
     */
    @Override
    public List<AiConsultationCategory> selectAiConsultationCategoryList(AiConsultationCategory aiConsultationCategory)
    {
        return aiConsultationCategoryMapper.selectAiConsultationCategoryList(aiConsultationCategory);
    }

    /**
     * 新增咨询分类
     * 
     * @param aiConsultationCategory 咨询分类
     * @return 结果
     */
    @Override
    public int insertAiConsultationCategory(AiConsultationCategory aiConsultationCategory)
    {
        aiConsultationCategory.setCreateTime(DateUtils.getNowDate());
        return aiConsultationCategoryMapper.insertAiConsultationCategory(aiConsultationCategory);
    }

    /**
     * 修改咨询分类
     * 
     * @param aiConsultationCategory 咨询分类
     * @return 结果
     */
    @Override
    public int updateAiConsultationCategory(AiConsultationCategory aiConsultationCategory)
    {
        aiConsultationCategory.setUpdateTime(DateUtils.getNowDate());
        return aiConsultationCategoryMapper.updateAiConsultationCategory(aiConsultationCategory);
    }

    /**
     * 批量删除咨询分类
     * 
     * @param categoryIds 需要删除的咨询分类主键
     * @return 结果
     */
    @Override
    public int deleteAiConsultationCategoryByCategoryIds(Long[] categoryIds)
    {
        return aiConsultationCategoryMapper.deleteAiConsultationCategoryByIds(categoryIds);
    }

    /**
     * 删除咨询分类信息
     * 
     * @param categoryId 咨询分类主键
     * @return 结果
     */
    @Override
    public int deleteAiConsultationCategoryByCategoryId(Long categoryId)
    {
        return aiConsultationCategoryMapper.deleteAiConsultationCategoryById(categoryId);
    }

    /**
     * 查询所有有效的咨询分类
     * 
     * @return 咨询分类集合
     */
    @Override
    public List<AiConsultationCategory> selectAllValidCategories()
    {
        AiConsultationCategory category = new AiConsultationCategory();
        category.setStatus("0"); // 状态为0表示正常
        category.setDelFlag("0"); // 删除标志为0表示存在
        return selectAiConsultationCategoryList(category);
    }
}