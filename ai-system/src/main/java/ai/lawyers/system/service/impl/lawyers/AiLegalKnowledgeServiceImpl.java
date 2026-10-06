package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.core.text.Convert;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeMapper;
import ai.lawyers.system.service.lawyers.IAiLegalKnowledgeService;
import ai.lawyers.system.service.lawyers.rag.IRagIndexService;

/**
 * 法律知识库 服务层实现
 * 
 * @author AI Lawyers
 */
@Service
public class AiLegalKnowledgeServiceImpl implements IAiLegalKnowledgeService 
{
    @Autowired
    private AiLegalKnowledgeMapper aiLegalKnowledgeMapper;

    /** T3 RAG：知识增删改/审核后异步重建该知识的分块向量索引 */
    @Autowired
    private IRagIndexService ragIndexService;

    /** P1-10：效力标记后给管理员发站内信提醒 */
    @Autowired
    private ai.lawyers.system.service.lawyers.queue.MessageNotifyDispatcher messageNotifyDispatcher;

    @Autowired
    private ai.lawyers.system.mapper.SysUserMapper sysUserMapper;

    /**
     * 查询法律知识库
     * 
     * @param knowledgeId 法律知识库主键
     * @return 法律知识库
     */
    @Override
    public AiLegalKnowledge selectAiLegalKnowledgeByKnowledgeId(Long knowledgeId)
    {
        return aiLegalKnowledgeMapper.selectAiLegalKnowledgeById(knowledgeId);
    }

    /**
     * 查询法律知识库列表
     * 
     * @param aiLegalKnowledge 法律知识库
     * @return 法律知识库
     */
    @Override
    public List<AiLegalKnowledge> selectAiLegalKnowledgeList(AiLegalKnowledge aiLegalKnowledge)
    {
        return aiLegalKnowledgeMapper.selectAiLegalKnowledgeList(aiLegalKnowledge);
    }

    /**
     * 根据分类ID查询法律知识库列表
     * 
     * @param categoryId 分类ID
     * @return 法律知识库集合
     */
    @Override
    public List<AiLegalKnowledge> selectAiLegalKnowledgeByCategoryId(Long categoryId)
    {
        return aiLegalKnowledgeMapper.selectAiLegalKnowledgeByCategoryId(categoryId);
    }

    /**
     * 根据关键词搜索法律知识库
     * 
     * @param keyword 关键词
     * @return 法律知识库集合
     */
    @Override
    public List<AiLegalKnowledge> searchAiLegalKnowledge(String keyword)
    {
        return aiLegalKnowledgeMapper.searchAiLegalKnowledge(keyword);
    }

    /**
     * 新增法律知识库
     * 
     * @param aiLegalKnowledge 法律知识库
     * @return 结果
     */
    @Override
    public int insertAiLegalKnowledge(AiLegalKnowledge aiLegalKnowledge)
    {
        aiLegalKnowledge.setCreateTime(DateUtils.getNowDate());
        aiLegalKnowledge.setViewCount(0L); // 初始化浏览次数为0
        int rows = aiLegalKnowledgeMapper.insertAiLegalKnowledge(aiLegalKnowledge);
        // T3 RAG：新增后异步建分块索引（仅审核通过且启用才会真正入索引）
        if (rows > 0)
        {
            ragIndexService.rebuildKnowledgeAsync(aiLegalKnowledge.getKnowledgeId());
        }
        return rows;
    }

    /**
     * 修改法律知识库
     * 
     * @param aiLegalKnowledge 法律知识库
     * @return 结果
     */
    @Override
    public int updateAiLegalKnowledge(AiLegalKnowledge aiLegalKnowledge)
    {
        aiLegalKnowledge.setUpdateTime(DateUtils.getNowDate());
        int rows = aiLegalKnowledgeMapper.updateAiLegalKnowledge(aiLegalKnowledge);
        // T3 RAG：内容/状态变更后异步重建分块索引（停用/未过审会在重建时清理旧分块）
        if (rows > 0 && aiLegalKnowledge.getKnowledgeId() != null)
        {
            ragIndexService.rebuildKnowledgeAsync(aiLegalKnowledge.getKnowledgeId());
        }
        return rows;
    }

