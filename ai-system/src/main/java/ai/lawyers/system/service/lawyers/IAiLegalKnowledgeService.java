package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;

/**
 * 法律知识库 服务接口
 * 
 * @author AI Lawyers
 */
public interface IAiLegalKnowledgeService 
{
    /**
     * 查询法律知识库
     * 
     * @param knowledgeId 法律知识库主键
     * @return 法律知识库
     */
    public AiLegalKnowledge selectAiLegalKnowledgeByKnowledgeId(Long knowledgeId);

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
     * 批量删除法律知识库
     * 
     * @param knowledgeIds 需要删除的法律知识库主键集合
     * @return 结果
     */
    public int deleteAiLegalKnowledgeByKnowledgeIds(Long[] knowledgeIds);

    /**
     * 删除法律知识库信息
     * 
     * @param knowledgeId 法律知识库主键
     * @return 结果
     */
    public int deleteAiLegalKnowledgeByKnowledgeId(Long knowledgeId);

    /**
     * 增加知识库浏览次数
     * 
     * @param knowledgeId 知识库ID
     * @return 结果
     */
    public int increaseViewCount(Long knowledgeId);

    /**
     * 导入法律知识库数据
     *
     * @param knowledgeList 知识库数据列表
     * @param isUpdateSupport 是否更新支持，如果已存在，则进行更新数据
     * @param operName 操作用户
     * @return 结果
     */
    public String importKnowledge(List<AiLegalKnowledge> knowledgeList, Boolean isUpdateSupport, String operName);

    /**
     * 审核法律知识库
     * 
     * @param aiLegalKnowledge 法律知识库
     * @return 结果
     */
    public int auditAiLegalKnowledge(AiLegalKnowledge aiLegalKnowledge);
}