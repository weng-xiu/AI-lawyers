package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.core.text.Convert;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.system.domain.lawyers.AiLegalConsultation;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.mapper.lawyers.AiLegalConsultationMapper;
import ai.lawyers.system.service.lawyers.IAiLegalConsultationService;
import ai.lawyers.system.service.lawyers.IAiLegalKnowledgeService;

/**
 * 法律咨询记录 服务层实现
 * 
 * @author AI Lawyers
 */
@Service
public class AiLegalConsultationServiceImpl implements IAiLegalConsultationService 
{
    @Autowired
    private AiLegalConsultationMapper aiLegalConsultationMapper;
    
    @Autowired
    private IAiLegalKnowledgeService aiLegalKnowledgeService;

    /**
     * 查询法律咨询记录
     * 
     * @param consultationId 法律咨询记录主键
     * @return 法律咨询记录
     */
    @Override
    public AiLegalConsultation selectAiLegalConsultationByConsultationId(Long consultationId)
    {
        return aiLegalConsultationMapper.selectAiLegalConsultationById(consultationId);
    }

    /**
     * 查询法律咨询记录列表
     * 
     * @param aiLegalConsultation 法律咨询记录
     * @return 法律咨询记录
     */
    @Override
    public List<AiLegalConsultation> selectAiLegalConsultationList(AiLegalConsultation aiLegalConsultation)
    {
        return aiLegalConsultationMapper.selectAiLegalConsultationList(aiLegalConsultation);
    }

    /**
     * 查询用户的历史咨询记录
     * 
     * @param userId 用户ID
     * @return 法律咨询记录集合
     */
    @Override
    public List<AiLegalConsultation> selectAiLegalConsultationByUserId(Long userId)
    {
        return aiLegalConsultationMapper.selectAiLegalConsultationByUserId(userId);
    }

    /**
     * 新增法律咨询记录
     * 
     * @param aiLegalConsultation 法律咨询记录
     * @return 结果
     */
    @Override
    public int insertAiLegalConsultation(AiLegalConsultation aiLegalConsultation)
    {
        aiLegalConsultation.setCreateTime(DateUtils.getNowDate());
        return aiLegalConsultationMapper.insertAiLegalConsultation(aiLegalConsultation);
    }

    /**
     * 修改法律咨询记录
     * 
     * @param aiLegalConsultation 法律咨询记录
     * @return 结果
     */
    @Override
    public int updateAiLegalConsultation(AiLegalConsultation aiLegalConsultation)
    {
        aiLegalConsultation.setUpdateTime(DateUtils.getNowDate());
        return aiLegalConsultationMapper.updateAiLegalConsultation(aiLegalConsultation);
    }

    /**
     * 批量删除法律咨询记录
     * 
     * @param consultationIds 需要删除的法律咨询记录主键
     * @return 结果
     */
    @Override
    public int deleteAiLegalConsultationByConsultationIds(Long[] consultationIds)
    {
        return aiLegalConsultationMapper.deleteAiLegalConsultationByIds(consultationIds);
    }

    /**
     * 删除法律咨询记录信息
     * 
     * @param consultationId 法律咨询记录主键
     * @return 结果
     */
    @Override
    public int deleteAiLegalConsultationByConsultationId(Long consultationId)
    {
        return aiLegalConsultationMapper.deleteAiLegalConsultationById(consultationId);
    }

    /**
     * AI智能回答法律咨询
     * 
     * @param question 用户问题
     * @param categoryId 分类ID
     * @return AI生成的回答
     */
    @Override
    public String generateLegalAnswer(String question, Long categoryId)
    {
        // 根据问题关键词搜索相关法律知识
        List<AiLegalKnowledge> knowledgeList = aiLegalKnowledgeService.searchAiLegalKnowledge(question);
        
        if (knowledgeList != null && !knowledgeList.isEmpty())
        {
            // 如果找到相关知识，返回最相关的知识内容
            AiLegalKnowledge knowledge = knowledgeList.get(0);
            // 增加浏览次数
            aiLegalKnowledgeService.increaseViewCount(knowledge.getKnowledgeId());
            return knowledge.getContent();
        }
        else
        {
            // 如果没有找到相关知识，返回默认回答
            return "很抱歉，我暂时无法回答您的问题。建议您咨询专业律师获取更准确的法律建议。";
        }
    }
    
    /**
     * 获取咨询记录统计数据
     * 
     * @return 统计数据
     */
    @Override
    public java.util.Map<String, Object> getConsultationStatistics()
    {
        java.util.Map<String, Object> statistics = new java.util.HashMap<>();
        
        // 总咨询数
        Long totalCount = aiLegalConsultationMapper.countTotalConsultations();
        statistics.put("totalCount", totalCount != null ? totalCount : 0);
        
        // 今日咨询数
        Long todayCount = aiLegalConsultationMapper.countTodayConsultations();
        statistics.put("todayCount", todayCount != null ? todayCount : 0);
        
        // 本周咨询数
        Long weekCount = aiLegalConsultationMapper.countWeekConsultations();
        statistics.put("weekCount", weekCount != null ? weekCount : 0);
        
        // 本月咨询数
        Long monthCount = aiLegalConsultationMapper.countMonthConsultations();
        statistics.put("monthCount", monthCount != null ? monthCount : 0);
        
        // 已完成咨询数
        Long completedCount = aiLegalConsultationMapper.countConsultationsByStatus("1");
        statistics.put("completedCount", completedCount != null ? completedCount : 0);
        
        // 处理中咨询数
        Long processingCount = aiLegalConsultationMapper.countConsultationsByStatus("0");
        statistics.put("processingCount", processingCount != null ? processingCount : 0);
        
        // 失败咨询数
        Long failedCount = aiLegalConsultationMapper.countConsultationsByStatus("2");
        statistics.put("failedCount", failedCount != null ? failedCount : 0);
        
        // 平均评分
        Double avgRating = aiLegalConsultationMapper.getAverageRating();
        statistics.put("avgRating", avgRating != null ? avgRating : 0.0);
        
        return statistics;
    }
    
    /**
     * 获取按分类统计的咨询数据
     * 
     * @return 分类统计数据
     */
    @Override
    public java.util.List<java.util.Map<String, Object>> getConsultationByCategory()
    {
        return aiLegalConsultationMapper.countConsultationsByCategory();
    }
    
    /**
     * 获取按日期统计的咨询数据
     * 
     * @param days 统计天数
     * @return 日期统计数据
     */
    @Override
    public java.util.List<java.util.Map<String, Object>> getConsultationByDate(Integer days)
    {
        if (days == null || days <= 0) {
            days = 7; // 默认统计7天
        }
        return aiLegalConsultationMapper.countConsultationsByDate(days);
    }
}