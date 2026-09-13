package ai.lawyers.web.controller.lawyers.collab;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallerProfile;
import ai.lawyers.system.domain.lawyers.AiChannelIdentity;
import ai.lawyers.system.domain.lawyers.AiUnifiedSession;
import ai.lawyers.system.mapper.lawyers.AiCallerProfileMapper;
import ai.lawyers.system.service.lawyers.IAiChannelIdentityService;
import ai.lawyers.system.service.lawyers.IAiUnifiedSessionService;

/**
 * 多渠道身份绑定 + 跨渠道时间线 Controller（F6，坐席端）
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/channel")
public class AiChannelIdentityController extends BaseController
{
    @Autowired
    private IAiChannelIdentityService channelIdentityService;

    @Autowired
    private IAiUnifiedSessionService unifiedSessionService;

    @Autowired
    private AiCallerProfileMapper callerProfileMapper;

    /**
     * 渠道身份列表（管理查询）。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:channelIdentity:list')")
    @GetMapping("/identity/list")
    public TableDataInfo list(AiChannelIdentity query)
    {
        startPage();
        List<AiChannelIdentity> list = channelIdentityService.selectIdentityList(query);
        return getDataTable(list);
    }

    /**
     * 发起渠道绑定（二期 H5/微信侧主要入口，坐席端可代客发起；二次确认调 /confirm/{id}）。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:channelIdentity:unbind')")
    @Log(title = "发起渠道绑定", businessType = BusinessType.INSERT)
    @PostMapping("/identity/requestBind")
    public AjaxResult requestBind(@RequestBody AiChannelIdentity body)
    {
        Long id = channelIdentityService.requestBind(body.getProfileId(), body.getChannelType(),
                body.getChannelUid(), body.getChannelNickname(), getUsername());
        return AjaxResult.success(id);
    }

    /**
     * 二次确认绑定。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:channelIdentity:unbind')")
    @Log(title = "确认渠道绑定", businessType = BusinessType.UPDATE)
    @PostMapping("/identity/confirm/{id}")
    public AjaxResult confirm(@PathVariable("id") Long id, @RequestBody(required = false) AiChannelIdentity body)
    {
        String nickname = body == null ? null : body.getChannelNickname();
        return toAjax(channelIdentityService.confirmBind(id, nickname));
    }

    /**
     * 解绑（手机号过户/多人共用撤销合并）。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:channelIdentity:unbind')")
    @Log(title = "解除渠道绑定", businessType = BusinessType.UPDATE)
    @PostMapping("/identity/unbind/{id}")
    public AjaxResult unbind(@PathVariable("id") Long id)
    {
        return toAjax(channelIdentityService.unbind(id, getUsername()));
    }

    /**
     * F6 跨渠道咨询时间线：按档案ID聚合；只给手机号时先解析档案，再合并手机号兜底会话。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:call:popup:query')")
    @GetMapping("/timeline")
    public AjaxResult timeline(@RequestParam(value = "profileId", required = false) Long profileId,
                               @RequestParam(value = "callerNumber", required = false) String callerNumber)
    {
        if (profileId == null && StringUtils.isEmpty(callerNumber))
        {
            return AjaxResult.error("profileId 与 callerNumber 至少传一个");
        }
        Long resolvedProfileId = profileId;
        if (resolvedProfileId == null && StringUtils.isNotEmpty(callerNumber))
        {
            AiCallerProfile profile = callerProfileMapper.selectAiCallerProfileByCallerNumber(callerNumber);
            if (profile != null)
            {
                resolvedProfileId = profile.getProfileId();
            }
        }
        List<AiUnifiedSession> sessions = unifiedSessionService.timeline(resolvedProfileId, callerNumber);
        return success(sessions);
    }

    /**
     * 档案下的有效渠道绑定（弹屏展示用）。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:channelIdentity:query')")
    @GetMapping("/identity/bound/{profileId}")
    public AjaxResult bound(@PathVariable("profileId") Long profileId)
    {
        return success(channelIdentityService.selectBoundByProfileId(profileId));
    }
}
