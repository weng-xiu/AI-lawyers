package ai.lawyers.system.service.lawyers.impl;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Date;
import java.util.ArrayList;
import java.util.UUID;
import java.io.File;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.common.core.text.Convert;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.domain.lawyers.AiUserConsultation;
import ai.lawyers.system.domain.lawyers.AiUserEvaluation;
import ai.lawyers.system.mapper.lawyers.AiUserConsultationMapper;
import ai.lawyers.system.mapper.lawyers.AiUserEvaluationMapper;
import ai.lawyers.system.service.lawyers.IAiUserConsultationService;

/**
 * 用户咨询Service业务层处理
 * 
 * @author AI律师
 * @date 2023-11-19
 */
@Service
public class AiUserConsultationServiceImpl implements IAiUserConsultationService 
{
    @Autowired
    private AiUserConsultationMapper aiUserConsultationMapper;
    
    @Autowired
    private AiUserEvaluationMapper aiUserEvaluationMapper;

    /** F8 公众端文本内容安全过滤 */
    @Autowired
    private ContentSafetyService contentSafetyService;

    @Value("${file.path}")
    private String filePath;

    /**
     * 查询用户咨询
     * 
     * @param id 用户咨询主键
     * @return 用户咨询
     */
    @Override
    public AiUserConsultation selectAiUserConsultationById(Long id)
    {
        return aiUserConsultationMapper.selectAiUserConsultationById(id);
    }

    /**
     * 查询用户咨询列表
     * 
     * @param aiUserConsultation 用户咨询
     * @return 用户咨询
     */
    @Override
    public List<AiUserConsultation> selectAiUserConsultationList(AiUserConsultation aiUserConsultation)
    {
        return aiUserConsultationMapper.selectAiUserConsultationList(aiUserConsultation);
    }

    /**
     * 新增用户咨询
     * 
     * @param aiUserConsultation 用户咨询
     * @return 结果
     */
    @Override
    public int insertAiUserConsultation(AiUserConsultation aiUserConsultation)
    {
        aiUserConsultation.setCreateTime(DateUtils.getNowDate());
        return aiUserConsultationMapper.insertAiUserConsultation(aiUserConsultation);
    }

    /**
     * 修改用户咨询
     * 
     * @param aiUserConsultation 用户咨询
     * @return 结果
     */
    @Override
    public int updateAiUserConsultation(AiUserConsultation aiUserConsultation)
    {
        aiUserConsultation.setUpdateTime(DateUtils.getNowDate());
        return aiUserConsultationMapper.updateAiUserConsultation(aiUserConsultation);
    }

    /**
     * 批量删除用户咨询
     * 
     * @param ids 需要删除的用户咨询主键
     * @return 结果
     */
    @Override
    public int deleteAiUserConsultationByIds(Long[] ids)
    {
        return aiUserConsultationMapper.deleteAiUserConsultationByIds(ids);
    }

    /**
     * 删除用户咨询信息
     * 
     * @param id 用户咨询主键
     * @return 结果
     */
    @Override
    public int deleteAiUserConsultationById(Long id)
    {
        return aiUserConsultationMapper.deleteAiUserConsultationById(id);
    }

