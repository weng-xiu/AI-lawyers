package ai.lawyers.system.service.impl.lawyers;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallBlacklist;
import ai.lawyers.system.mapper.lawyers.AiCallBlacklistMapper;
import ai.lawyers.system.service.lawyers.IAiCallBlacklistService;

/**
 * 通话黑白名单 Service 实现
 */
@Service
public class AiCallBlacklistServiceImpl implements IAiCallBlacklistService
{
    /** 名单类型：黑名单 */
    private static final int LIST_TYPE_BLACK = 1;
    /** 名单类型：白名单 */
    private static final int LIST_TYPE_WHITE = 2;
    /** 名单类型：退订名单（W4，"一处退订，呼叫+短信均禁止"） */
    private static final int LIST_TYPE_UNSUBSCRIBE = 3;
    /** 状态：启用 */
    private static final int STATUS_ENABLED = 1;

    @Autowired
    private AiCallBlacklistMapper blacklistMapper;

    @Override
    public List<AiCallBlacklist> selectList(AiCallBlacklist query)
    {
        return blacklistMapper.selectList(query);
    }

    @Override
    public AiCallBlacklist selectById(Long id)
    {
        return blacklistMapper.selectById(id);
    }

    @Override
    public int insert(AiCallBlacklist entity)
    {
        if (entity.getStatus() == null)
        {
            entity.setStatus(STATUS_ENABLED);
        }
        if (entity.getListType() == null)
        {
            entity.setListType(LIST_TYPE_BLACK);
        }
        entity.setCreateTime(DateUtils.getNowDate());
        return blacklistMapper.insert(entity);
    }

    @Override
    public int update(AiCallBlacklist entity)
    {
        entity.setUpdateTime(DateUtils.getNowDate());
        return blacklistMapper.update(entity);
    }

    @Override
    public int deleteByIds(Long[] ids)
    {
        return blacklistMapper.deleteByIds(ids);
    }

    @Override
    public boolean isBlacklisted(String phoneNumber)
    {
        return isInEffectiveList(phoneNumber, LIST_TYPE_BLACK);
    }

    @Override
    public boolean isWhitelisted(String phoneNumber)
    {
        return isInEffectiveList(phoneNumber, LIST_TYPE_WHITE);
    }

    private boolean isInEffectiveList(String phoneNumber, int listType)
    {
        if (StringUtils.isEmpty(phoneNumber))
        {
            return false;
        }
        List<AiCallBlacklist> records = blacklistMapper.selectByPhone(phoneNumber);
        if (records == null || records.isEmpty())
        {
            return false;
        }
        Date now = DateUtils.getNowDate();
        for (AiCallBlacklist r : records)
        {
            if (r == null) continue;
            if (r.getListType() == null || r.getListType() != listType) continue;
            if (r.getStatus() == null || r.getStatus() != STATUS_ENABLED) continue;
            if (r.getEffectiveStart() != null && now.before(r.getEffectiveStart())) continue;
            if (r.getEffectiveEnd() != null && now.after(r.getEffectiveEnd())) continue;
            return true;
        }
        return false;
    }

    @Override
    public boolean addUnsubscribe(String phoneNumber, String reason)
    {
        if (StringUtils.isEmpty(phoneNumber))
        {
            return false;
        }
        AiCallBlacklist exist = findUnsubscribeRecord(phoneNumber);
        if (exist != null)
        {
            // 幂等恢复：置启用、清空生效区间，不产生重复记录
            AiCallBlacklist upd = new AiCallBlacklist();
            upd.setId(exist.getId());
            upd.setReason(StringUtils.isEmpty(reason) ? "短信回复T退订" : reason);
            upd.setUpdateBy("SMS_UPSTREAM");
            return blacklistMapper.updateUnsubscribeActive(upd) > 0;
        }
        AiCallBlacklist entity = new AiCallBlacklist();
        entity.setPhoneNumber(phoneNumber);
        entity.setListType(LIST_TYPE_UNSUBSCRIBE);
        entity.setStatus(STATUS_ENABLED);
        entity.setReason(StringUtils.isEmpty(reason) ? "短信回复T退订" : reason);
        entity.setCreateBy("SMS_UPSTREAM");
        return blacklistMapper.insert(entity) > 0;
    }

    /** 查询该号码已有的退订名单记录（含停用/过期），用于幂等恢复 */
    private AiCallBlacklist findUnsubscribeRecord(String phoneNumber)
    {
        List<AiCallBlacklist> records = blacklistMapper.selectByPhone(phoneNumber);
        if (records == null || records.isEmpty())
        {
            return null;
        }
        for (AiCallBlacklist r : records)
        {
            if (r != null && r.getListType() != null && r.getListType() == LIST_TYPE_UNSUBSCRIBE)
            {
                return r;
            }
        }
        return null;
    }
}
