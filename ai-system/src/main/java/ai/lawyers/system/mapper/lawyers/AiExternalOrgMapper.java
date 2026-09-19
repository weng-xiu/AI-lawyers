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

    /**
     * F3 风险联动转办：查询启用状态的可转办机构（精简公开字段，不含 appId/appSecret/apiUrl）。
     * @param externalType 条线类型，可空
     */
    public List<AiExternalOrg> selectTransferOptions(@org.apache.ibatis.annotations.Param("externalType") String externalType);

    public int insertAiExternalOrg(AiExternalOrg aiExternalOrg);

    public int updateAiExternalOrg(AiExternalOrg aiExternalOrg);

    public int deleteAiExternalOrgByOrgId(Long orgId);

    public int deleteAiExternalOrgByOrgIds(Long[] orgIds);
}
