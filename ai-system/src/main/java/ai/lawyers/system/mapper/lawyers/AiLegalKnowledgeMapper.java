package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import org.apache.ibatis.annotations.Param;
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
     * 根据标题查询法律知识库
     * 
     * @param title 知识库标题
     * @return 法律知识库
     */
    public AiLegalKnowledge selectAiLegalKnowledgeByTitle(String title);

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
     * F10：公众端常见问题（仅启用/审核通过/现行有效的知识，按浏览量倒序取前 N 条）。
     *
     * @param categoryId 知识分类ID（可空）
     * @param limit      返回条数上限（服务端已收敛到安全范围）
     * @return 常见问题集合
     */
    public List<AiLegalKnowledge> selectFaqKnowledge(@Param("categoryId") Long categoryId,
                                                     @Param("limit") int limit);

    /**
     * T3：加载启用且审核通过的知识（供分块/向量化建索引）
     *
     * @return 可建索引的知识集合
     */
    public List<AiLegalKnowledge> selectIndexableKnowledge();

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
     * P1-10：按来源去重键计数（source_url 存 source://call/{id} 或 source://ticket/{id}）。
     */
    public int countBySourceUrl(String sourceUrl);

    /**
     * 批量删除法律知识库
     * 
     * @param knowledgeIds 需要删除的数据ID
     * @return 结果
     */
    public int deleteAiLegalKnowledgeByIds(Long[] knowledgeIds);
}