package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiLegalConsultation;

/**
 * 法律咨询记录 服务接口
 * 
 * @author AI Lawyers
 */
public interface IAiLegalConsultationService 
{
    /**
     * 查询法律咨询记录
     * 
     * @param consultationId 法律咨询记录主键
     * @return 法律咨询记录
     */
    public AiLegalConsultation selectAiLegalConsultationByConsultationId(Long consultationId);

    /**
     * 查询法律咨询记录列表
     * 
     * @param aiLegalConsultation 法律咨询记录
     * @return 法律咨询记录集合
     */
    public List<AiLegalConsultation> selectAiLegalConsultationList(AiLegalConsultation aiLegalConsultation);

    /**
     * 查询用户的历史咨询记录
     * 
     * @param userId 用户ID
     * @return 法律咨询记录集合
     */
    public List<AiLegalConsultation> selectAiLegalConsultationByUserId(Long userId);

    /**
     * 新增法律咨询记录
     * 
     * @param aiLegalConsultation 法律咨询记录
     * @return 结果
     */
    public int insertAiLegalConsultation(AiLegalConsultation aiLegalConsultation);

    /**
     * 修改法律咨询记录
     * 
     * @param aiLegalConsultation 法律咨询记录
     * @return 结果
     */
    public int updateAiLegalConsultation(AiLegalConsultation aiLegalConsultation);

    /**
     * 批量删除法律咨询记录
     * 
     * @param consultationIds 需要删除的法律咨询记录主键集合
     * @return 结果
     */
    public int deleteAiLegalConsultationByConsultationIds(Long[] consultationIds);

    /**
     * 删除法律咨询记录信息
     * 
     * @param consultationId 法律咨询记录主键
     * @return 结果
     */
    public int deleteAiLegalConsultationByConsultationId(Long consultationId);

    /**
     * AI智能回答法律咨询
     * 
     * @param question 用户问题
     * @param categoryId 分类ID
     * @return AI生成的回答
     */
    public String generateLegalAnswer(String question, Long categoryId);
}