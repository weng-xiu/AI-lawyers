package ai.lawyers.system.service.impl.lawyers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiConsultationCategory;
import ai.lawyers.system.mapper.lawyers.CategoryMapper;
import ai.lawyers.system.service.lawyers.IAiConsultationCategoryService;

/**
 * 咨询分类服务实现（T1-8 L5 收口）。
 *
 * <p>原 ai-admin 下存在同包同名的 service/impl 且标注 {@code @Primary}，ai-system 侧只有接口无实现，
 * 运行期依赖类加载顺序、devtools RestartClassLoader 还会引发 Bean 类型不一致。现将完整实现（逻辑删除、
 * 树形构建、名称唯一校验、导入）统一放在 ai-system，删除 ai-admin 重复类。</p>
 *
 * @author ai-lawyers
 */
@Service
public class AiConsultationCategoryServiceImpl implements IAiConsultationCategoryService
{
    @Autowired
    private CategoryMapper categoryMapper;

    @Override
    public AiConsultationCategory selectAiConsultationCategoryByCategoryId(Long categoryId)
    {
        return categoryMapper.selectCategoryById(categoryId);
    }

    @Override
    public List<AiConsultationCategory> selectAiConsultationCategoryList(AiConsultationCategory aiConsultationCategory)
    {
        return categoryMapper.selectCategoryList(aiConsultationCategory);
    }

    @Override
    public int insertAiConsultationCategory(AiConsultationCategory aiConsultationCategory)
    {
        return categoryMapper.insertCategory(aiConsultationCategory);
    }

    @Override
    public int updateAiConsultationCategory(AiConsultationCategory aiConsultationCategory)
    {
        return categoryMapper.updateCategory(aiConsultationCategory);
    }

    @Override
    @Transactional
    public int deleteAiConsultationCategoryByCategoryIds(Long[] categoryIds)
    {
        return categoryMapper.deleteCategoryByIds(categoryIds);
    }

    @Override
    @Transactional
    public int deleteAiConsultationCategoryByCategoryId(Long categoryId)
    {
        if (categoryMapper.countChildrenByParentId(categoryId) > 0)
        {
            throw new ServiceException("该分类下存在子分类，不允许删除");
        }
        return categoryMapper.deleteCategoryById(categoryId);
    }

    @Override
    public List<AiConsultationCategory> selectAllValidCategories()
    {
        return categoryMapper.selectAllValidCategories();
    }

    /**
     * 构建前端树结构：顶级节点（父节点不在当前列表中）作为根，递归挂载子节点。
     */
    @Override
    public List<AiConsultationCategory> buildCategoryTree(List<AiConsultationCategory> categories)
    {
        List<AiConsultationCategory> returnList = new ArrayList<>();
        List<Long> idList = new ArrayList<>();
        for (AiConsultationCategory category : categories)
        {
            idList.add(category.getCategoryId());
        }
        for (Iterator<AiConsultationCategory> iterator = categories.iterator(); iterator.hasNext();)
        {
            AiConsultationCategory category = iterator.next();
            // 顶级节点：父节点不在本次列表中
            if (!idList.contains(category.getParentId()))
            {
                recursionFn(categories, category);
                returnList.add(category);
            }
        }
        if (returnList.isEmpty())
        {
            returnList = categories;
        }
        return returnList;
    }

    /** 递归挂载子节点 */
    private void recursionFn(List<AiConsultationCategory> list, AiConsultationCategory t)
    {
        List<AiConsultationCategory> childList = getChildList(list, t);
        t.setChildren(childList);
        for (AiConsultationCategory child : childList)
        {
            if (hasChild(list, child))
            {
                recursionFn(list, child);
            }
        }
    }

    private List<AiConsultationCategory> getChildList(List<AiConsultationCategory> list, AiConsultationCategory t)
    {
        List<AiConsultationCategory> tlist = new ArrayList<>();
        for (AiConsultationCategory n : list)
        {
            if (n.getParentId() != null && t.getCategoryId() != null
                    && n.getParentId().longValue() == t.getCategoryId().longValue())
            {
                tlist.add(n);
            }
        }
        return tlist;
    }

    private boolean hasChild(List<AiConsultationCategory> list, AiConsultationCategory t)
    {
        return getChildList(list, t).size() > 0;
    }

    @Override
    public boolean checkCategoryNameUnique(AiConsultationCategory category)
    {
        Long categoryId = StringUtils.isNull(category.getCategoryId()) ? -1L : category.getCategoryId();
        AiConsultationCategory info = categoryMapper.selectCategoryByName(category.getCategoryName());
        if (StringUtils.isNotNull(info) && !info.getCategoryId().equals(categoryId))
        {
            return false;
        }
        return true;
    }

    @Override
    @Transactional
    public String importCategory(List<AiConsultationCategory> categoryList, Boolean isUpdateSupport, String operName)
    {
        if (StringUtils.isNull(categoryList) || categoryList.isEmpty())
        {
            throw new ServiceException("导入咨询分类数据不能为空！");
        }
        int successNum = 0;
        int failureNum = 0;
        StringBuilder successMsg = new StringBuilder();
        StringBuilder failureMsg = new StringBuilder();
        for (AiConsultationCategory category : categoryList)
        {
            try
            {
                AiConsultationCategory exist = categoryMapper.selectCategoryByName(category.getCategoryName());
                if (StringUtils.isNull(exist))
                {
                    category.setCreateBy(operName);
                    this.insertAiConsultationCategory(category);
                    successNum++;
                    successMsg.append("<br/>").append(successNum).append("、分类 ")
                            .append(category.getCategoryName()).append(" 导入成功");
                }
                else if (Boolean.TRUE.equals(isUpdateSupport))
                {
                    category.setCategoryId(exist.getCategoryId());
                    category.setUpdateBy(operName);
                    this.updateAiConsultationCategory(category);
                    successNum++;
                    successMsg.append("<br/>").append(successNum).append("、分类 ")
                            .append(category.getCategoryName()).append(" 更新成功");
                }
                else
                {
                    failureNum++;
                    failureMsg.append("<br/>").append(failureNum).append("、分类 ")
                            .append(category.getCategoryName()).append(" 已存在");
                }
            }
            catch (Exception e)
            {
                failureNum++;
                failureMsg.append("<br/>").append(failureNum).append("、分类 ")
                        .append(category.getCategoryName()).append(" 导入失败：").append(e.getMessage());
            }
        }
        if (failureNum > 0)
        {
            failureMsg.insert(0, "很抱歉，导入失败！共 " + failureNum + " 条数据格式不正确，错误如下：");
            throw new ServiceException(failureMsg.toString());
        }
        successMsg.insert(0, "恭喜您，数据已全部导入成功！共 " + successNum + " 条，数据如下：");
        return successMsg.toString();
    }
}
