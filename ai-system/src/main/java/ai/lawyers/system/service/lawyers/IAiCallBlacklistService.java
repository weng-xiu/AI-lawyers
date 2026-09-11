package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiCallBlacklist;

/**
 * 通话黑白名单 Service
 */
public interface IAiCallBlacklistService
{
    List<AiCallBlacklist> selectList(AiCallBlacklist query);

    AiCallBlacklist selectById(Long id);

    int insert(AiCallBlacklist entity);

    int update(AiCallBlacklist entity);

    int deleteByIds(Long[] ids);

    /**
     * 判断号码是否在生效中的黑名单内（status=1, list_type=1，当前时间在生效区间内，时间为空则不限）。
     */
    boolean isBlacklisted(String phoneNumber);

    /**
     * 判断号码是否在生效中的白名单内（status=1, list_type=2，当前时间在生效区间内，时间为空则不限）。
     */
    boolean isWhitelisted(String phoneNumber);

    /**
     * W4：写入退订名单（list_type=3），实现"一处退订，呼叫+短信均禁止"。
     * 幂等：同号码已有退订记录时置为启用并清空生效区间；无则新增。
     *
     * @param phoneNumber 被叫号码
     * @param reason      退订原因（来源，如"短信回复T退订"）
     * @return true 写入/恢复成功
     */
    boolean addUnsubscribe(String phoneNumber, String reason);
}