    /**
     * 提交法律咨询
     * 
     * @param category 问题分类
     * @param content 问题内容
     * @param files 附件文件列表
     * @return 咨询记录
     */
    @Override
    public AiUserConsultation submitConsultation(String category, String content, List<MultipartFile> files)
    {
        // F8 安全基线：内容长度 + 本地敏感词校验（坐席端/公众端共用，命中即拒绝入库）
        contentSafetyService.validateConsultation(category, content);

        // 创建咨询记录
        AiUserConsultation consultation = new AiUserConsultation();
        consultation.setUserId(SecurityUtils.getUserId());
        consultation.setCategory(category);
        consultation.setContent(content);
        consultation.setStatus("PROCESSING"); // 处理中
        consultation.setCreateTime(new Date());
        
        // 处理附件
        StringBuilder attachmentPaths = new StringBuilder();
        if (files != null && !files.isEmpty()) {
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    try {
                        // 生成唯一文件名
                        String originalFilename = file.getOriginalFilename();
                        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
                        String newFilename = UUID.randomUUID().toString() + extension;
                        
                        // 创建文件目录（取绝对路径：MultipartFile.transferTo 对相对路径会按
                        // Tomcat 临时目录解析，导致 FileNotFoundException，F8 联调修复）
                        File dir = new File(filePath + "/consultation/").getAbsoluteFile();
                        if (!dir.exists() && !dir.mkdirs()) {
                            throw new RuntimeException("上传目录创建失败: " + dir.getAbsolutePath());
                        }
                        
                        // 保存文件
                        File destFile = new File(dir, newFilename);
                        file.transferTo(destFile.getAbsoluteFile());
                        
                        // 记录文件路径
                        if (attachmentPaths.length() > 0) {
                            attachmentPaths.append(",");
                        }
                        attachmentPaths.append("/consultation/").append(newFilename);
                    } catch (IOException e) {
                        throw new RuntimeException("文件上传失败: " + e.getMessage());
                    }
                }
            }
        }
        
        if (attachmentPaths.length() > 0) {
            consultation.setAttachments(attachmentPaths.toString());
        }
        
        // 保存咨询记录
        aiUserConsultationMapper.insertAiUserConsultation(consultation);
        
        // 异步处理AI咨询
        processAIConsultation(consultation);
        
        return consultation;
    }

    /**
     * 提交咨询评价
     * 
     * @param evaluation 评价信息
     * @return 结果
     */
    @Override
    public int submitEvaluation(AiUserEvaluation evaluation)
    {
        contentSafetyService.validateFeedback(evaluation.getFeedback());
        evaluation.setCreateTime(new Date());
        evaluation.setUserId(SecurityUtils.getUserId());
        return aiUserEvaluationMapper.insertAiUserEvaluation(evaluation);
    }

    /**
     * 公众端：对象级归属校验——仅允许访问本人咨询，防 IDOR 遍历
     */
    @Override
    public AiUserConsultation selectOwnConsultationById(Long consultationId)
    {
        AiUserConsultation consultation = aiUserConsultationMapper.selectAiUserConsultationById(consultationId);
        if (consultation == null)
        {
            throw new ServiceException("咨询记录不存在");
        }
        Long currentUserId = SecurityUtils.getUserId();
        if (!currentUserId.equals(consultation.getUserId()))
        {
            // 不暴露记录是否存在，统一按"不存在"返回
            throw new ServiceException("咨询记录不存在");
        }
        return consultation;
    }

    /**
     * 公众端：评价提交（咨询归属校验 + 一咨询一评价 + 四维评分兜底 + 文本安全）
     */
    @Override
    public int submitOwnEvaluation(AiUserEvaluation evaluation)
    {
        if (evaluation.getConsultationId() == null)
        {
            throw new ServiceException("缺少咨询ID");
        }
        if (evaluation.getOverallRating() == null
                || evaluation.getOverallRating() < 1 || evaluation.getOverallRating() > 5)
        {
            throw new ServiceException("请先选择总体评分（1-5分）");
        }
        contentSafetyService.validateFeedback(evaluation.getFeedback());

        AiUserConsultation consultation = selectOwnConsultationById(evaluation.getConsultationId());
        if (!"COMPLETED".equals(consultation.getStatus()))
        {
            throw new ServiceException("咨询尚未完成，暂不能评价");
        }

        AiUserEvaluation query = new AiUserEvaluation();
        query.setConsultationId(evaluation.getConsultationId());
        query.setUserId(SecurityUtils.getUserId());
        List<AiUserEvaluation> existed = aiUserEvaluationMapper.selectAiUserEvaluationList(query);
        if (existed != null && !existed.isEmpty())
        {
            throw new ServiceException("该咨询已评价，请勿重复提交");
        }

        // 四维评分缺省时以总体评分兜底（公众端仅采集总体评分 + 文字反馈）
        Integer overall = evaluation.getOverallRating();
        if (evaluation.getProfessionalismRating() == null)
        {
            evaluation.setProfessionalismRating(overall);
        }
        if (evaluation.getResponsivenessRating() == null)
        {
            evaluation.setResponsivenessRating(overall);
        }
        if (evaluation.getQualityRating() == null)
        {
            evaluation.setQualityRating(overall);
        }

        evaluation.setEvaluationId(null);
        evaluation.setUserId(SecurityUtils.getUserId());
        evaluation.setCreateTime(new Date());
        return aiUserEvaluationMapper.insertAiUserEvaluation(evaluation);
    }

    /**
     * 获取问题分类列表
     * 
     * @return 分类列表
     */
    @Override
    public List<Map<String, Object>> getQuestionCategories()
    {
        List<Map<String, Object>> categories = new ArrayList<>();
        
        Map<String, Object> category1 = new HashMap<>();
        category1.put("value", "marriage_family");
        category1.put("label", "婚姻家庭");
        categories.add(category1);
        
        Map<String, Object> category2 = new HashMap<>();
        category2.put("value", "labor_dispute");
        category2.put("label", "劳动纠纷");
        categories.add(category2);
        
        Map<String, Object> category3 = new HashMap<>();
        category3.put("value", "contract_dispute");
        category3.put("label", "合同纠纷");
        categories.add(category3);
        
        Map<String, Object> category4 = new HashMap<>();
        category4.put("value", "property_dispute");
        category4.put("label", "财产纠纷");
        categories.add(category4);
        
        Map<String, Object> category5 = new HashMap<>();
        category5.put("value", "criminal_case");
        category5.put("label", "刑事案件");
        categories.add(category5);
        
        Map<String, Object> category6 = new HashMap<>();
        category6.put("value", "intellectual_property");
        category6.put("label", "知识产权");
        categories.add(category6);
        
        Map<String, Object> category7 = new HashMap<>();
        category7.put("value", "consumer_rights");
        category7.put("label", "消费者权益");
        categories.add(category7);
        
        Map<String, Object> category8 = new HashMap<>();
        category8.put("value", "other");
        category8.put("label", "其他");
        categories.add(category8);
        
        return categories;
    }

    /**
     * 处理AI咨询请求
     * 
     * @param consultation 咨询信息
     * @return 处理后的咨询信息
     */
    @Override
    public AiUserConsultation processAIConsultation(AiUserConsultation consultation)
    {
        // 模拟AI处理过程
        try {
            // 更新状态为处理中
            consultation.setStatus("PROCESSING");
            aiUserConsultationMapper.updateAiUserConsultation(consultation);
            
            // 模拟AI处理时间
            Thread.sleep(3000);
            
            // 生成AI回答
            String aiAnswer = generateAIAnswer(consultation.getCategory(), consultation.getContent());
            String relatedLaws = generateRelatedLaws(consultation.getCategory());
            String relatedCases = generateRelatedCases(consultation.getCategory());
            Double confidence = generateConfidence();
            
            // 更新咨询结果
            consultation.setAiAnswer(aiAnswer);
            consultation.setRelatedLaws(relatedLaws);
            consultation.setRelatedCases(relatedCases);
            consultation.setConfidence(confidence);
            consultation.setStatus("COMPLETED");
            consultation.setUpdateTime(new Date());
            
            aiUserConsultationMapper.updateAiUserConsultation(consultation);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            consultation.setStatus("FAILED");
            consultation.setUpdateTime(new Date());
            aiUserConsultationMapper.updateAiUserConsultation(consultation);
        }
        
        return consultation;
    }
    
    /**
     * 生成AI回答
     */
    private String generateAIAnswer(String category, String content)
    {
        // 这里应该调用真实的AI服务，暂时返回模拟数据
        return "根据您提供的" + getCategoryName(category) + "问题，我为您提供以下法律建议：\n\n" +
               "1. 首先，您需要了解相关法律法规的基本规定...\n" +
               "2. 其次，针对您的具体情况，建议采取以下措施...\n" +
               "3. 最后，如果问题无法通过协商解决，可以考虑以下法律途径...\n\n" +
               "请注意，以上回答仅供参考，具体法律问题建议咨询专业律师。";
    }
    
    /**
     * 生成相关法条
     */
    private String generateRelatedLaws(String category)
    {
        // 这里应该根据分类查询相关法条，暂时返回模拟数据
        return "1. 《中华人民共和国民法典》第XXX条\n" +
               "2. 《中华人民共和国民事诉讼法》第XXX条\n" +
               "3. 《最高人民法院关于适用<中华人民共和国民法典>的解释》第XXX条";
    }
    
    /**
     * 生成相关案例
     */
    private String generateRelatedCases(String category)
    {
        // 这里应该根据分类查询相关案例，暂时返回模拟数据
        return "1. 案例一：XXX诉XXX纠纷案（案号：(2023)XX民初XX号）\n" +
               "2. 案例二：XXX诉XXX合同纠纷案（案号：(2023)XX民终XX号）";
    }
    
    /**
     * 生成置信度
     */
    private Double generateConfidence()
    {
        // 生成0.8-0.95之间的随机置信度
        return 0.8 + Math.random() * 0.15;
    }
    
    /**
     * 获取分类名称
     */
    private String getCategoryName(String category)
    {
        switch (category) {
            case "marriage_family": return "婚姻家庭";
            case "labor_dispute": return "劳动纠纷";
            case "contract_dispute": return "合同纠纷";
            case "property_dispute": return "财产纠纷";
            case "criminal_case": return "刑事案件";
            case "intellectual_property": return "知识产权";
            case "consumer_rights": return "消费者权益";
            default: return "其他";
        }
    }
}