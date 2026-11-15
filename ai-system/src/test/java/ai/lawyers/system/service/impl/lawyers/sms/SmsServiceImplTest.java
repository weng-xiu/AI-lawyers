package ai.lawyers.system.service.impl.lawyers.sms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.system.domain.lawyers.sms.AiSmsConfig;
import ai.lawyers.system.domain.lawyers.sms.AiSmsLog;
import ai.lawyers.system.domain.lawyers.sms.AiSmsTemplate;
import ai.lawyers.system.domain.lawyers.sms.SmsRequest;
import ai.lawyers.system.domain.lawyers.sms.SmsResult;
import ai.lawyers.system.mapper.lawyers.sms.AiSmsConfigMapper;
import ai.lawyers.system.mapper.lawyers.sms.AiSmsLogMapper;
import ai.lawyers.system.mapper.lawyers.sms.AiSmsTemplateMapper;
import ai.lawyers.system.service.lawyers.compliance.ComplianceGuard;
import ai.lawyers.system.service.lawyers.sms.ISmsProvider;

/**
 * 短信发送服务单测。
 *
 * <p>覆盖模板/通道校验、日限流原子计数与降级兜底、退订拦截、模板变量渲染、
 * 供应商解析失败、发送成功/空结果/异常留痕回写与日志落库失败不阻断发送。</p>
 *
 * @author ai-lawyers
 */
class SmsServiceImplTest
{
    private SmsServiceImpl service;

    @Mock private AiSmsTemplateMapper templateMapper;
    @Mock private AiSmsConfigMapper configMapper;
    @Mock private AiSmsLogMapper logMapper;
    @Mock private RedisCache redisCache;
    @Mock private RedisTemplate redisTemplate;
    @Mock private ValueOperations valueOperations;
    @Mock private ComplianceGuard complianceGuard;
    @Mock private ISmsProvider mockSmsProvider;

