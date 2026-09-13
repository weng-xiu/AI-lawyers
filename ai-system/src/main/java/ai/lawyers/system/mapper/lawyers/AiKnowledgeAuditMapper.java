package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiKnowledgeAudit;

/**
 * 知识库审核发布流水 Mapper（F7）
 *
 * @author ai-lawyers
 */
public interface AiKnowledgeAuditMapper
{
    public AiKnowledgeAudit selectAiKnowledgeAuditByAuditId(Long auditId);

    public List<AiKnowledgeAudit> selectAiKnowledgeAuditList(AiKnowledgeAudit query);

    public int insertAiKnowledgeAudit(AiKnowledgeAudit audit);

    /**
     * 变更知识审核状态（审核通过/驳回/下线），同步写审核人/时间/意见。
     */
    public int updateKnowledgeAuditState(@org.apache.ibatis.annotations.Param("knowledgeId") Long knowledgeId,
                                         @org.apache.ibatis.annotations.Param("auditStatus") String auditStatus,
                                         @org.apache.ibatis.annotations.Param("status") String status,
                                         @org.apache.ibatis.annotations.Param("auditBy") String auditBy,
                                         @org.apache.ibatis.annotations.Param("auditRemark") String auditRemark);

    /** 发布版本号 +1（通过审核发布时） */
    public int incrementPublishVersion(@org.apache.ibatis.annotations.Param("knowledgeId") Long knowledgeId);
}
