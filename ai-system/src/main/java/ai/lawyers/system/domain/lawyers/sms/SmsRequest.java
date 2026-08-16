package ai.lawyers.system.domain.lawyers.sms;

/**
 * 短信发送请求（供供应商 Provider 使用，已渲染好变量）。
 *
 * @author ai-lawyers
 */
public class SmsRequest
{
    /** 接收号码 */
    private String phone;

    /** 短信签名 */
    private String signName;

    /** 供应商模板CODE */
    private String templateCode;

    /** 实际发送内容（变量已渲染） */
    private String content;

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getSignName() { return signName; }
    public void setSignName(String signName) { this.signName = signName; }

    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
