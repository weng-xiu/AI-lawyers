package ai.lawyers.system.domain.lawyers;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 协同外部机构台账对象 ai_external_org（F3）
 *
 * <p>法律援助机构 / 人民调解委员会 / 公证处 / 司法鉴定机构 / 仲裁机构 / 12345 政务热线，
 * 按省-市-区县维护，支撑一键转办与公众端服务导航。</p>
 *
 * @author ai-lawyers
 */
public class AiExternalOrg extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 机构ID */
    private Long orgId;

    /** 机构名称 */
    @Excel(name = "机构名称")
    private String orgName;

    /** 条线类型 LEGAL_AID/MEDIATION/NOTARY/FORENSIC/ARBITRATION/HOTLINE_12345 */
    @Excel(name = "条线类型")
    private String externalType;

    @Excel(name = "省")
    private String province;

    @Excel(name = "市")
    private String city;

    @Excel(name = "区县")
    private String district;

    @Excel(name = "机构地址")
    private String address;

    @Excel(name = "联系电话")
    private String contactPhone;

    @Excel(name = "联系人")
    private String contactPerson;

    /** 对接方式 API/FILE/MANUAL */
    @Excel(name = "对接方式")
    private String accessMode;

    /** 协同接口地址（API方式） */
    private String apiUrl;

    /** 对接AppId（API方式） */
    private String appId;

    /** 对接密钥（API方式，P3-G1 同步加密存储） */
    private String appSecret;

    @Excel(name = "服务时间")
    private String serviceHours;

    /** 申请材料清单（转介指引展示） */
    private String applyMaterials;

    /** 状态 0启用 1停用 */
    @Excel(name = "状态", readConverterExp = "0=启用,1=停用")
    private String status;

    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }

    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }

    public String getExternalType() { return externalType; }
    public void setExternalType(String externalType) { this.externalType = externalType; }

    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public String getAccessMode() { return accessMode; }
    public void setAccessMode(String accessMode) { this.accessMode = accessMode; }

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }

    public String getServiceHours() { return serviceHours; }
    public void setServiceHours(String serviceHours) { this.serviceHours = serviceHours; }

    public String getApplyMaterials() { return applyMaterials; }
    public void setApplyMaterials(String applyMaterials) { this.applyMaterials = applyMaterials; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("orgId", getOrgId())
                .append("orgName", getOrgName())
                .append("externalType", getExternalType())
                .append("province", getProvince())
                .append("city", getCity())
                .append("district", getDistrict())
                .append("contactPhone", getContactPhone())
                .append("accessMode", getAccessMode())
                .append("status", getStatus())
                .toString();
    }
}
