package ai.lawyers.system.service.lawyers.impl;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.mapper.lawyers.AiConsultationCategoryAdminMapper;
import ai.lawyers.system.domain.lawyers.AiConsultationCategory;
import ai.lawyers.system.service.lawyers.IAiConsultationCategoryService;

/**
 * 咨询分类Service业务层处理
 * 
 * @author AI Lawyers
 * @date 2025-06-23
 */
@Service("aiConsultationCategoryAdminServiceImpl")
@Primary
public class AiConsultationCategoryServiceImpl implements IAiConsultationCategoryService 
{
    @Autowired
    private AiConsultationCategoryAdminMapper aiConsultationCategoryMapper;

    /**
     * 查询咨询分类
     * 
     * @param categoryId 咨询分类主键
     * @return 咨询分类
     */
    @Override
    public AiConsultationCategory selectAiConsultationCategoryByCategoryId(Long categoryId)
    {
        return aiConsultationCategoryMapper.selectAiConsultationCategoryByCategoryId(categoryId);
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
        return aiConsultationCategoryMapper.selectAiConsultationCategoryAdminList(aiConsultationCategory);
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
        return aiConsultationCategoryMapper.insertAiConsultationCategoryAdmin(aiConsultationCategory);
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
        return aiConsultationCategoryMapper.updateAiConsultationCategoryAdmin(aiConsultationCategory);
    }

    /**
     * 批量删除咨询分类
     * 
     * @param categoryIds 需要删除的咨询分类主键
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteAiConsultationCategoryByCategoryIds(Long[] categoryIds)
    {
        return aiConsultationCategoryMapper.deleteAiConsultationCategoryByCategoryIds(categoryIds);
    }
    
    /**
     * 删除咨询分类信息
     * 
     * @param categoryId 咨询分类主键
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteAiConsultationCategoryByCategoryId(Long categoryId)
    {
        return aiConsultationCategoryMapper.deleteAiConsultationCategoryByCategoryId(categoryId);
    }
    
    /**
     * 构建前端所需要树结构
     * 
     * @param categories 分类列表
     * @return 树结构列表
     */
    @Override
    public List<AiConsultationCategory> buildCategoryTree(List<AiConsultationCategory> categories)
    {
        List<AiConsultationCategory> returnList = new ArrayList<AiConsultationCategory>();
        List<Long> tempList = new ArrayList<Long>();
        for (AiConsultationCategory category : categories)
        {
            tempList.add(category.getCategoryId());
        }
        for (Iterator<AiConsultationCategory> iterator = categories.iterator(); iterator.hasNext();)
        {
            AiConsultationCategory category = (AiConsultationCategory) iterator.next();
            // 如果是顶级节点, 遍历该父节点的所有子节点
            if (!tempList.contains(category.getParentId()))
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
    
    /**
     * 递归列表
     */
    private void recursionFn(List<AiConsultationCategory> list, AiConsultationCategory t)
    {
        // 得到子节点列表
        List<AiConsultationCategory> childList = getChildList(list, t);
        t.setChildren(childList);
        for (AiConsultationCategory tChild : childList)
        {
            if (hasChild(list, tChild))
            {
                recursionFn(list, tChild);
            }
        }
    }
    
    /**
     * 得到子节点列表
     */
    private List<AiConsultationCategory> getChildList(List<AiConsultationCategory> list, AiConsultationCategory t)
    {
        List<AiConsultationCategory> tlist = new ArrayList<AiConsultationCategory>();
        Iterator<AiConsultationCategory> it = list.iterator();
        while (it.hasNext())
        {
            AiConsultationCategory n = (AiConsultationCategory) it.next();
            if (n.getParentId() != null && n.getParentId().longValue() == t.getCategoryId().longValue())
            {
                tlist.add(n);
            }
        }
        return tlist;
    }
    
    /**
     * 判断是否有子节点
     */
    private boolean hasChild(List<AiConsultationCategory> list, AiConsultationCategory t)
    {
        return getChildList(list, t).size() > 0 ? true : false;
    }
    
    /**
     * 查询所有有效的咨询分类
     * 
     * @return 咨询分类集合
     */
    @Override
    public List<AiConsultationCategory> selectAllValidCategories()
    {
        return aiConsultationCategoryMapper.selectAllValidCategories();
    }
    
    /**
     * 导入咨询分类数据
     *
     * @param categoryList 咨询分类数据列表
     * @param isUpdateSupport 是否更新支持，如果已存在，则进行更新数据
     * @param operName 操作用户
     * @return 结果
     */
    @Override
    @Transactional
    public String importCategory(List<AiConsultationCategory> categoryList, Boolean isUpdateSupport, String operName)
    {
        if (StringUtils.isNull(categoryList) || categoryList.size() == 0)
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
                // 判断这个分类名称是否存在
                AiConsultationCategory c = aiConsultationCategoryMapper.selectAiConsultationCategoryByCategoryName(category.getCategoryName());
                if (StringUtils.isNull(c))
                {
                    category.setCreateBy(operName);
                    this.insertAiConsultationCategory(category);
                    successNum++;
                    successMsg.append("<br/>" + successNum + "、分类 " + category.getCategoryName() + " 导入成功");
                }
                else if (isUpdateSupport)
                {
                    category.setUpdateBy(operName);
                    this.updateAiConsultationCategory(category);
                    successNum++;
                    successMsg.append("<br/>" + successNum + "、分类 " + category.getCategoryName() + " 更新成功");
                }
                else
                {
                    failureNum++;
                    failureMsg.append("<br/>" + failureNum + "、分类 " + category.getCategoryName() + " 已存在");
                }
            }
            catch (Exception e)
            {
                failureNum++;
                String msg = "<br/>" + failureNum + "、分类 " + category.getCategoryName() + " 导入失败：";
                failureMsg.append(msg + e.getMessage());
            }
        }
        if (failureNum > 0)
        {
            failureMsg.insert(0, "很抱歉，导入失败！共 " + failureNum + " 条数据格式不正确，错误如下：");
            throw new ServiceException(failureMsg.toString());
        }
        else
        {
            successMsg.insert(0, "恭喜您，数据已全部导入成功！共 " + successNum + " 条，数据如下：");
        }
        return successMsg.toString();
    }
    
    /**
     * 校验分类名称是否唯一
     * 
     * @param category 分类信息
     * @return 结果
     */
    @Override
    public boolean checkCategoryNameUnique(AiConsultationCategory category)
    {
        Long categoryId = StringUtils.isNull(category.getCategoryId()) ? -1L : category.getCategoryId();
        AiConsultationCategory info = aiConsultationCategoryMapper.selectAiConsultationCategoryByCategoryName(category.getCategoryName());
        if (StringUtils.isNotNull(info) && !info.getCategoryId().equals(categoryId))
        {
            return false;
        }
        return true;
    }
}