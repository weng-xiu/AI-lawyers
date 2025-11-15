package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiLegalConsultation;

/**
 * 法律咨询记录 数据层
 * 
 * @author AI Lawyers
 */
public interface AiLegalConsultationMapper
{
    /**
     * 查询法律咨询记录
     * 
     * @param consultationId 法律咨询记录ID
     * @return 法律咨询记录
     */
    public AiLegalConsultation selectAiLegalConsultationById(Long consultationId);

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
     * 删除法律咨询记录
     * 
     * @param consultationId 法律咨询记录ID
     * @return 结果
     */
    public int deleteAiLegalConsultationById(Long consultationId);

    /**
     * 批量删除法律咨询记录
     * 
     * @param consultationIds 需要删除的数据ID
     * @return 结果
     */
    public int deleteAiLegalConsultationByIds(Long[] consultationIds);
}