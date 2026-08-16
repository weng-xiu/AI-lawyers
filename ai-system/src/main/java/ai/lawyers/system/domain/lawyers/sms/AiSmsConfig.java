package ai.lawyers.system.domain.lawyers.sms;

import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 短信通道配置对象 ai_sms_config
 *
 * @author ai-lawyers
 */
public class AiSmsConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 通道ID */
    private Long configId;

    /** 通道名称 */
    @Excel(name = "通道名称")
    private String configName;

    /** 供应商（mock/aliyun/tencent） */
    @Excel(name = "供应商")
    private String provider;

    /** 访问密钥ID */
    private String accessKeyId;

    /** 访问密钥Secret */
    private String accessKeySecret;

    /** 短信签名 */
    @Excel(name = "短信签名")
    private String signName;

    /** 地域（阿里云） */
    private String regionId;

    /** 腾讯云AppId */
    private String sdkAppId;

    /** 单号码日发送上限 */
    @Excel(name = "日发送上限")
    private Integer dailyLimit;

    /** 状态（0停用 1启用） */
    @Excel(name = "状态", readConverterExp = "0=停用,1=启用")
    private String status;

    public Long getConfigId() { return configId; }
    public void setConfigId(Long configId) { this.configId = configId; }

    public String getConfigName() { return configName; }
    public void setConfigName(String configName) { this.configName = configName; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }

    public String getAccessKeySecret() { return accessKeySecret; }
    public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }

    public String getSignName() { return signName; }
    public void setSignName(String signName) { this.signName = signName; }

    public String getRegionId() { return regionId; }
    public void setRegionId(String regionId) { this.regionId = regionId; }

    public String getSdkAppId() { return sdkAppId; }
    public void setSdkAppId(String sdkAppId) { this.sdkAppId = sdkAppId; }

    public Integer getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(Integer dailyLimit) { this.dailyLimit = dailyLimit; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("configId", getConfigId())
            .append("configName", getConfigName())
            .append("provider", getProvider())
            .append("status", getStatus())
            .toString();
    }
}
