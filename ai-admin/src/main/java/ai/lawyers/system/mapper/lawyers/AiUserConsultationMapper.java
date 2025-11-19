package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiUserConsultation;

/**
 * 用户咨询Mapper接口
 * 
 * @author AI律师
 * @date 2023-11-19
 */
public interface AiUserConsultationMapper 
{
    /**
     * 查询用户咨询
     * 
     * @param consultationId 用户咨询主键
     * @return 用户咨询
     */
    public AiUserConsultation selectAiUserConsultationById(Long consultationId);

    /**
     * 查询用户咨询列表
     * 
     * @param aiUserConsultation 用户咨询
     * @return 用户咨询集合
     */
    public List<AiUserConsultation> selectAiUserConsultationList(AiUserConsultation aiUserConsultation);

    /**
     * 新增用户咨询
     * 
     * @param aiUserConsultation 用户咨询
     * @return 结果
     */
    public int insertAiUserConsultation(AiUserConsultation aiUserConsultation);

    /**
     * 修改用户咨询
     * 
     * @param aiUserConsultation 用户咨询
     * @return 结果
     */
    public int updateAiUserConsultation(AiUserConsultation aiUserConsultation);

    /**
     * 删除用户咨询
     * 
     * @param consultationId 用户咨询主键
     * @return 结果
     */
    public int deleteAiUserConsultationById(Long consultationId);

    /**
     * 批量删除用户咨询
     * 
     * @param consultationIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteAiUserConsultationByIds(Long[] consultationIds);
}