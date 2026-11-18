package ai.lawyers.web.controller.lawyers.collab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.utils.sign.PiiCryptoUtils;
import ai.lawyers.system.domain.lawyers.AiCallerProfile;
import ai.lawyers.system.domain.lawyers.AiUnifiedSession;
import ai.lawyers.system.mapper.lawyers.AiCallerProfileMapper;
import ai.lawyers.system.service.lawyers.IAiUnifiedSessionService;

/**
 * 跨渠道会话占用查询接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiSessionOccupyControllerTest
{
    @Mock
    private IAiUnifiedSessionService unifiedSessionService;

    @Mock
    private AiCallerProfileMapper callerProfileMapper;

    @InjectMocks
    private AiSessionOccupyController controller;

    private AiUnifiedSession session(String channelType)
    {
        AiUnifiedSession session = new AiUnifiedSession();
        session.setChannelType(channelType);
        return session;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(AjaxResult result)
    {
        return (Map<String, Object>) result.get(AjaxResult.DATA_TAG);
    }

    @Test
    void 身份参数缺失返回错误提示()
    {
        AjaxResult result = controller.active(null, null, null);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("profileId 与 callerNumber 至少传一个", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void 按手机号反查档案并区分当前渠道()
    {
        AiCallerProfile profile = new AiCallerProfile();
        profile.setProfileId(9L);
        List<AiUnifiedSession> sessions = new ArrayList<AiUnifiedSession>();
        sessions.add(session("phone"));
        sessions.add(session("chat"));
        try (MockedStatic<PiiCryptoUtils> mocked = mockStatic(PiiCryptoUtils.class))
        {
            mocked.when(() -> PiiCryptoUtils.blindIndex("13800000000")).thenReturn("idx-1");
            when(callerProfileMapper.selectAiCallerProfileByCallerNumberIndex("idx-1")).thenReturn(profile);
            when(unifiedSessionService.listActive(eq(9L), eq("13800000000"))).thenReturn(sessions);

            AjaxResult result = controller.active(null, "13800000000", "phone");

            Map<String, Object> data = dataOf(result);
            assertEquals(1, ((List<?>) data.get("current")).size());
            assertEquals(1, ((List<?>) data.get("others")).size());
            assertEquals(data.get("others"), data.get("mergeable"));
        }
    }

    @Test
    void 手机号未匹配到档案时按原号码查询()
    {
        try (MockedStatic<PiiCryptoUtils> mocked = mockStatic(PiiCryptoUtils.class))
        {
            mocked.when(() -> PiiCryptoUtils.blindIndex("13900000000")).thenReturn("idx-2");
            when(callerProfileMapper.selectAiCallerProfileByCallerNumberIndex("idx-2")).thenReturn(null);
            when(unifiedSessionService.listActive(eq(null), eq("13900000000"))).thenReturn(new ArrayList<AiUnifiedSession>());

            AjaxResult result = controller.active(null, "13900000000", null);

            assertTrue(((List<?>) dataOf(result).get("others")).isEmpty());
        }
    }

    @Test
    void 仅传档案ID时不再反查号码索引()
    {
        List<AiUnifiedSession> sessions = new ArrayList<AiUnifiedSession>();
        sessions.add(session("chat"));
        when(unifiedSessionService.listActive(eq(5L), eq(null))).thenReturn(sessions);

        AjaxResult result = controller.active(5L, null, null);

        assertEquals(1, ((List<?>) dataOf(result).get("others")).size());
        assertTrue(((List<?>) dataOf(result).get("current")).isEmpty());
        verify(callerProfileMapper, never()).selectAiCallerProfileByCallerNumberIndex(null);
    }
}
