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
}
