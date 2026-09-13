package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiKnowledgeAudit;

/**
 * 法律知识审核发布 Service（F7）
 *
 * <p>状态机：草稿 → SUBMIT 待审核(0) → APPROVE 通过(1) / REJECT 不通过(2)；
 * 通过后 PUBLISH 发布上架（status=0，版本号+1）；OFFLINE 下线（status=1）。
 * 每次动作写 ai_knowledge_audit 留痕；失效法条（valid_status=2）不进入 RAG 检索与索引。</p>
 *
 * @author ai-lawyers
 */
public interface IAiKnowledgeAuditService
{
    public List<AiKnowledgeAudit> selectAuditList(AiKnowledgeAudit query);

    /** 提交审核 */
    public void submit(Long knowledgeId, String operator);

    /** 审核通过 */
    public void approve(Long knowledgeId, String opinion, String auditor);

    /** 审核驳回 */
    public void reject(Long knowledgeId, String opinion, String auditor);

    /** 发布上架（审核通过后，版本号+1） */
    public void publish(Long knowledgeId, String opinion, String auditor);

    /** 下线（停止上架与检索） */
    public void offline(Long knowledgeId, String opinion, String auditor);
}
