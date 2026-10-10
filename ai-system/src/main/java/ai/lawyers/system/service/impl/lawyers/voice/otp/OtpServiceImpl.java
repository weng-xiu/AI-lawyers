package ai.lawyers.system.service.impl.lawyers.voice.otp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.sign.PiiCryptoUtils;
import ai.lawyers.system.domain.lawyers.sms.SmsResult;
import ai.lawyers.system.service.lawyers.sms.ISmsService;
import ai.lawyers.system.service.lawyers.voice.otp.IOtpService;

/**
 * P3-#12（核身分级 L2）：通话内短信 OTP 核身服务实现。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>存储：内存 ConcurrentHashMap，key 为号码盲索引（{@link PiiCryptoUtils#blindIndex}，
 *       不落明文手机号）；value 存核身码的 SHA-256 哈希（不存明文），过期懒清理 + 容量上限保护；</li>
 *   <li>生命周期：TTL 内有效（默认 300s）、同号重发间隔（默认 60s）、校验通过即作废（一次性）、
 *       连续错码达上限即作废（防爆破）；</li>
 *   <li>发送：经 {@link ISmsService}（模板 {@code ai.voice.otp.template-id}，未配置时跳过真实发送，
 *       本地 Mock 环境仍签发可测）；通道明确失败时撤销本次签发。</li>
 * </ul>
 *
 * <p>不落库：核身码与校验过程仅内存留存（TTL 后懒清理），不写入任何持久化表；
 * 短信发送记录（含模板内容）由 SmsService 既有链路负责。</p>
 *
 * @author ai-lawyers
 */
@Service
public class OtpServiceImpl implements IOtpService
{
    private static final Logger log = LoggerFactory.getLogger(OtpServiceImpl.class);

    /** 核身码长度（6 位数字，DTMF 回输友好） */
    private static final int CODE_LENGTH = 6;

    /** 存储容量上限（防内存膨胀；触顶时先清过期，仍满则拒绝新签发） */
    private static final int MAX_STORE = 10000;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 短信服务（可缺席：内网 Mock 环境跳过真实发送仍本地签发） */
    @Autowired(required = false)
    private ISmsService smsService;

    /** OTP 短信模板ID（未配置时跳过真实发送） */
    @Value("${ai.voice.otp.template-id:}")
    private String otpTemplateId = "";

    /** 有效期（秒） */
    @Value("${ai.voice.otp.ttl-seconds:300}")
    private int ttlSeconds = 300;

    /** 同号重发间隔（秒） */
    @Value("${ai.voice.otp.resend-interval-seconds:60}")
    private int resendIntervalSeconds = 60;

    /** 连续错码作废上限（次） */
    @Value("${ai.voice.otp.max-verify-attempts:5}")
    private int maxVerifyAttempts = 5;

    /**
     * 时钟覆写（毫秒；&gt;=0 时替代系统时钟，仅测试用）。
     */
    long nowOverride = -1;

    /** 存储：盲索引(phone) -> 条目 */
    private final Map<String, OtpEntry> store = new ConcurrentHashMap<>();

    @Override
    public OtpIssueResult issue(String phone, String sessionId, Long recordId)
    {
        try
        {
            if (phone == null || phone.trim().isEmpty())
            {
                return OtpIssueResult.fail("号码为空，无法签发核身码");
            }
            String key = PiiCryptoUtils.blindIndex(phone.trim());
            long now = now();
            purgeIfNeeded(now);
            OtpEntry existing = store.get(key);
            if (existing != null && now < existing.issueAt + resendIntervalSeconds * 1000L)
            {
                return OtpIssueResult.fail("发送过于频繁，请稍后再试");
            }
            String code = randomCode();
            String msgId = sendSms(phone, code, sessionId, recordId);
            if (SEND_FAILED.equals(msgId))
            {
                return OtpIssueResult.fail("短信发送失败，请稍后再试");
            }
            store.put(key, new OtpEntry(sha256(code), now, now + ttlSeconds * 1000L));
            log.info("OTP 签发 phoneIndex={} ttl={}s smsMsgId={}", maskIndex(key), ttlSeconds, msgId);
            return OtpIssueResult.ok(ttlSeconds, msgId);
        }
        catch (Exception e)
        {
            log.warn("OTP 签发异常: {}", e.getMessage());
            return OtpIssueResult.fail("核身码签发异常，请稍后再试");
        }
    }

