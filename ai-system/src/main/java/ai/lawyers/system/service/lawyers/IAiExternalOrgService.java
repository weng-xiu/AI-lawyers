package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiExternalOrg;

/**
 * 协同外部机构台账 Service（F3）
 *
 * @author ai-lawyers
 */
public interface IAiExternalOrgService
{
    public AiExternalOrg selectAiExternalOrgByOrgId(Long orgId);

    public List<AiExternalOrg> selectAiExternalOrgList(AiExternalOrg query);

    /**
     * 公众端服务导航：仅查询启用机构，返回结果不包含对接密钥等敏感字段。
     */
    public List<AiExternalOrg> selectEnabledOrgsForPortal(AiExternalOrg query);

    public int insertAiExternalOrg(AiExternalOrg org);

    public int updateAiExternalOrg(AiExternalOrg org);

    public int deleteAiExternalOrgByOrgIds(Long[] orgIds);
}
