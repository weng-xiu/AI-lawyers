package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiConsultationCategory;

/**
 * 咨询分类 服务接口。
 *
 * <p>T1-8（L5）收口：原 ai-admin / ai-system 同包同名接口合并为本接口，
 * 统一由 ai-system 侧 {@code AiConsultationCategoryServiceImpl} 实现（逻辑删除/树形/唯一校验）。</p>
 *
 * @author ai-lawyers
 */
public interface IAiConsultationCategoryService
{
    /**
     * 查询咨询分类
     *
     * @param categoryId 咨询分类主键
     * @return 咨询分类
     */
    AiConsultationCategory selectAiConsultationCategoryByCategoryId(Long categoryId);

    /**
     * 查询咨询分类列表
     *
     * @param aiConsultationCategory 咨询分类查询条件
     * @return 咨询分类集合
     */
    List<AiConsultationCategory> selectAiConsultationCategoryList(AiConsultationCategory aiConsultationCategory);

    /**
     * 新增咨询分类
     *
     * @param aiConsultationCategory 咨询分类
     * @return 结果
     */
    int insertAiConsultationCategory(AiConsultationCategory aiConsultationCategory);

    /**
     * 修改咨询分类
     *
     * @param aiConsultationCategory 咨询分类
     * @return 结果
     */
    int updateAiConsultationCategory(AiConsultationCategory aiConsultationCategory);

    /**
     * 逻辑删除批量咨询分类
     *
     * @param categoryIds 需要删除的咨询分类主键集合
     * @return 结果
     */
    int deleteAiConsultationCategoryByCategoryIds(Long[] categoryIds);

    /**
     * 逻辑删除咨询分类信息
     *
     * @param categoryId 咨询分类主键
     * @return 结果
     */
    int deleteAiConsultationCategoryByCategoryId(Long categoryId);

    /**
     * 构建前端所需要树结构
     *
     * @param categories 分类列表
     * @return 树结构列表
     */
    List<AiConsultationCategory> buildCategoryTree(List<AiConsultationCategory> categories);

    /**
     * 查询所有有效的咨询分类（启用且未删除）
     *
     * @return 咨询分类集合
     */
    List<AiConsultationCategory> selectAllValidCategories();

    /**
     * 校验分类名称是否唯一
     *
     * @param category 分类信息
     * @return true 唯一；false 名称已存在
     */
    boolean checkCategoryNameUnique(AiConsultationCategory category);

    /**
     * 导入咨询分类数据
     *
     * @param categoryList    咨询分类数据列表
     * @param isUpdateSupport 是否更新支持，如果已存在，则进行更新数据
     * @param operName        操作用户
     * @return 结果提示
     */
    String importCategory(List<AiConsultationCategory> categoryList, Boolean isUpdateSupport, String operName);
}
