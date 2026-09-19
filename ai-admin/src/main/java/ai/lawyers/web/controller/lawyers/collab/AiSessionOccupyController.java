package ai.lawyers.web.controller.lawyers.collab;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallerProfile;
import ai.lawyers.system.domain.lawyers.AiUnifiedSession;
import ai.lawyers.system.mapper.lawyers.AiCallerProfileMapper;
import ai.lawyers.system.service.lawyers.IAiUnifiedSessionService;

/**
 * 跨渠道会话占用 —— 坐席侧只读提示接口（方案B P1）。
 *
 * <p>弹屏/发起会话前查询公众当前各渠道活跃会话：
 * {@code current} 为当前渠道会话，{@code others} 为其他渠道活跃会话（跨渠道并发提示），
 * {@code mergeable} 为其他渠道中可建议并单的候选。P1 仅做只读提示，不做强制并单。</p>
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/session")
public class AiSessionOccupyController extends BaseController
{
    @Autowired
    private IAiUnifiedSessionService unifiedSessionService;

    @Autowired
    private AiCallerProfileMapper callerProfileMapper;

    /**
     * 查询某身份当前的活跃会话。
     *
     * @param profileId   公众档案ID（与 callerNumber 至少传一个）
     * @param callerNumber 手机号；缺 profileId 时先反查档案
     * @param channelType 当前业务渠道（可选），用于区分 current/others
     */
    @PreAuthorize("@ss.hasPermi('lawyers:call:popup:query')")
    @GetMapping("/active")
    public AjaxResult active(@RequestParam(value = "profileId", required = false) Long profileId,
                             @RequestParam(value = "callerNumber", required = false) String callerNumber,
                             @RequestParam(value = "channelType", required = false) String channelType)
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
        List<AiUnifiedSession> sessions = unifiedSessionService.listActive(resolvedProfileId, callerNumber);
        List<AiUnifiedSession> current = new ArrayList<>();
        List<AiUnifiedSession> others = new ArrayList<>();
        for (AiUnifiedSession session : sessions)
        {
            if (StringUtils.isNotEmpty(channelType) && channelType.equals(session.getChannelType()))
            {
                current.add(session);
            }
            else
            {
                others.add(session);
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("current", current);
        data.put("others", others);
        // P1：其他渠道活跃会话均作为可并单提示候选返回，前端按需提示；强制并单在后续迭代实现
        data.put("mergeable", others);
        return success(data);
    }
}
