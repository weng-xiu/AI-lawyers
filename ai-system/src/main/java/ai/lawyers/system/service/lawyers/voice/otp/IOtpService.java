package ai.lawyers.system.service.lawyers.voice.otp;

/**
 * P3-#12（核身分级 L2）：语音通话内短信 OTP 核身服务接口。
 *
 * <p>对应调研报告 6.3-C 核身分级：L1 号码锚定（已落地，只读）→
 * <b>L2 短信 OTP（本服务，低敏写操作：发短信/预约回访）</b>→ L3 声纹（随 #15）。</p>
 *
 * <p>职责边界：生成（6 位数字码）/过期（TTL）/校验（一次性 + 错误次数上限）+ 尽力发送；
 * 不落库、不与工单/档案关联，核身结论由调用方（写操作确认状态机）消费。</p>
 *
 * <p>降级原则：短信服务未装配（内网 Mock 环境）时仍本地签发，便于全链路联调；
 * 短信通道明确返回失败时撤销本次签发。任何路径不外抛。</p>
 *
 * @author ai-lawyers
 */
public interface IOtpService
{
    /**
     * 签发核身码并发送短信。
     *
     * @param phone     接收号码（本人号码，只允许来自服务端锚定话单）
     * @param sessionId 语音会话ID（短信发送记录关联，可空）
     * @param recordId  通话记录ID（短信发送记录关联，可空）
     * @return 签发结果（含 TTL 与短信回执ID；同号重发间隔内/通道失败/容量保护时失败）
     */
    OtpIssueResult issue(String phone, String sessionId, Long recordId);

    /**
     * 校验核身码（一次性：校验通过即作废）。
     *
     * @param phone 接收号码（与签发时一致）
     * @param code  用户回输的核身码（DTMF 或 ASR 提取）
     * @return 校验状态；除 {@link OtpVerifyStatus#VERIFIED} 外均表示未通过
     */
    OtpVerifyStatus verify(String phone, String code);

    /**
     * 签发结果值类。
     */
    class OtpIssueResult
    {
        /** 是否签发成功 */
        private final boolean success;

        /** 结果消息（失败时为原因） */
        private final String message;

        /** 有效期（秒），供前端/IVR 播报提示 */
        private final int ttlSeconds;

        /** 短信回执ID（本地模式为 null） */
        private final String msgId;

        private OtpIssueResult(boolean success, String message, int ttlSeconds, String msgId)
        {
            this.success = success;
            this.message = message;
            this.ttlSeconds = ttlSeconds;
            this.msgId = msgId;
        }

        public static OtpIssueResult ok(int ttlSeconds, String msgId)
        {
            return new OtpIssueResult(true, "核身码已发送", ttlSeconds, msgId);
        }

        public static OtpIssueResult fail(String message)
        {
            return new OtpIssueResult(false, message, 0, null);
        }

        public boolean isSuccess() { return success; }

        public String getMessage() { return message; }

        public int getTtlSeconds() { return ttlSeconds; }

        public String getMsgId() { return msgId; }
    }

    /**
     * 校验状态枚举。
     */
    enum OtpVerifyStatus
    {
        /** 核身通过 */
        VERIFIED("核身通过"),
        /** 核身码不存在或已被使用 */
        NOT_FOUND("核身码不存在或已使用"),
        /** 核身码已过期 */
        EXPIRED("核身码已过期，请重新获取"),
        /** 核身码错误 */
        INVALID_CODE("核身码错误"),
        /** 错误次数过多，核身码已作废 */
        TOO_MANY_ATTEMPTS("错误次数过多，请重新获取核身码");

        private final String label;

        OtpVerifyStatus(String label)
        {
            this.label = label;
        }

        public String getLabel()
        {
            return label;
        }
    }
}
