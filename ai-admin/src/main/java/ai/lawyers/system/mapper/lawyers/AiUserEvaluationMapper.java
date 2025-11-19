package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiUserEvaluation;

/**
 * 用户评价Mapper接口
 * 
 * @author AI律师
 * @date 2023-11-19
 */
public interface AiUserEvaluationMapper 
{
    /**
     * 查询用户评价
     * 
     * @param evaluationId 用户评价主键
     * @return 用户评价
     */
    public AiUserEvaluation selectAiUserEvaluationById(Long evaluationId);

    /**
     * 查询用户评价列表
     * 
     * @param aiUserEvaluation 用户评价
     * @return 用户评价集合
     */
    public List<AiUserEvaluation> selectAiUserEvaluationList(AiUserEvaluation aiUserEvaluation);

    /**
     * 新增用户评价
     * 
     * @param aiUserEvaluation 用户评价
     * @return 结果
     */
    public int insertAiUserEvaluation(AiUserEvaluation aiUserEvaluation);

    /**
     * 修改用户评价
     * 
     * @param aiUserEvaluation 用户评价
     * @return 结果
     */
    public int updateAiUserEvaluation(AiUserEvaluation aiUserEvaluation);

    /**
     * 删除用户评价
     * 
     * @param evaluationId 用户评价主键
     * @return 结果
     */
    public int deleteAiUserEvaluationById(Long evaluationId);

    /**
     * 批量删除用户评价
     * 
     * @param evaluationIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteAiUserEvaluationByIds(Long[] evaluationIds);
}