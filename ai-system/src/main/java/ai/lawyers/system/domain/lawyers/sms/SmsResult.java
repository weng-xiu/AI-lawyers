package ai.lawyers.system.domain.lawyers.sms;

/**
 * 短信发送结果。
 *
 * @author ai-lawyers
 */
public class SmsResult
{
    /** 是否成功 */
    private boolean success;

    /** 结果消息（失败时为原因） */
    private String message;

    /** 供应商回执ID（Mock 为本地生成） */
    private String msgId;

    /** 实际发送内容 */
    private String content;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getMsgId() { return msgId; }
    public void setMsgId(String msgId) { this.msgId = msgId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public static SmsResult success(String msgId, String content)
    {
        SmsResult r = new SmsResult();
        r.setSuccess(true);
        r.setMsgId(msgId);
        r.setContent(content);
        r.setMessage("发送成功");
        return r;
    }

    public static SmsResult fail(String message)
    {
        SmsResult r = new SmsResult();
        r.setSuccess(false);
        r.setMessage(message);
        return r;
    }
}
