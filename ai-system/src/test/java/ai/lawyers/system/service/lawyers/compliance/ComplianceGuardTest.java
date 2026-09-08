package ai.lawyers.system.service.lawyers.compliance;

import java.lang.reflect.Field;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ai.lawyers.system.domain.lawyers.AiCallBlacklist;
import ai.lawyers.system.mapper.lawyers.AiCallBlacklistMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W4 合规守卫测试：外呼时段限制 + 退订名单拦截。
 *
 * @author ai-lawyers
 */
class ComplianceGuardTest
{
    private ComplianceGuard guard;
    private AiCallBlacklistMapper blacklistMapper;

    @BeforeEach
    void setUp() throws Exception
    {
        guard = new ComplianceGuard();
        blacklistMapper = mock(AiCallBlacklistMapper.class);
        setField(guard, "blacklistMapper", blacklistMapper);
        setField(guard, "complianceEnabled", true);
        setField(guard, "allowedHours", "09:00-21:00");
        setField(guard, "timezone", "Asia/Shanghai");
    }

    private void setField(Object obj, String name, Object value) throws Exception
    {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    @Test
    void callingAllowed_withinHours_returnsTrue()
    {
        // 模拟当前时间在 09:00-21:00 内
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Shanghai"));
        boolean within = now.isAfter(LocalTime.of(9, 0)) && now.isBefore(LocalTime.of(21, 0));
        assertThat(guard.isCallingAllowed()).isEqualTo(within);
    }

    @Test
    void callingAllowed_disabled_returnsTrue() throws Exception
    {
        setField(guard, "complianceEnabled", false);
        assertThat(guard.isCallingAllowed()).isTrue();
    }

    @Test
    void callingAllowed_multipleSegments() throws Exception
    {
        setField(guard, "allowedHours", "00:00-23:59");
        assertThat(guard.isCallingAllowed()).isTrue();
    }

    @Test
    void isUnsubscribed_noRecord_returnsFalse()
    {
        when(blacklistMapper.selectByPhone("13800138000")).thenReturn(new ArrayList<>());
        assertThat(guard.isUnsubscribed("13800138000")).isFalse();
    }

    @Test
    void isUnsubscribed_withUnsubscribeRecord_returnsTrue()
    {
        AiCallBlacklist item = new AiCallBlacklist();
        item.setPhoneNumber("13800138000");
        item.setListType(3);
        item.setStatus(1);
        List<AiCallBlacklist> list = new ArrayList<>();
        list.add(item);
        when(blacklistMapper.selectByPhone("13800138000")).thenReturn(list);
        assertThat(guard.isUnsubscribed("13800138000")).isTrue();
    }

    @Test
    void isUnsubscribed_onlyBlacklistType_returnsFalse()
    {
        AiCallBlacklist item = new AiCallBlacklist();
        item.setPhoneNumber("13800138000");
        item.setListType(1); // 黑名单，非退订
        item.setStatus(1);
        List<AiCallBlacklist> list = new ArrayList<>();
        list.add(item);
        when(blacklistMapper.selectByPhone("13800138000")).thenReturn(list);
        assertThat(guard.isUnsubscribed("13800138000")).isFalse();
    }

    @Test
    void isUnsubscribed_disabledRecord_returnsFalse()
    {
        AiCallBlacklist item = new AiCallBlacklist();
        item.setPhoneNumber("13800138000");
        item.setListType(3);
        item.setStatus(0); // 停用
        List<AiCallBlacklist> list = new ArrayList<>();
        list.add(item);
        when(blacklistMapper.selectByPhone("13800138000")).thenReturn(list);
        assertThat(guard.isUnsubscribed("13800138000")).isFalse();
    }

    @Test
    void isUnsubscribed_expiredRecord_returnsFalse()
    {
        AiCallBlacklist item = new AiCallBlacklist();
        item.setPhoneNumber("13800138000");
        item.setListType(3);
        item.setStatus(1);
        item.setEffectiveEnd(new Date(System.currentTimeMillis() - 86400000L)); // 已过期
        List<AiCallBlacklist> list = new ArrayList<>();
        list.add(item);
        when(blacklistMapper.selectByPhone("13800138000")).thenReturn(list);
        assertThat(guard.isUnsubscribed("13800138000")).isFalse();
    }

    @Test
    void isSmsAllowed_unsubscribed_returnsFalse()
    {
        AiCallBlacklist item = new AiCallBlacklist();
        item.setListType(3);
        item.setStatus(1);
        List<AiCallBlacklist> list = new ArrayList<>();
        list.add(item);
        when(blacklistMapper.selectByPhone("13800138000")).thenReturn(list);
        assertThat(guard.isSmsAllowed("13800138000")).isFalse();
    }
}