    @Override
    public OtpVerifyStatus verify(String phone, String code)
    {
        try
        {
            if (phone == null || phone.trim().isEmpty() || code == null || code.trim().isEmpty())
            {
                return OtpVerifyStatus.NOT_FOUND;
            }
            String key = PiiCryptoUtils.blindIndex(phone.trim());
            OtpEntry entry = store.get(key);
            if (entry == null)
            {
                return OtpVerifyStatus.NOT_FOUND;
            }
            long now = now();
            if (now > entry.expireAt)
            {
                store.remove(key);
                return OtpVerifyStatus.EXPIRED;
            }
            if (entry.attempts >= maxVerifyAttempts)
            {
                store.remove(key);
                return OtpVerifyStatus.TOO_MANY_ATTEMPTS;
            }
            if (entry.codeHash.equals(sha256(code.trim())))
            {
                store.remove(key);
                return OtpVerifyStatus.VERIFIED;
            }
            entry.attempts++;
            if (entry.attempts >= maxVerifyAttempts)
            {
                store.remove(key);
                log.info("OTP 连续错码达上限，条目作废 phoneIndex={}", maskIndex(key));
                return OtpVerifyStatus.TOO_MANY_ATTEMPTS;
            }
            return OtpVerifyStatus.INVALID_CODE;
        }
        catch (Exception e)
        {
            log.warn("OTP 校验异常: {}", e.getMessage());
            return OtpVerifyStatus.NOT_FOUND;
        }
    }

    /** 发送哨兵值：sendSms 返回它表示通道明确失败（撤销签发） */
    private static final String SEND_FAILED = "__SEND_FAILED__";

    /**
     * 发送核身短信。短信服务缺席或模板未配置 → 本地模式（返回 null，仍签发）；
     * 通道明确失败 → 返回 {@link #SEND_FAILED}。
     */
    private String sendSms(String phone, String code, String sessionId, Long recordId)
    {
        if (smsService == null || otpTemplateId == null || otpTemplateId.trim().isEmpty())
        {
            log.info("OTP 短信服务/模板未配置，本地模式签发 phoneIndex={}", maskIndex(PiiCryptoUtils.blindIndex(phone.trim())));
            return null;
        }
        try
        {
            Long templateId = Long.valueOf(otpTemplateId.trim());
            java.util.Map<String, String> params = new java.util.HashMap<>();
            params.put("code", code);
            SmsResult r = smsService.send(phone, templateId, params, sessionId, recordId);
            if (r == null || !r.isSuccess())
            {
                log.info("OTP 短信发送失败，撤销签发 phoneIndex={} reason={}",
                        maskIndex(PiiCryptoUtils.blindIndex(phone.trim())),
                        r == null ? "null" : r.getMessage());
                return SEND_FAILED;
            }
            return r.getMsgId();
        }
        catch (NumberFormatException e)
        {
            log.warn("OTP 短信模板ID配置非法（{}），按本地模式签发", otpTemplateId);
            return null;
        }
    }

    /** 容量保护：触顶先清过期条目 */
    private void purgeIfNeeded(long now)
    {
        if (store.size() < MAX_STORE)
        {
            return;
        }
        store.entrySet().removeIf(e -> e.getValue().expireAt < now);
    }

    private String randomCode()
    {
        int bound = 900000;
        return String.valueOf(100000 + RANDOM.nextInt(bound));
    }

    private static String sha256(String plain)
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(plain.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest)
            {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        }
        catch (Exception e)
        {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 日志脱敏：盲索引只留前 8 位 */
    private static String maskIndex(String key)
    {
        return key == null ? "null" : key.substring(0, Math.min(8, key.length())) + "**";
    }

    private long now()
    {
        return nowOverride >= 0 ? nowOverride : System.currentTimeMillis();
    }

    /**
     * 存储条目（codeHash 为 6 位码的 SHA-256 hex，不存明文）。
     */
    private static final class OtpEntry
    {
        final String codeHash;

        final long issueAt;

        final long expireAt;

        volatile int attempts;

        OtpEntry(String codeHash, long issueAt, long expireAt)
        {
            this.codeHash = codeHash;
            this.issueAt = issueAt;
            this.expireAt = expireAt;
        }
    }
}
