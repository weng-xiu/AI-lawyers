package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;

/**
 * 法律知识库 数据层
 * 
 * @author AI Lawyers
 */
public interface AiLegalKnowledgeMapper
{
    /**
     * 查询法律知识库
     * 
     * @param knowledgeId 法律知识库ID
     * @return 法律知识库
     */
    public AiLegalKnowledge selectAiLegalKnowledgeById(Long knowledgeId);

    /**
     * 查询法律知识库列表
     * 
     * @param aiLegalKnowledge 法律知识库
     * @return 法律知识库集合
     */
    public List<AiLegalKnowledge> selectAiLegalKnowledgeList(AiLegalKnowledge aiLegalKnowledge);

    /**
     * 根据分类ID查询法律知识库列表
     * 
     * @param categoryId 分类ID
     * @return 法律知识库集合
     */
    public List<AiLegalKnowledge> selectAiLegalKnowledgeByCategoryId(Long categoryId);

    /**
     * 根据关键词搜索法律知识库
     * 
     * @param keyword 关键词
     * @return 法律知识库集合
     */
    public List<AiLegalKnowledge> searchAiLegalKnowledge(String keyword);

    /**
     * 新增法律知识库
     * 
     * @param aiLegalKnowledge 法律知识库
     * @return 结果
     */
    public int insertAiLegalKnowledge(AiLegalKnowledge aiLegalKnowledge);

    /**
     * 修改法律知识库
     * 
     * @param aiLegalKnowledge 法律知识库
     * @return 结果
     */
    public int updateAiLegalKnowledge(AiLegalKnowledge aiLegalKnowledge);

    /**
     * 删除法律知识库
     * 
     * @param knowledgeId 法律知识库ID
     * @return 结果
     */
    public int deleteAiLegalKnowledgeById(Long knowledgeId);

    /**
     * 批量删除法律知识库
     * 
     * @param knowledgeIds 需要删除的数据ID
     * @return 结果
     */
    public int deleteAiLegalKnowledgeByIds(Long[] knowledgeIds);
}