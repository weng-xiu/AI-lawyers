package ai.lawyers.system.service.lawyers.voice.otp;

/**
 * P3-#12：机器人写操作通话内确认状态机接口。
 *
 * <p>协议流程（对应 {@link WriteOpState} 状态图）：白名单草稿（{@code begin}）
 * → 发核身码（{@code requestOtp}，L2 短信 OTP）→ 核身（{@code verifyIdentity}）
 * → 播报操作摘要并等待按键（{@code armConfirm}；"确认请按1，取消请按2"）
 * → 用户确认（{@code confirm}）→ 业务执行器落库后回执（{@code complete}）。</p>
 *
 * <p>纪律：动作必须在 {@link WriteOpAction} 白名单内；callerNumber 只允许来自
 * 服务端锚定话单（L1 已保证）；未经 {@code confirm} 任何组件不得落库；
 * 会话纯内存、TTL 懒过期；同一号码同时只允许一个活跃会话（防轰炸）。</p>
 *
 * @author ai-lawyers
 */
public interface IWriteOpConfirmService
{
    /**
     * 创建白名单写操作草稿会话（状态 DRAFT）。
     *
     * @param action       白名单动作（白名单外拒绝）
     * @param sessionId    语音会话ID
     * @param recordId     服务端锚定话单ID
     * @param callerNumber 来电号码（服务端锚定，不接受前端透传）
     * @param payloadJson  动作参数草稿（JSON）
     * @return 创建结果（含 opId）
     */
    BeginResult begin(WriteOpAction action, String sessionId, Long recordId,
                      String callerNumber, String payloadJson);

    /**
     * 发送核身码：DRAFT → OTP_SENT（OTP 签发失败保持 DRAFT 可重试）。
     */
    TransitionResult requestOtp(String opId);

    /**
     * 核身：OTP_SENT → VERIFIED（错码保持 OTP_SENT；连续错码达上限 → REJECTED）。
     */
    TransitionResult verifyIdentity(String opId, String otpCode);

    /**
     * 播报操作摘要并进入等待确认：VERIFIED → AWAITING_CONFIRM。
     *
     * @param summarySpeech 操作摘要话术（必须包含动作要点，供 TTS 播报与审计）
     */
    TransitionResult armConfirm(String opId, String summarySpeech);

    /**
     * 用户确认（IVR 按 1 / 语音肯定意图）：AWAITING_CONFIRM → CONFIRMED。
     */
    TransitionResult confirm(String opId);

    /**
     * 业务执行成功回执：CONFIRMED → EXECUTED（终态）。
     * 仅允许在业务执行器真正落库成功后调用。
     */
    TransitionResult complete(String opId);

    /**
     * 取消（IVR 按 2 / 语音否定意图 / 核身达上限）：任意非终态 → REJECTED（终态）。
     */
    TransitionResult reject(String opId);

    /**
     * 查询会话（含 TTL 懒过期判定；不存在返回 null）。
     */
    WriteOpSession get(String opId);

    /**
     * 创建结果值类。
     */
    class BeginResult
    {
        private final boolean success;

        private final String message;

        private final String opId;

        private BeginResult(boolean success, String message, String opId)
        {
            this.success = success;
            this.message = message;
            this.opId = opId;
        }

        public static BeginResult ok(String opId)
        {
            return new BeginResult(true, "会话已创建", opId);
        }

        public static BeginResult fail(String message)
        {
            return new BeginResult(false, message, null);
        }

        public boolean isSuccess() { return success; }

        public String getMessage() { return message; }

        public String getOpId() { return opId; }
    }

    /**
     * 状态转换结果值类。
     */
    class TransitionResult
    {
        private final boolean success;

        private final String message;

        /** 转换后状态（失败时为当前状态） */
        private final WriteOpState state;

        private TransitionResult(boolean success, String message, WriteOpState state)
        {
            this.success = success;
            this.message = message;
            this.state = state;
        }

        public static TransitionResult ok(WriteOpState state)
        {
            return new TransitionResult(true, "操作成功", state);
        }

        public static TransitionResult fail(String message, WriteOpState state)
        {
            return new TransitionResult(false, message, state);
        }

        public boolean isSuccess() { return success; }

        public String getMessage() { return message; }

        public WriteOpState getState() { return state; }
    }
}