    /**
     * 批量删除法律知识库
     * 
     * @param knowledgeIds 需要删除的法律知识库主键
     * @return 结果
     */
    @Override
    public int deleteAiLegalKnowledgeByKnowledgeIds(Long[] knowledgeIds)
    {
        int rows = aiLegalKnowledgeMapper.deleteAiLegalKnowledgeByIds(knowledgeIds);
        // T3 RAG：知识删除后异步清理其分块并刷新向量索引
        if (rows > 0)
        {
            ragIndexService.onKnowledgeDeleted(knowledgeIds);
        }
        return rows;
    }

    /**
     * 删除法律知识库信息
     * 
     * @param knowledgeId 法律知识库主键
     * @return 结果
     */
    @Override
    public int deleteAiLegalKnowledgeByKnowledgeId(Long knowledgeId)
    {
        int rows = aiLegalKnowledgeMapper.deleteAiLegalKnowledgeById(knowledgeId);
        // T3 RAG：知识删除后异步清理其分块并刷新向量索引
        if (rows > 0 && knowledgeId != null)
        {
            ragIndexService.onKnowledgeDeleted(new Long[]{knowledgeId});
        }
        return rows;
    }

    /**
     * 增加知识库浏览次数
     * 
     * @param knowledgeId 知识库ID
     * @return 结果
     */
    @Override
    public int increaseViewCount(Long knowledgeId)
    {
        AiLegalKnowledge knowledge = selectAiLegalKnowledgeByKnowledgeId(knowledgeId);
        if (knowledge != null)
        {
            knowledge.setViewCount(knowledge.getViewCount() + 1);
            return updateAiLegalKnowledge(knowledge);
        }
        return 0;
    }

    /**
     * 导入法律知识库数据
     *
     * @param knowledgeList 知识库数据列表
     * @param isUpdateSupport 是否更新支持，如果已存在，则进行更新数据
     * @param operName 操作用户
     * @return 结果
     */
    @Override
    public String importKnowledge(List<AiLegalKnowledge> knowledgeList, Boolean isUpdateSupport, String operName)
    {
        if (StringUtils.isNull(knowledgeList) || knowledgeList.size() == 0)
        {
            throw new ServiceException("导入法律知识库数据不能为空！");
        }
        int successNum = 0;
        int failureNum = 0;
        StringBuilder successMsg = new StringBuilder();
        StringBuilder failureMsg = new StringBuilder();
        for (AiLegalKnowledge knowledge : knowledgeList)
        {
            try
            {
                // 判断这个知识库标题是否存在
                AiLegalKnowledge k = aiLegalKnowledgeMapper.selectAiLegalKnowledgeByTitle(knowledge.getTitle());
                if (StringUtils.isNull(k))
                {
                    knowledge.setCreateBy(operName);
                    knowledge.setCreateTime(DateUtils.getNowDate());
                    knowledge.setViewCount(0L);
                    knowledge.setAuditStatus("0"); // 待审核状态
                    this.insertAiLegalKnowledge(knowledge);
                    successNum++;
                    successMsg.append("<br/>" + successNum + "、知识库 " + knowledge.getTitle() + " 导入成功");
                }
                else if (isUpdateSupport)
                {
                    knowledge.setUpdateBy(operName);
                    knowledge.setUpdateTime(DateUtils.getNowDate());
                    this.updateAiLegalKnowledge(knowledge);
                    successNum++;
                    successMsg.append("<br/>" + successNum + "、知识库 " + knowledge.getTitle() + " 更新成功");
                }
                else
                {
                    failureNum++;
                    failureMsg.append("<br/>" + failureNum + "、知识库 " + knowledge.getTitle() + " 已存在");
                }
            }
            catch (Exception e)
            {
                failureNum++;
                String msg = "<br/>" + failureNum + "、知识库 " + knowledge.getTitle() + " 导入失败：";
                failureMsg.append(msg + e.getMessage());
            }
        }
        if (failureNum > 0)
        {
            failureMsg.insert(0, "很抱歉，导入失败！共 " + failureNum + " 条数据格式不正确，错误如下：");
            throw new ServiceException(failureMsg.toString());
        }
        else
        {
            successMsg.insert(0, "恭喜您，数据已全部导入成功！共 " + successNum + " 条，数据如下：");
        }
        return successMsg.toString();
    }

