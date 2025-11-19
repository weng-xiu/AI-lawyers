package ai.lawyers.system.service.lawyers;

import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;
import ai.lawyers.system.domain.lawyers.AiUserConsultation;
import ai.lawyers.system.domain.lawyers.AiUserEvaluation;

/**
 * 用户咨询Service接口
 * 
 * @author AI律师
 * @date 2023-11-19
 */
public interface IAiUserConsultationService 
{
    /**
     * 查询用户咨询
     * 
     * @param id 用户咨询主键
     * @return 用户咨询
     */
    public AiUserConsultation selectAiUserConsultationById(Long id);

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
     * 批量删除用户咨询
     * 
     * @param ids 需要删除的用户咨询主键集合
     * @return 结果
     */
    public int deleteAiUserConsultationByIds(Long[] ids);

    /**
     * 删除用户咨询信息
     * 
     * @param id 用户咨询主键
     * @return 结果
     */
    public int deleteAiUserConsultationById(Long id);

    /**
     * 提交法律咨询
     * 
     * @param category 问题分类
     * @param content 问题内容
     * @param files 附件文件列表
     * @return 咨询记录
     */
    public AiUserConsultation submitConsultation(String category, String content, List<MultipartFile> files);

    /**
     * 提交咨询评价
     * 
     * @param evaluation 评价信息
     * @return 结果
     */
    public int submitEvaluation(AiUserEvaluation evaluation);

    /**
     * 获取问题分类列表
     * 
     * @return 分类列表
     */
    public List<Map<String, Object>> getQuestionCategories();

    /**
     * 处理AI咨询请求
     * 
     * @param consultation 咨询信息
     * @return 处理后的咨询信息
     */
    public AiUserConsultation processAIConsultation(AiUserConsultation consultation);
}