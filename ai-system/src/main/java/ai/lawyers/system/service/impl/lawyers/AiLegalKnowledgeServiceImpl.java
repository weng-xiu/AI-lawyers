package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.core.text.Convert;
import ai.lawyers.common.utils.DateUtils;
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
}