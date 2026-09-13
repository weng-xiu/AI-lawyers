package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiChannelIdentity;

/**
 * 多渠道身份绑定 Service（F6）
 *
 * <p>绑定须二次确认（微信授权回调 / 短信验证码校验通过后调 {@link #confirmBind}）；
 * 同一渠道身份同一时间仅允许绑定一个档案，支持解绑留痕。</p>
 *
 * @author ai-lawyers
 */
public interface IAiChannelIdentityService
{
    public AiChannelIdentity selectIdentityById(Long id);

    public List<AiChannelIdentity> selectIdentityList(AiChannelIdentity query);

    /** 档案下的有效绑定 */
    public List<AiChannelIdentity> selectBoundByProfileId(Long profileId);

    /**
     * 发起绑定：登记渠道身份（bind_status=1 未生效），待二次确认。
     *
     * @return 绑定记录ID
     */
    public Long requestBind(Long profileId, String channelType, String channelUid,
                            String channelNickname, String operator);

    /**
     * 二次确认绑定（授权/验证码通过后调用）。
     */
    public int confirmBind(Long id, String channelNickname);

    /**
     * 解绑（保留留痕）。
     */
    public int unbind(Long id, String operator);
}
