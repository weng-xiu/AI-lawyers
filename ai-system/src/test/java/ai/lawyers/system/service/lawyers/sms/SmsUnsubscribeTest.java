package ai.lawyers.system.service.lawyers.sms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ai.lawyers.system.domain.lawyers.AiCallBlacklist;
import ai.lawyers.system.mapper.lawyers.AiCallBlacklistMapper;
import ai.lawyers.system.service.impl.lawyers.AiCallBlacklistServiceImpl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W4 退订名单写入入口测试：{@link AiCallBlacklistServiceImpl#addUnsubscribe} 幂等逻辑 +
 * 短信上行退订指令解析语义。
 *
 * <p>覆盖三条验收线：</p>
 * <ul>
 *   <li>新增：无退订记录时 insert（list_type=3, status=1, 来源 SMS_UPSTREAM）</li>
 *   <li>幂等恢复：已有退订记录时 updateUnsubscribeActive（置启用、清生效区间），不产生重复记录</li>
 *   <li>上行解析：T / TD / 退订 / T退订 / TD退订 / 停止推送 / STOP（忽略大小写与首尾空白）识别为退订指令，
 *       其他内容（如关键字查询）不触发退订</li>
 * </ul>
 *
 * @author ai-lawyers
 */
class SmsUnsubscribeTest
{
    /** 与 SmsUpstreamController.UNSUBSCRIBE_REGEX 保持一致的解析语义（复刻） */
    private static final String UNSUBSCRIBE_REGEX = "(?i)^\\s*(T|TD|TD退订|T退订|退订|停止推送|拒收|STOP)\\s*$";

    private AiCallBlacklistMapper mapper;
    private AiCallBlacklistServiceImpl service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(AiCallBlacklistMapper.class);
        service = new AiCallBlacklistServiceImpl();
        setMapper(service, mapper);
    }

    private void setMapper(AiCallBlacklistServiceImpl svc, AiCallBlacklistMapper m)
    {
        try
        {
            java.lang.reflect.Field f = AiCallBlacklistServiceImpl.class.getDeclaredField("blacklistMapper");
            f.setAccessible(true);
            f.set(svc, m);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("注入 blacklistMapper 失败", e);
        }
    }

    // ---------- 新增分支 ----------

    @Test
    void addUnsubscribe_noExistingRecord_insertsEnabledRecord()
    {
        when(mapper.selectByPhone("13800138000")).thenReturn(new ArrayList<>());
        when(mapper.insert(org.mockito.ArgumentMatchers.any(AiCallBlacklist.class))).thenReturn(1);

        boolean ok = service.addUnsubscribe("13800138000", null);

        assertThat(ok).isTrue();
        org.mockito.ArgumentCaptor<AiCallBlacklist> cap = org.mockito.ArgumentCaptor.forClass(AiCallBlacklist.class);
        verify(mapper).insert(cap.capture());
        AiCallBlacklist entity = cap.getValue();
        assertThat(entity.getPhoneNumber()).isEqualTo("13800138000");
        assertThat(entity.getListType()).isEqualTo(3);
        assertThat(entity.getStatus()).isEqualTo(1);
        assertThat(entity.getCreateBy()).isEqualTo("SMS_UPSTREAM");
        assertThat(entity.getReason()).isEqualTo("短信回复T退订"); // 未传 reason 使用默认
        verify(mapper, never()).updateUnsubscribeActive(org.mockito.ArgumentMatchers.any(AiCallBlacklist.class));
    }

    @Test
    void addUnsubscribe_customReason_keepsProvidedReason()
    {
        when(mapper.selectByPhone("13900139000")).thenReturn(new ArrayList<>());
        when(mapper.insert(org.mockito.ArgumentMatchers.any(AiCallBlacklist.class))).thenReturn(1);

        service.addUnsubscribe("13900139000", "用户来电要求退订");

        org.mockito.ArgumentCaptor<AiCallBlacklist> cap = org.mockito.ArgumentCaptor.forClass(AiCallBlacklist.class);
        verify(mapper).insert(cap.capture());
        assertThat(cap.getValue().getReason()).isEqualTo("用户来电要求退订");
    }

    // ---------- 幂等恢复分支 ----------

    @Test
    void addUnsubscribe_existingDisabledRecord_reactivatesWithoutDuplicate()
    {
        AiCallBlacklist existing = new AiCallBlacklist();
        existing.setId(7L);
        existing.setPhoneNumber("13700137000");
        existing.setListType(3);
        existing.setStatus(0); // 已被停用
        when(mapper.selectByPhone("13700137000")).thenReturn(Collections.singletonList(existing));
        when(mapper.updateUnsubscribeActive(org.mockito.ArgumentMatchers.any(AiCallBlacklist.class))).thenReturn(1);

        boolean ok = service.addUnsubscribe("13700137000", null);

        assertThat(ok).isTrue();
        // 关键：不得再 insert，避免同号码重复退订记录
        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any(AiCallBlacklist.class));
        org.mockito.ArgumentCaptor<AiCallBlacklist> cap = org.mockito.ArgumentCaptor.forClass(AiCallBlacklist.class);
        verify(mapper).updateUnsubscribeActive(cap.capture());
        AiCallBlacklist upd = cap.getValue();
        assertThat(upd.getId()).isEqualTo(7L);
        assertThat(upd.getUpdateBy()).isEqualTo("SMS_UPSTREAM");
    }

    @Test
    void addUnsubscribe_emptyPhone_returnsFalseAndNoDbCall()
    {
        assertThat(service.addUnsubscribe("  ", null)).isFalse();
        assertThat(service.addUnsubscribe(null, null)).isFalse();
        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any(AiCallBlacklist.class));
        verify(mapper, never()).updateUnsubscribeActive(org.mockito.ArgumentMatchers.any(AiCallBlacklist.class));
    }

    // ---------- 上行退订指令解析语义（与控制器正则保持一致） ----------

    @Test
    void upstreamContent_unsubscribeKeywords_matches()
    {
        // 常用退订指令：大小写、首尾空白均需识别
        String[] keywords = { "T", "TD", "t", "td", "退订", "T退订", "TD退订", "t退订", "td退订",
                "停止推送", "拒收", "STOP", "stop", "  T  ", "\t退订\n" };
        for (String kw : keywords)
        {
            assertThat(kw.trim().matches(UNSUBSCRIBE_REGEX))
                    .as("退订指令应命中: [%s]", kw)
                    .isTrue();
        }
    }

    @Test
    void upstreamContent_nonUnsubscribeContent_notMatched()
    {
        // 非退订上行（如关键字查询、乱码）不得触发退订
        String[] contents = { "查话费", "help", "如何退订", "T退订吗", "不是退订", "TXT", "TDZ", "123" };
        for (String c : contents)
        {
            assertThat(c.trim().matches(UNSUBSCRIBE_REGEX))
                    .as("非退订内容不应命中: [%s]", c)
                    .isFalse();
        }
    }

    @Test
    void upstreamContent_embeddedKeyword_notTriggered()
    {
        // 内容中带 T/TD 关键字但不构成退订指令的（如回复"T+文字"），不触发
        String[] contents = { "TD退订请回复", "T，你好", "STOPPING" };
        for (String c : contents)
        {
            assertThat(c.trim().matches(UNSUBSCRIBE_REGEX))
                    .as("混合内容不应命中: [%s]", c)
                    .isFalse();
        }
    }
}
