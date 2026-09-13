package ai.lawyers.system.service.impl.lawyers;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.domain.lawyers.AiKnowledgeAudit;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.mapper.lawyers.AiKnowledgeAuditMapper;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeMapper;
import ai.lawyers.system.service.lawyers.IAiKnowledgeAuditService;

/**
 * 法律知识审核发布 Service 实现（F7）
 *
 * @author ai-lawyers
 */
@Service
public class AiKnowledgeAuditServiceImpl implements IAiKnowledgeAuditService
{
    @Autowired
    private AiKnowledgeAuditMapper auditMapper;

    @Autowired
    private AiLegalKnowledgeMapper knowledgeMapper;

    @Override
    public List<AiKnowledgeAudit> selectAuditList(AiKnowledgeAudit query)
    {
        return auditMapper.selectAiKnowledgeAuditList(query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long knowledgeId, String operator)
    {
        AiLegalKnowledge k = requireKnowledge(knowledgeId);
        String from = k.getAuditStatus();
        auditMapper.updateKnowledgeAuditState(knowledgeId, "0", k.getStatus(), operator, "提交审核");
        writeLog(knowledgeId, k.getPublishVersion(), "SUBMIT", from, "0", "提交审核", operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long knowledgeId, String opinion, String auditor)
    {
        AiLegalKnowledge k = requireKnowledge(knowledgeId);
        if (!"0".equals(k.getAuditStatus()))
        {
            throw new ServiceException("仅待审核状态的知识可以审核通过");
        }
        auditMapper.updateKnowledgeAuditState(knowledgeId, "1", k.getStatus(), auditor, opinion);
        writeLog(knowledgeId, k.getPublishVersion(), "APPROVE", "0", "1", opinion, auditor);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long knowledgeId, String opinion, String auditor)
    {
        AiLegalKnowledge k = requireKnowledge(knowledgeId);
        if (!"0".equals(k.getAuditStatus()))
        {
            throw new ServiceException("仅待审核状态的知识可以驳回");
        }
        auditMapper.updateKnowledgeAuditState(knowledgeId, "2", k.getStatus(), auditor, opinion);
        writeLog(knowledgeId, k.getPublishVersion(), "REJECT", "0", "2", opinion, auditor);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long knowledgeId, String opinion, String auditor)
    {
        AiLegalKnowledge k = requireKnowledge(knowledgeId);
        if ("2".equals(k.getAuditStatus()))
        {
            throw new ServiceException("审核驳回的知识不能发布，请重新提交审核");
        }
        // 发布即审核通过并上架
        auditMapper.updateKnowledgeAuditState(knowledgeId, "1", "0", auditor, opinion);
        auditMapper.incrementPublishVersion(knowledgeId);
        Integer version = k.getPublishVersion() == null ? 1 : k.getPublishVersion() + 1;
        writeLog(knowledgeId, version, "PUBLISH", "0", "PUBLISHED", opinion, auditor);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offline(Long knowledgeId, String opinion, String auditor)
    {
        AiLegalKnowledge k = requireKnowledge(knowledgeId);
        auditMapper.updateKnowledgeAuditState(knowledgeId,
                k.getAuditStatus() == null ? "1" : k.getAuditStatus(), "1", auditor, opinion);
        writeLog(knowledgeId, k.getPublishVersion(), "OFFLINE", "0", "OFFLINE", opinion, auditor);
    }

    private AiLegalKnowledge requireKnowledge(Long knowledgeId)
    {
        if (knowledgeId == null)
        {
            throw new ServiceException("知识ID不能为空");
        }
        AiLegalKnowledge k = knowledgeMapper.selectAiLegalKnowledgeById(knowledgeId);
        if (k == null)
        {
            throw new ServiceException("知识条目不存在");
        }
        return k;
    }

    private void writeLog(Long knowledgeId, Integer version, String action, String from, String to,
                          String opinion, String auditor)
    {
        AiKnowledgeAudit audit = new AiKnowledgeAudit();
        audit.setKnowledgeId(knowledgeId);
        audit.setPublishVersion(version);
        audit.setAction(action);
        audit.setFromStatus(from);
        audit.setToStatus(to);
        audit.setAuditOpinion(opinion);
        audit.setAuditor(auditor);
        audit.setAuditTime(new Date());
        audit.setCreateBy(auditor);
        auditMapper.insertAiKnowledgeAudit(audit);
    }
}
