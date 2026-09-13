package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiExternalOrg;
import ai.lawyers.system.mapper.lawyers.AiExternalOrgMapper;
import ai.lawyers.system.service.lawyers.IAiExternalOrgService;

/**
 * 协同外部机构台账 Service 实现（F3）
 *
 * @author ai-lawyers
 */
@Service
public class AiExternalOrgServiceImpl implements IAiExternalOrgService
{
    @Autowired
    private AiExternalOrgMapper aiExternalOrgMapper;

    @Override
    public AiExternalOrg selectAiExternalOrgByOrgId(Long orgId)
    {
        return aiExternalOrgMapper.selectAiExternalOrgByOrgId(orgId);
    }

    @Override
    public List<AiExternalOrg> selectAiExternalOrgList(AiExternalOrg query)
    {
        return aiExternalOrgMapper.selectAiExternalOrgList(query);
    }

    @Override
    public List<AiExternalOrg> selectEnabledOrgsForPortal(AiExternalOrg query)
    {
        if (query == null)
        {
            query = new AiExternalOrg();
        }
        // 公众端只读目录强制启用态
        query.setStatus("0");
        List<AiExternalOrg> list = aiExternalOrgMapper.selectAiExternalOrgList(query);
        for (AiExternalOrg org : list)
        {
            // PII/安全收口：导航场景剥离对接凭证与内部接口地址
            org.setAppSecret(null);
            org.setAppId(null);
            if (!"MANUAL".equals(org.getAccessMode()))
            {
                org.setApiUrl(null);
            }
        }
        return list;
    }

    @Override
    public int insertAiExternalOrg(AiExternalOrg org)
    {
        validateOrg(org);
        if (StringUtils.isEmpty(org.getStatus()))
        {
            org.setStatus("0");
        }
        if (StringUtils.isEmpty(org.getAccessMode()))
        {
            org.setAccessMode("MANUAL");
        }
        return aiExternalOrgMapper.insertAiExternalOrg(org);
    }

    @Override
    public int updateAiExternalOrg(AiExternalOrg org)
    {
        if (org.getOrgId() == null)
        {
            throw new ServiceException("机构ID不能为空");
        }
        validateOrg(org);
        return aiExternalOrgMapper.updateAiExternalOrg(org);
    }

    @Override
    public int deleteAiExternalOrgByOrgIds(Long[] orgIds)
    {
        return aiExternalOrgMapper.deleteAiExternalOrgByOrgIds(orgIds);
    }

    private void validateOrg(AiExternalOrg org)
    {
        if (org == null)
        {
            throw new ServiceException("机构信息不能为空");
        }
        if (StringUtils.isEmpty(org.getOrgName()))
        {
            throw new ServiceException("机构名称不能为空");
        }
        if (StringUtils.isEmpty(org.getExternalType()))
        {
            throw new ServiceException("条线类型不能为空");
        }
        if ("API".equals(org.getAccessMode()) && StringUtils.isEmpty(org.getApiUrl()))
        {
            throw new ServiceException("API 对接方式必须填写协同接口地址");
        }
    }
}
