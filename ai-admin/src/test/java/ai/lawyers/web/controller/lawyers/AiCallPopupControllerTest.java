package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiCallerProfile;
import ai.lawyers.system.service.lawyers.ICallPopupService;

/**
 * 来电弹屏接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiCallPopupControllerTest
{
    @Mock
    private ICallPopupService callPopupService;

    @InjectMocks
    private AiCallPopupController controller;

    @Test
    void 档案返回来电人信息与智能分析()
    {
        Map<String, Object> profile = new HashMap<String, Object>();
        profile.put("callerNumber", "13800000000");
        profile.put("level", "VIP");
        when(callPopupService.getPopupProfile("13800000000")).thenReturn(profile);

        AjaxResult result = controller.profile("13800000000");

        assertSame(profile, result.get(AjaxResult.DATA_TAG));
        verify(callPopupService).getPopupProfile("13800000000");
    }

    @Test
    void 历史通话携带条数限制()
    {
        List<Map<String, Object>> history = new ArrayList<Map<String, Object>>();
        history.add(Collections.singletonMap("callId", 11L));
        doReturn(history).when(callPopupService).getPopupHistory("13800000000", 20);

        AjaxResult result = controller.history("13800000000", 20);

        assertSame(history, result.get(AjaxResult.DATA_TAG));
        verify(callPopupService).getPopupHistory("13800000000", 20);
    }

    @Test
    void 历史工单与来电轨迹透传()
    {
        List<Map<String, Object>> tickets = new ArrayList<Map<String, Object>>();
        tickets.add(Collections.singletonMap("ticketId", 5L));
        List<Map<String, Object>> track = new ArrayList<Map<String, Object>>();
        track.add(Collections.singletonMap("node", "呼入"));
        doReturn(tickets).when(callPopupService).getPopupTickets("13800000000");
        doReturn(track).when(callPopupService).getPopupTrack("13800000000");

        assertSame(tickets, controller.tickets("13800000000").get(AjaxResult.DATA_TAG));
        assertSame(track, controller.track("13800000000").get(AjaxResult.DATA_TAG));
    }

    @Test
    void 更新档案写入更新人()
    {
        SysUser user = new SysUser();
        user.setUserName("agent01");
        AiCallerProfile profile = new AiCallerProfile();
        when(callPopupService.updateAiCallerProfile(any(AiCallerProfile.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(new LoginUser(7L, 8L, user, null));
            AjaxResult result = controller.editProfile(profile);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("agent01", profile.getUpdateBy());
        verify(callPopupService).updateAiCallerProfile(profile);
    }
}
