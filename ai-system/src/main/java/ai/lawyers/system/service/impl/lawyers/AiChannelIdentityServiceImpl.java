package ai.lawyers.system.service.impl.lawyers;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiChannelIdentity;
import ai.lawyers.system.mapper.lawyers.AiChannelIdentityMapper;
import ai.lawyers.system.service.lawyers.IAiChannelIdentityService;

/**
 * 多渠道身份绑定 Service 实现（F6）
 *
 * @author ai-lawyers
 */
@Service
public class AiChannelIdentityServiceImpl implements IAiChannelIdentityService
{
    @Autowired
    private AiChannelIdentityMapper channelIdentityMapper;

    @Override
    public AiChannelIdentity selectIdentityById(Long id)
    {
        return channelIdentityMapper.selectAiChannelIdentityById(id);
    }

    @Override
    public List<AiChannelIdentity> selectIdentityList(AiChannelIdentity query)
    {
        return channelIdentityMapper.selectAiChannelIdentityList(query);
    }

    @Override
    public List<AiChannelIdentity> selectBoundByProfileId(Long profileId)
    {
        return channelIdentityMapper.selectBoundByProfileId(profileId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long requestBind(Long profileId, String channelType, String channelUid,
                            String channelNickname, String operator)
    {
        if (profileId == null)
        {
            throw new ServiceException("来电档案ID不能为空");
        }
        if (StringUtils.isEmpty(channelType) || StringUtils.isEmpty(channelUid))
        {
            throw new ServiceException("渠道类型与渠道标识不能为空");
        }
        // 同一渠道身份已绑定其他档案：必须先解绑（手机号过户/多人共用收口）
        AiChannelIdentity existing = channelIdentityMapper.selectByChannelUid(channelType, channelUid);
        if (existing != null)
        {
            if ("0".equals(existing.getBindStatus()) && !profileId.equals(existing.getProfileId()))
            {
                throw new ServiceException("该渠道身份已绑定其他档案，请先解绑后再绑定");
            }
            if (profileId.equals(existing.getProfileId()))
            {
                // 同档案重复登记：已绑定直接返回，未绑定则重新发起确认；携带新昵称时同步刷新
                if (StringUtils.isNotEmpty(channelNickname)
                        && !channelNickname.equals(existing.getChannelNickname()))
                {
                    channelIdentityMapper.refreshNickname(existing.getId(), channelNickname);
                }
                return existing.getId();
            }
            // 已解绑状态 + 不同档案：转移归属（复用记录，避免唯一约束冲突）
            if ("1".equals(existing.getBindStatus()))
            {
                channelIdentityMapper.transferOwnership(existing.getId(), profileId,
                        channelNickname, operator);
                return existing.getId();
            }
        }
        AiChannelIdentity identity = new AiChannelIdentity();
        identity.setProfileId(profileId);
        identity.setChannelType(channelType);
        identity.setChannelUid(channelUid);
        identity.setChannelNickname(channelNickname);
        // 1=未生效（待二次确认），确认后置 0
        identity.setBindStatus("1");
        identity.setCreateBy(operator);
        channelIdentityMapper.insertAiChannelIdentity(identity);
        return identity.getId();
    }

    @Override
    public int confirmBind(Long id, String channelNickname)
    {
        AiChannelIdentity identity = channelIdentityMapper.selectAiChannelIdentityById(id);
        if (identity == null)
        {
            throw new ServiceException("绑定记录不存在");
        }
        return channelIdentityMapper.confirmBind(id, channelNickname, new Date());
    }

    @Override
    public int unbind(Long id, String operator)
    {
        AiChannelIdentity identity = channelIdentityMapper.selectAiChannelIdentityById(id);
        if (identity == null)
        {
            throw new ServiceException("绑定记录不存在");
        }
        return channelIdentityMapper.unbind(id, new Date(), operator);
    }
}
