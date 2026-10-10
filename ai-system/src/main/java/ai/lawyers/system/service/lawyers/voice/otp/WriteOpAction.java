package ai.lawyers.system.service.lawyers.voice.otp;

/**
 * P3-#12（核身分级 L2）：机器人写操作白名单。
 *
 * <p>对应调研报告 6.3-C：L2 短信 OTP 核身解锁的低敏写操作（发短信、预约回访）。
 * 敏感写操作须 L3 声纹（随 #15），不在本白名单。写操作沿用 copilot_actions
 * 的"白名单草稿 + 播报二次确认"模式，模型不直接落库——状态机通过后才由
 * 业务执行器（后续迭代）按 payloadJson 草稿落库。</p>
 *
 * @author ai-lawyers
 */
public enum WriteOpAction
{
    /** 机器人代发短信（如：补发办事指南/材料清单链接） */
    SEND_SMS("sendSms", "代发短信"),

    /** 预约人工回访（登记回访时间与事项） */
    BOOK_CALLBACK("bookCallback", "预约回访");

    private final String code;

    private final String desc;

    WriteOpAction(String code, String desc)
    {
        this.code = code;
        this.desc = desc;
    }

    public String getCode()
    {
        return code;
    }

    public String getDesc()
    {
        return desc;
    }

    /** 按编码解析（白名单外编码返回 null，调用方拒绝） */
    public static WriteOpAction ofCode(String code)
    {
        if (code == null)
        {
            return null;
        }
        for (WriteOpAction a : values())
        {
            if (a.code.equals(code))
            {
                return a;
            }
        }
        return null;
    }
}
