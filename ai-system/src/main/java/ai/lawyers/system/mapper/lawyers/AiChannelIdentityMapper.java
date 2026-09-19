package ai.lawyers.system.mapper.lawyers;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.AiChannelIdentity;

/**
 * 多渠道身份绑定 Mapper（F6）
 *
 * @author ai-lawyers
 */
public interface AiChannelIdentityMapper
{
    public AiChannelIdentity selectAiChannelIdentityById(Long id);

    /** 按渠道+渠道标识查询（唯一键） */
    public AiChannelIdentity selectByChannelUid(@Param("channelType") String channelType,
                                                @Param("channelUid") String channelUid);

    public List<AiChannelIdentity> selectAiChannelIdentityList(AiChannelIdentity query);

    /** 档案下的有效绑定 */
    public List<AiChannelIdentity> selectBoundByProfileId(Long profileId);

    public int insertAiChannelIdentity(AiChannelIdentity aiChannelIdentity);

    /** 二次确认绑定 */
    public int confirmBind(@Param("id") Long id, @Param("nickname") String nickname,
                           @Param("confirmTime") Date confirmTime);

    /** 解绑（保留留痕，状态置为已解绑） */
    public int unbind(@Param("id") Long id, @Param("unbindTime") Date unbindTime,
                      @Param("updateBy") String updateBy);

    /** 重复发起绑定时刷新渠道昵称 */
    public int refreshNickname(@Param("id") Long id, @Param("nickname") String nickname);
}
