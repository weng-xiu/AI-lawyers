package ai.lawyers.admin.controller.lawyers;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Anonymous;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiCallerProfile;
import ai.lawyers.system.domain.lawyers.AiChannelIdentity;
import ai.lawyers.system.domain.lawyers.AiExternalOrg;
import ai.lawyers.system.mapper.lawyers.AiCallerProfileMapper;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.IAiChannelIdentityService;
import ai.lawyers.system.service.lawyers.IAiExternalOrgService;

/**
 * 公众端门户 Controller（F8 + F2 + F6 二期绑定入口）
 *
 * <p><b>IDOR 安全收口：</b>除服务导航只读目录（@Anonymous，已剥离对接凭证）外，
 * 全部接口必须公众端 JWT 登录态，数据一律按当前登录用户手机号过滤，
 * 不接收任何工单/档案 ID 作为查询条件。</p>
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/portal")
public class AiUserPortalController extends BaseController
{
    @Autowired
    private IAiCallTicketService callTicketService;

    @Autowired
    private IAiExternalOrgService externalOrgService;

    @Autowired
    private AiCallerProfileMapper callerProfileMapper;

    @Autowired
    private IAiChannelIdentityService channelIdentityService;

    /**
     * 我的工单：强制按登录用户手机号过滤，忽略请求中任何外部条件。
     */
    @GetMapping("/ticket/list")
    public TableDataInfo myTickets(AiCallTicket query)
    {
        String phone = currentPhone();
        AiCallTicket filter = new AiCallTicket();
        filter.setCallerNumber(phone);
        // 仅允许状态过滤随请求透传，其余条件一律不接受
        if (StringUtils.isNotEmpty(query.getStatus()))
        {
            filter.setStatus(query.getStatus());
        }
        startPage();
        List<AiCallTicket> list = callTicketService.selectAiCallTicketList(filter);
        return getDataTable(list);
    }

    /**
     * 服务导航：按条线/属地查询启用机构（只读目录，匿名可访问，不含对接密钥）。
     */
    @Anonymous
    @GetMapping("/services/orgs")
    public AjaxResult serviceOrgs(AiExternalOrg query)
    {
        return success(externalOrgService.selectEnabledOrgsForPortal(query));
    }

    /**
     * F2 适老化偏好保存（语种/关怀模式），按本人手机号关联来电档案，无档案则新建。
     * Body：{ "languagePreference": "zh-CN|yue-CN", "careMode": 0|1 }
     */
    @Log(title = "公众端适老偏好", businessType = BusinessType.UPDATE)
    @PostMapping("/preference")
    public AjaxResult savePreference(@RequestBody Map<String, Object> body)
    {
        String phone = currentPhone();
        String language = body.get("languagePreference") == null
                ? null : String.valueOf(body.get("languagePreference"));
        Integer careMode = body.get("careMode") == null
                ? null : Integer.valueOf(String.valueOf(body.get("careMode")));
        if (StringUtils.isEmpty(language) && careMode == null)
        {
            throw new ServiceException("没有需要保存的偏好项");
        }
        AiCallerProfile profile = callerProfileMapper.selectAiCallerProfileByCallerNumber(phone);
        String username = SecurityUtils.getUsername();
        if (profile == null)
        {
            profile = new AiCallerProfile();
            profile.setCallerNumber(phone);
            profile.setLanguagePreference(StringUtils.isNotEmpty(language) ? language : "zh-CN");
            profile.setCareMode(careMode == null ? 0 : careMode);
            profile.setCreateBy(username);
            callerProfileMapper.insertAiCallerProfile(profile);
        }
        else
        {
            callerProfileMapper.updatePreference(profile.getProfileId(),
                    StringUtils.isNotEmpty(language) ? language : profile.getLanguagePreference(),
                    careMode == null ? (profile.getCareMode() == null ? 0 : profile.getCareMode()) : careMode);
        }
        return success("偏好已保存");
    }

    /**
     * 我的渠道绑定（二期 H5/微信；按本人手机号对应档案查询）。
     */
    @GetMapping("/channel/list")
    public AjaxResult myChannels()
    {
        AiCallerProfile profile = requireOwnProfile();
        return success(channelIdentityService.selectBoundByProfileId(profile.getProfileId()));
    }

    /**
     * 发起渠道绑定（二次确认走 /channel/confirm）。
     * Body：{ "channelType":"WECHAT_MINI", "channelUid":"openid-xxx", "channelNickname":"昵称" }
     */
    @Log(title = "公众端发起渠道绑定", businessType = BusinessType.INSERT)
    @PostMapping("/channel/requestBind")
    public AjaxResult requestBind(@RequestBody AiChannelIdentity body)
    {
        AiCallerProfile profile = requireOwnProfile();
        Long id = channelIdentityService.requestBind(profile.getProfileId(), body.getChannelType(),
                body.getChannelUid(), body.getChannelNickname(), SecurityUtils.getUsername());
        return AjaxResult.success("绑定申请已提交，待二次确认", id);
    }

    /**
     * 二次确认绑定（短信验证码/微信授权通过后调用）。
     */
    @Log(title = "公众端确认渠道绑定", businessType = BusinessType.UPDATE)
    @PostMapping("/channel/confirm")
    public AjaxResult confirmBind(@RequestBody AiChannelIdentity body)
    {
        if (body.getId() == null)
        {
            throw new ServiceException("绑定记录ID不能为空");
        }
        ensureOwnIdentity(body.getId());
        return toAjax(channelIdentityService.confirmBind(body.getId(), body.getChannelNickname()));
    }

    /**
     * 解绑本人渠道身份。
     */
    @Log(title = "公众端解除渠道绑定", businessType = BusinessType.UPDATE)
    @PostMapping("/channel/unbind")
    public AjaxResult unbind(@RequestBody AiChannelIdentity body)
    {
        if (body.getId() == null)
        {
            throw new ServiceException("绑定记录ID不能为空");
        }
        ensureOwnIdentity(body.getId());
        return toAjax(channelIdentityService.unbind(body.getId(), SecurityUtils.getUsername()));
    }

    /** 取当前登录公众用户手机号（公众账号必须绑定手机号） */
    private String currentPhone()
    {
        SysUser user = SecurityUtils.getLoginUser().getUser();
        if (user == null || StringUtils.isEmpty(user.getPhonenumber()))
        {
            throw new ServiceException("当前账号未绑定手机号，无法关联服务记录");
        }
        return user.getPhonenumber();
    }

    private AiCallerProfile requireOwnProfile()
    {
        AiCallerProfile profile = callerProfileMapper.selectAiCallerProfileByCallerNumber(currentPhone());
        if (profile == null)
        {
            throw new ServiceException("暂无来电档案");
        }
        return profile;
    }

    /** 对象级权限校验：绑定记录必须属于本人手机号对应档案 */
    private void ensureOwnIdentity(Long identityId)
    {
        AiCallerProfile profile = requireOwnProfile();
        AiChannelIdentity identity = channelIdentityService.selectIdentityById(identityId);
        if (identity == null || !profile.getProfileId().equals(identity.getProfileId()))
        {
            throw new ServiceException("绑定记录不存在或无权操作");
        }
    }
}
