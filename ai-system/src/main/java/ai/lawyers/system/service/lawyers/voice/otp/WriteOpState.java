package ai.lawyers.system.service.lawyers.voice.otp;

/**
 * P3-#12：写操作确认状态机状态。
 *
 * <p>通话内确认帧协议（与媒体层解耦，IVR/机器人侧按键映射到服务 API）：</p>
 * <pre>
 * DRAFT ──requestOtp──▶ OTP_SENT ──verifyIdentity(OTP通过)──▶ VERIFIED
 *        （OTP失败保持DRAFT）   （错码保持OTP_SENT；达上限→REJECTED）
 * VERIFIED ──armConfirm(播报操作摘要)──▶ AWAITING_CONFIRM
 * AWAITING_CONFIRM ──confirm(用户按1)──▶ CONFIRMED ──complete(业务执行成功)──▶ EXECUTED
 * 任意非终态 ──reject(用户按2/核身达上限)──▶ REJECTED；超时懒判定 → EXPIRED
 * </pre>
 *
 * @author ai-lawyers
 */
public enum WriteOpState
{
    /** 草稿已建，待发送核身码 */
    DRAFT("草稿已建"),

    /** 核身码已发送 */
    OTP_SENT("核身码已发送"),

    /** 身份已核验（OTP 通过），可播报操作摘要 */
    VERIFIED("身份已核验"),

    /** 已播报操作摘要，等待用户按键确认（1=确认 2=取消） */
    AWAITING_CONFIRM("等待用户确认"),

    /** 用户已确认，待业务执行器落库 */
    CONFIRMED("用户已确认"),

    /** 已执行（终态） */
    EXECUTED("已执行"),

    /** 用户取消或核身失败（终态） */
    REJECTED("已取消"),

    /** 会话超时（终态） */
    EXPIRED("已超时");

    private final String label;

    WriteOpState(String label)
    {
        this.label = label;
    }

    public String getLabel()
    {
        return label;
    }

    public boolean isTerminal()
    {
        return this == EXECUTED || this == REJECTED || this == EXPIRED;
    }
}