    private Map<String, ISmsProvider> smsProviders;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new SmsServiceImpl();
        smsProviders = new HashMap<>();
        ReflectionTestUtils.setField(service, "templateMapper", templateMapper);
        ReflectionTestUtils.setField(service, "configMapper", configMapper);
        ReflectionTestUtils.setField(service, "logMapper", logMapper);
        ReflectionTestUtils.setField(service, "redisCache", redisCache);
        ReflectionTestUtils.setField(service, "smsProviders", smsProviders);
        ReflectionTestUtils.setField(service, "complianceGuard", complianceGuard);
        ReflectionTestUtils.setField(redisCache, "redisTemplate", redisTemplate);
        // 默认允许发送，退订用例中再覆盖为 false
        when(complianceGuard.isSmsAllowed(anyString())).thenReturn(true);
        // 默认落库成功并回填 logId，便于验证结果回写
        when(logMapper.insertAiSmsLog(any(AiSmsLog.class))).thenAnswer(invocation -> {
            AiSmsLog saved = invocation.getArgument(0);
            saved.setLogId(100L);
            return 1;
        });
    }

    private static AiSmsTemplate template(Long configId, String content)
    {
        AiSmsTemplate template = new AiSmsTemplate();
        template.setConfigId(configId);
        template.setContent(content);
        template.setProviderTemplateCode("TPL");
        return template;
    }

    private static AiSmsConfig config(Integer dailyLimit, String status, String provider)
    {
        AiSmsConfig config = new AiSmsConfig();
        config.setDailyLimit(dailyLimit);
        config.setStatus(status);
        config.setProvider(provider);
        config.setSignName("签名");
        return config;
    }

    private AiSmsLog lastInsertedLog()
    {
        ArgumentCaptor<AiSmsLog> captor = ArgumentCaptor.forClass(AiSmsLog.class);
        verify(logMapper).insertAiSmsLog(captor.capture());
        return captor.getValue();
    }

    @Test
    void send_templateMissing_failsAndLogs()
    {
        when(templateMapper.selectAiSmsTemplateByTemplateId(1L)).thenReturn(null);

        SmsResult result = service.send("13800138000", 1L, null, "s1", 2L);

        assertFalse(result.isSuccess());
        assertEquals("短信模板不存在", result.getMessage());
        AiSmsLog log = lastInsertedLog();
        assertEquals("2", log.getSendStatus());
        assertEquals("短信模板不存在", log.getFailReason());
        assertEquals("s1", log.getSessionId());
        assertEquals(2L, log.getRecordId());
    }

    @Test
    void send_configMissing_fails()
    {
        when(templateMapper.selectAiSmsTemplateByTemplateId(1L)).thenReturn(template(5L, "内容"));
        when(configMapper.selectAiSmsConfigByConfigId(5L)).thenReturn(null);

        SmsResult result = service.send("13800138000", 1L, null, null, null);

        assertFalse(result.isSuccess());
        assertEquals("短信通道不存在或已停用", result.getMessage());
    }

    @Test
    void send_configDisabled_fails()
    {
        when(templateMapper.selectAiSmsTemplateByTemplateId(1L)).thenReturn(template(5L, "内容"));
        when(configMapper.selectAiSmsConfigByConfigId(5L)).thenReturn(config(10, "0", null));

        assertEquals("短信通道不存在或已停用",
            service.send("13800138000", 1L, null, null, null).getMessage());
    }

    @Test
    void send_dailyLimitExceeded_fails()
    {
        when(templateMapper.selectAiSmsTemplateByTemplateId(1L)).thenReturn(template(5L, "内容"));
        when(configMapper.selectAiSmsConfigByConfigId(5L)).thenReturn(config(2, "1", null));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(3L);

        SmsResult result = service.send("13800138000", 1L, null, null, null);

        assertFalse(result.isSuccess());
        assertEquals("超过单号码日发送上限(2)", result.getMessage());
        verify(mockSmsProvider, never()).send(any());
    }

    @Test
    void send_redisFails_fallsBackToDbCount()
    {
        when(templateMapper.selectAiSmsTemplateByTemplateId(1L)).thenReturn(template(5L, "内容"));
        when(configMapper.selectAiSmsConfigByConfigId(5L)).thenReturn(config(10, "1", null));
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis down"));
        when(logMapper.selectTodayCountByPhone("13800138000")).thenReturn(9);
        smsProviders.put("mockSmsProvider", mockSmsProvider);
        when(mockSmsProvider.send(any(SmsRequest.class))).thenReturn(SmsResult.success("msg-1", null));

        SmsResult result = service.send("13800138000", 1L, null, null, null);

        assertTrue(result.isSuccess());
        verify(logMapper).selectTodayCountByPhone("13800138000");
    }

    @Test
    void send_redisAndDbFail_allowsSend()
    {
        when(templateMapper.selectAiSmsTemplateByTemplateId(1L)).thenReturn(template(5L, "内容"));
        when(configMapper.selectAiSmsConfigByConfigId(5L)).thenReturn(config(10, "1", null));
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis down"));
        when(logMapper.selectTodayCountByPhone(anyString())).thenThrow(new IllegalStateException("db down"));
        smsProviders.put("mockSmsProvider", mockSmsProvider);
        when(mockSmsProvider.send(any(SmsRequest.class))).thenReturn(SmsResult.success("msg-1", null));

        assertTrue(service.send("13800138000", 1L, null, null, null).isSuccess());
    }

    @Test
    void send_unsubscribed_fails()
    {
        when(templateMapper.selectAiSmsTemplateByTemplateId(1L)).thenReturn(template(5L, "内容"));
        when(configMapper.selectAiSmsConfigByConfigId(5L)).thenReturn(config(0, "1", null));
        when(complianceGuard.isSmsAllowed("13800138000")).thenReturn(false);

        SmsResult result = service.send("13800138000", 1L, null, null, null);

        assertFalse(result.isSuccess());
        assertEquals("号码已退订，拒绝发送", result.getMessage());
    }

    @Test
    void send_success_rendersContentAndWritesBack()
    {
        when(templateMapper.selectAiSmsTemplateByTemplateId(1L))
            .thenReturn(template(5L, "您好${name}，金额${amount}元"));
        when(configMapper.selectAiSmsConfigByConfigId(5L)).thenReturn(config(0, "1", null));
        when(complianceGuard.isSmsAllowed(anyString())).thenReturn(true);
        smsProviders.put("mockSmsProvider", mockSmsProvider);
        when(mockSmsProvider.send(any(SmsRequest.class))).thenAnswer(invocation -> {
            SmsRequest request = invocation.getArgument(0);
            assertEquals("您好张三，金额100元", request.getContent());
            assertEquals("签名", request.getSignName());
            assertEquals("TPL", request.getTemplateCode());
            return SmsResult.success("msg-1", null);
        });
        when(logMapper.updateSendResult(anyLong(), anyString(), any(), any())).thenReturn(1);

        SmsResult result = service.send("13800138000", 1L,
            Map.of("name", "张三", "amount", "100"), null, null);

        assertTrue(result.isSuccess());
        assertEquals("您好张三，金额100元", result.getContent());
        AiSmsLog pending = lastInsertedLog();
        assertEquals("0", pending.getSendStatus());
        verify(logMapper).updateSendResult(eq(pending.getLogId()), eq("1"), eq("msg-1"), eq(null));
    }

    @Test
    void send_providerReturnsNull_treatedAsFailure()
    {
        when(templateMapper.selectAiSmsTemplateByTemplateId(1L)).thenReturn(template(5L, "内容"));
        when(configMapper.selectAiSmsConfigByConfigId(5L)).thenReturn(config(0, "1", null));
        smsProviders.put("mockSmsProvider", mockSmsProvider);
        when(mockSmsProvider.send(any(SmsRequest.class))).thenReturn(null);
        when(logMapper.updateSendResult(anyLong(), anyString(), any(), any())).thenReturn(1);

        SmsResult result = service.send("13800138000", 1L, null, null, null);

        assertFalse(result.isSuccess());
        assertEquals("供应商返回空结果", result.getMessage());
        verify(logMapper).updateSendResult(anyLong(), eq("2"), eq(null), eq("供应商返回空结果"));
    }

    @Test
    void send_configuredProviderMissing_throwsAndLogsFailure()
    {
        when(templateMapper.selectAiSmsTemplateByTemplateId(1L)).thenReturn(template(5L, "内容"));
        when(configMapper.selectAiSmsConfigByConfigId(5L)).thenReturn(config(0, "1", "ALI"));
        when(logMapper.updateSendResult(anyLong(), anyString(), any(), any())).thenReturn(1);

        SmsResult result = service.send("13800138000", 1L, null, null, null);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("短信供应商未配置或未启用：ALI"));
        verify(logMapper).updateSendResult(anyLong(), eq("2"), eq(null), anyString());
    }

    @Test
    void send_providerThrows_truncatesFailReason()
    {
        String longMessage = "x".repeat(600);
        when(templateMapper.selectAiSmsTemplateByTemplateId(1L)).thenReturn(template(5L, "内容"));
        when(configMapper.selectAiSmsConfigByConfigId(5L)).thenReturn(config(0, "1", null));
        smsProviders.put("mockSmsProvider", mockSmsProvider);
        when(mockSmsProvider.send(any(SmsRequest.class))).thenThrow(new IllegalStateException(longMessage));
        when(logMapper.updateSendResult(anyLong(), anyString(), any(), any())).thenReturn(1);

        SmsResult result = service.send("13800138000", 1L, null, null, null);

        assertFalse(result.isSuccess());
        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(logMapper).updateSendResult(anyLong(), eq("2"), eq(null), reason.capture());
        assertEquals(500, reason.getValue().length());
    }

    @Test
    void send_logInsertFails_stillSendsWithoutWriteBack()
    {
        when(templateMapper.selectAiSmsTemplateByTemplateId(1L)).thenReturn(template(5L, "内容"));
        when(configMapper.selectAiSmsConfigByConfigId(5L)).thenReturn(config(0, "1", null));
        when(logMapper.insertAiSmsLog(any(AiSmsLog.class))).thenThrow(new IllegalStateException("db down"));
        smsProviders.put("mockSmsProvider", mockSmsProvider);
        when(mockSmsProvider.send(any(SmsRequest.class))).thenReturn(SmsResult.success("msg-1", null));

        SmsResult result = service.send("13800138000", 1L, null, null, null);

        assertTrue(result.isSuccess());
        verify(logMapper, never()).updateSendResult(anyLong(), anyString(), any(), any());
    }

    @Test
    void send_writeBackReturnsZero_keepsSuccess()
    {
        when(templateMapper.selectAiSmsTemplateByTemplateId(1L)).thenReturn(template(5L, "内容"));
        when(configMapper.selectAiSmsConfigByConfigId(5L)).thenReturn(config(0, "1", null));
        smsProviders.put("mockSmsProvider", mockSmsProvider);
        when(mockSmsProvider.send(any(SmsRequest.class))).thenReturn(SmsResult.success("msg-1", null));
        when(logMapper.updateSendResult(anyLong(), anyString(), any(), any())).thenReturn(0);

        assertTrue(service.send("13800138000", 1L, null, null, null).isSuccess());
    }

    @Test
    void send_writeBackThrows_keepsSuccess()
    {
        when(templateMapper.selectAiSmsTemplateByTemplateId(1L)).thenReturn(template(5L, "内容"));
        when(configMapper.selectAiSmsConfigByConfigId(5L)).thenReturn(config(0, "1", null));
        smsProviders.put("mockSmsProvider", mockSmsProvider);
        when(mockSmsProvider.send(any(SmsRequest.class))).thenReturn(SmsResult.success("msg-1", null));
        when(logMapper.updateSendResult(anyLong(), anyString(), any(), any()))
            .thenThrow(new IllegalStateException("db down"));

        assertTrue(service.send("13800138000", 1L, null, null, null).isSuccess());
    }

    @Test
    void send_firstQuotaIncrement_setsExpire()
    {
        when(templateMapper.selectAiSmsTemplateByTemplateId(1L)).thenReturn(template(5L, "内容"));
        when(configMapper.selectAiSmsConfigByConfigId(5L)).thenReturn(config(5, "1", null));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String key = "sms:limit:13800138000:" + LocalDate.now();
        when(valueOperations.increment(key)).thenReturn(1L);
        smsProviders.put("mockSmsProvider", mockSmsProvider);
        when(mockSmsProvider.send(any(SmsRequest.class))).thenReturn(SmsResult.success("msg-1", null));
        when(logMapper.updateSendResult(anyLong(), anyString(), any(), any())).thenReturn(1);
        when(redisCache.expire(eq(key), anyLong())).thenReturn(true);

        assertTrue(service.send("13800138000", 1L, null, null, null).isSuccess());
        verify(redisCache).expire(eq(key), anyLong());
    }
}
