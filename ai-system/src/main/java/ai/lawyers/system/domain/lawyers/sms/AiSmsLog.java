package ai.lawyers.system.domain.lawyers.sms;

import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 短信发送记录对象 ai_sms_log
 *
 * @author ai-lawyers
 */
public class AiSmsLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    private Long logId;

    /** 接收号码 */
    @Excel(name = "接收号码")
    private String phone;

    /** 模板ID */
    private Long templateId;

    /** 通道ID */
    private Long configId;

    /** 模板变量JSON */
    private String paramsJson;

    /** 实际发送内容 */
    @Excel(name = "发送内容")
    private String content;

    /** 发送状态（0待发 1成功 2失败） */
    @Excel(name = "发送状态", readConverterExp = "0=待发,1=成功,2=失败")
    private String sendStatus;

    /** 供应商回执ID */
    @Excel(name = "回执ID")
    private String providerMsgId;

    /** 失败原因 */
    @Excel(name = "失败原因")
    private String failReason;

    /** IVR会话ID */
    private String sessionId;

    /** 通话记录ID */
    private Long recordId;

    /** 模板名称（关联查询） */
    @Excel(name = "模板")
    private String templateName;

    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public Long getConfigId() { return configId; }
    public void setConfigId(Long configId) { this.configId = configId; }

    public String getParamsJson() { return paramsJson; }
    public void setParamsJson(String paramsJson) { this.paramsJson = paramsJson; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSendStatus() { return sendStatus; }
    public void setSendStatus(String sendStatus) { this.sendStatus = sendStatus; }

    public String getProviderMsgId() { return providerMsgId; }
    public void setProviderMsgId(String providerMsgId) { this.providerMsgId = providerMsgId; }

    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("logId", getLogId())
            .append("phone", getPhone())
            .append("templateId", getTemplateId())
            .append("sendStatus", getSendStatus())
            .toString();
    }
}