    /**
     * 审核法律知识库
     * 
     * @param aiLegalKnowledge 法律知识库
     * @return 结果
     */
    @Override
    public int auditAiLegalKnowledge(AiLegalKnowledge aiLegalKnowledge)
    {
        if (StringUtils.isNull(aiLegalKnowledge) || StringUtils.isNull(aiLegalKnowledge.getKnowledgeId()))
        {
            throw new ServiceException("参数错误！");
        }
        
        AiLegalKnowledge knowledge = selectAiLegalKnowledgeByKnowledgeId(aiLegalKnowledge.getKnowledgeId());
        if (StringUtils.isNull(knowledge))
        {
            throw new ServiceException("法律知识库不存在！");
        }
        
        knowledge.setAuditStatus(aiLegalKnowledge.getAuditStatus());
        knowledge.setAuditBy(aiLegalKnowledge.getAuditBy());
        knowledge.setAuditTime(aiLegalKnowledge.getAuditTime());
        knowledge.setAuditRemark(aiLegalKnowledge.getAuditRemark());
        knowledge.setUpdateTime(DateUtils.getNowDate());
        
        return updateAiLegalKnowledge(knowledge);
    }

    /**
     * P1-10：标记法规效力状态。
     */
    @Override
    public int markValidStatus(Long knowledgeId, String validStatus, String reason, String operator)
    {
        if (StringUtils.isNull(knowledgeId))
        {
            throw new ServiceException("知识ID不能为空");
        }
        if (!"1".equals(validStatus) && !"2".equals(validStatus) && !"3".equals(validStatus))
        {
            throw new ServiceException("效力状态只能为：1现行有效、2已修订、3已失效");
        }
        AiLegalKnowledge knowledge = selectAiLegalKnowledgeByKnowledgeId(knowledgeId);
        if (StringUtils.isNull(knowledge))
        {
            throw new ServiceException("法律知识库不存在！");
        }
        knowledge.setValidStatus(validStatus);
        knowledge.setUpdateBy(StringUtils.isNotEmpty(operator) ? operator : "system");
        knowledge.setUpdateTime(DateUtils.getNowDate());
        if (StringUtils.isNotEmpty(reason))
        {
            knowledge.setAuditRemark(StringUtils.substring(reason, 0, 500));
        }
        int rows = aiLegalKnowledgeMapper.updateAiLegalKnowledge(knowledge);
        if (rows > 0)
        {
            // 失效/修订立即从向量索引移除（复用单条重建，内部已按 valid_status 过滤）
            ragIndexService.rebuildKnowledgeAsync(knowledgeId);
            notifyValidStatusChanged(knowledge, validStatus, reason);
        }
        return rows;
    }

    /** 给管理员角色发效力变更站内信提醒 */
    private void notifyValidStatusChanged(AiLegalKnowledge knowledge, String validStatus, String reason)
    {
        String label;
        switch (validStatus)
        {
            case "2": label = "已修订"; break;
            case "3": label = "已失效"; break;
            default: label = "现行有效"; break;
        }
        String title = "法规效力变更提醒：" + StringUtils.substring(knowledge.getTitle(), 0, 80);
        String content = "知识《" + knowledge.getTitle() + "》被标记为「" + label + "」，"
                + "已同步刷新 RAG 向量索引。"
                + (StringUtils.isNotEmpty(reason) ? "原因：" + reason : "");
        try
        {
            List<Long> adminIds = sysUserMapper.selectUserIdsByRoleKey("admin");
            if (adminIds != null)
            {
                for (Long uid : adminIds)
                {
                    messageNotifyDispatcher.notify(uid, "1", title,
                            StringUtils.substring(content, 0, 1000),
                            "knowledge", knowledge.getKnowledgeId(), "system");
                }
            }
        }
        catch (Exception e)
        {
            // 通知失败不影响主流程
            org.slf4j.LoggerFactory.getLogger(AiLegalKnowledgeServiceImpl.class)
                    .warn("法规效力变更通知发送失败 knowledgeId={}: {}", knowledge.getKnowledgeId(), e.getMessage());
        }
    }
}