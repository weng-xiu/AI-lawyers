package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.core.text.Convert;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeMapper;
import ai.lawyers.system.service.lawyers.IAiLegalKnowledgeService;

/**
 * 法律知识库 服务层实现
 * 
 * @author AI Lawyers
 */
@Service
public class AiLegalKnowledgeServiceImpl implements IAiLegalKnowledgeService 
{
    @Autowired
    private AiLegalKnowledgeMapper aiLegalKnowledgeMapper;

    /**
     * 查询法律知识库
     * 
     * @param knowledgeId 法律知识库主键
     * @return 法律知识库
     */
    @Override
    public AiLegalKnowledge selectAiLegalKnowledgeByKnowledgeId(Long knowledgeId)
    {
        return aiLegalKnowledgeMapper.selectAiLegalKnowledgeById(knowledgeId);
    }

    /**
     * 查询法律知识库列表
     * 
     * @param aiLegalKnowledge 法律知识库
     * @return 法律知识库
     */
    @Override
    public List<AiLegalKnowledge> selectAiLegalKnowledgeList(AiLegalKnowledge aiLegalKnowledge)
    {
        return aiLegalKnowledgeMapper.selectAiLegalKnowledgeList(aiLegalKnowledge);
    }

    /**
     * 根据分类ID查询法律知识库列表
     * 
     * @param categoryId 分类ID
     * @return 法律知识库集合
     */
    @Override
    public List<AiLegalKnowledge> selectAiLegalKnowledgeByCategoryId(Long categoryId)
    {
        return aiLegalKnowledgeMapper.selectAiLegalKnowledgeByCategoryId(categoryId);
    }

    /**
     * 根据关键词搜索法律知识库
     * 
     * @param keyword 关键词
     * @return 法律知识库集合
     */
    @Override
    public List<AiLegalKnowledge> searchAiLegalKnowledge(String keyword)
    {
        return aiLegalKnowledgeMapper.searchAiLegalKnowledge(keyword);
    }

    /**
     * 新增法律知识库
     * 
     * @param aiLegalKnowledge 法律知识库
     * @return 结果
     */
    @Override
    public int insertAiLegalKnowledge(AiLegalKnowledge aiLegalKnowledge)
    {
        aiLegalKnowledge.setCreateTime(DateUtils.getNowDate());
        aiLegalKnowledge.setViewCount(0L); // 初始化浏览次数为0
        return aiLegalKnowledgeMapper.insertAiLegalKnowledge(aiLegalKnowledge);
    }

    /**
     * 修改法律知识库
     * 
     * @param aiLegalKnowledge 法律知识库
     * @return 结果
     */
    @Override
    public int updateAiLegalKnowledge(AiLegalKnowledge aiLegalKnowledge)
    {
        aiLegalKnowledge.setUpdateTime(DateUtils.getNowDate());
        return aiLegalKnowledgeMapper.updateAiLegalKnowledge(aiLegalKnowledge);
    }

    /**
     * 批量删除法律知识库
     * 
     * @param knowledgeIds 需要删除的法律知识库主键
     * @return 结果
     */
    @Override
    public int deleteAiLegalKnowledgeByKnowledgeIds(Long[] knowledgeIds)
    {
        return aiLegalKnowledgeMapper.deleteAiLegalKnowledgeByIds(knowledgeIds);
    }

    /**
     * 删除法律知识库信息
     * 
     * @param knowledgeId 法律知识库主键
     * @return 结果
     */
    @Override
    public int deleteAiLegalKnowledgeByKnowledgeId(Long knowledgeId)
    {
        return aiLegalKnowledgeMapper.deleteAiLegalKnowledgeById(knowledgeId);
    }

    /**
     * 增加知识库浏览次数
     * 
     * @param knowledgeId 知识库ID
     * @return 结果
     */
    @Override
    public int increaseViewCount(Long knowledgeId)
    {
        AiLegalKnowledge knowledge = selectAiLegalKnowledgeByKnowledgeId(knowledgeId);
        if (knowledge != null)
        {
            knowledge.setViewCount(knowledge.getViewCount() + 1);
            return updateAiLegalKnowledge(knowledge);
        }
        return 0;
    }

    /**
     * 导入法律知识库数据
     *
     * @param knowledgeList 知识库数据列表
     * @param isUpdateSupport 是否更新支持，如果已存在，则进行更新数据
     * @param operName 操作用户
     * @return 结果
     */
    @Override
    public String importKnowledge(List<AiLegalKnowledge> knowledgeList, Boolean isUpdateSupport, String operName)
    {
        if (StringUtils.isNull(knowledgeList) || knowledgeList.size() == 0)
        {
            throw new ServiceException("导入法律知识库数据不能为空！");
        }
        int successNum = 0;
        int failureNum = 0;
        StringBuilder successMsg = new StringBuilder();
        StringBuilder failureMsg = new StringBuilder();
        for (AiLegalKnowledge knowledge : knowledgeList)
        {
            try
            {
                // 判断这个知识库标题是否存在
                AiLegalKnowledge k = aiLegalKnowledgeMapper.selectAiLegalKnowledgeByTitle(knowledge.getTitle());
                if (StringUtils.isNull(k))
                {
                    knowledge.setCreateBy(operName);
                    knowledge.setCreateTime(DateUtils.getNowDate());
                    knowledge.setViewCount(0L);
                    knowledge.setAuditStatus("0"); // 待审核状态
                    this.insertAiLegalKnowledge(knowledge);
                    successNum++;
                    successMsg.append("<br/>" + successNum + "、知识库 " + knowledge.getTitle() + " 导入成功");
                }
                else if (isUpdateSupport)
                {
                    knowledge.setUpdateBy(operName);
                    knowledge.setUpdateTime(DateUtils.getNowDate());
                    this.updateAiLegalKnowledge(knowledge);
                    successNum++;
                    successMsg.append("<br/>" + successNum + "、知识库 " + knowledge.getTitle() + " 更新成功");
                }
                else
                {
                    failureNum++;
                    failureMsg.append("<br/>" + failureNum + "、知识库 " + knowledge.getTitle() + " 已存在");
                }
            }
            catch (Exception e)
            {
                failureNum++;
                String msg = "<br/>" + failureNum + "、知识库 " + knowledge.getTitle() + " 导入失败：";
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
     * 审核法律知识库
     * 
     * @param aiLegalKnowledge 法律知识库
     * @return 结果
     */
    @Override
    public int auditAiLegalKnowledge(AiLegalKnowledge aiLegalKnowledge)
    {
        if (StringUtils.isNull(aiLegalKnowledge) || StringUtils.isNull(aiLegalKnowledge.getKnowledgeId()))
        {
            throw new ServiceException("参数错误！");
        }
        
        AiLegalKnowledge knowledge = selectAiLegalKnowledgeByKnowledgeId(aiLegalKnowledge.getKnowledgeId());
        if (StringUtils.isNull(knowledge))
        {
            throw new ServiceException("法律知识库不存在！");
        }
        
        knowledge.setAuditStatus(aiLegalKnowledge.getAuditStatus());
        knowledge.setAuditBy(aiLegalKnowledge.getAuditBy());
        knowledge.setAuditTime(aiLegalKnowledge.getAuditTime());
        knowledge.setAuditRemark(aiLegalKnowledge.getAuditRemark());
        knowledge.setUpdateTime(DateUtils.getNowDate());
        
        return updateAiLegalKnowledge(knowledge);
    }
}