package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.AiConsultationCategory;

/**
 * 咨询分类数据层（T1-8 收口）。
 *
 * <p>合并原 ai-admin 下 AiConsultationCategoryAdminMapper 的逻辑删除（del_flag）/树形/唯一校验能力，
 * 统一在 ai-system 提供，消除 ai-admin 与 ai-system 同包同名类（L5）。</p>
 *
 * @author ai-lawyers
 */
public interface CategoryMapper
{
    /** 主键查询分类 */
    AiConsultationCategory selectCategoryById(@Param("categoryId") Long categoryId);

    /** 条件查询分类列表（仅未删除） */
    List<AiConsultationCategory> selectCategoryList(AiConsultationCategory category);

    /** 查询全部有效（启用且未删除）分类 */
    List<AiConsultationCategory> selectAllValidCategories();

    /** 按名称精确查询（启用且未删除），用于唯一性校验/导入判重 */
    AiConsultationCategory selectCategoryByName(@Param("categoryName") String categoryName);

    /** 统计某父分类下未删除子分类数量（删除前校验） */
    int countChildrenByParentId(@Param("parentId") Long parentId);

    int insertCategory(AiConsultationCategory category);

    int updateCategory(AiConsultationCategory category);

    /** 逻辑删除单个分类（del_flag 置 2） */
    int deleteCategoryById(@Param("categoryId") Long categoryId);

    /** 逻辑删除批量分类（del_flag 置 2） */
    int deleteCategoryByIds(@Param("categoryIds") Long[] categoryIds);
}
