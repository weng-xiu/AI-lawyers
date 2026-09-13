package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiExternalOrg;

/**
 * 协同外部机构台账 Mapper（F3）
 *
 * @author ai-lawyers
 */
public interface AiExternalOrgMapper
{
    public AiExternalOrg selectAiExternalOrgByOrgId(Long orgId);

    public List<AiExternalOrg> selectAiExternalOrgList(AiExternalOrg aiExternalOrg);

    public int insertAiExternalOrg(AiExternalOrg aiExternalOrg);

    public int updateAiExternalOrg(AiExternalOrg aiExternalOrg);

    public int deleteAiExternalOrgByOrgId(Long orgId);

    public int deleteAiExternalOrgByOrgIds(Long[] orgIds);
}
