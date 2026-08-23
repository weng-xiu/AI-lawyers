package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiCallBlacklist;

/**
 * 通话黑白名单 Mapper
 */
public interface AiCallBlacklistMapper
{
    public List<AiCallBlacklist> selectList(AiCallBlacklist query);

    public AiCallBlacklist selectById(Long id);

    /**
     * 查询某号码的全部名单记录（含黑/白名单，所有状态与时效）。
     */
    public List<AiCallBlacklist> selectByPhone(String phoneNumber);

    public int insert(AiCallBlacklist entity);

    public int update(AiCallBlacklist entity);

    public int deleteByIds(Long[] ids);
}
