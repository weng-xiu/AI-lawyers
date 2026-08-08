package ai.lawyers.system.domain.lawyers.trunk;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 号段-运营商路由 对象 ai_number_segment
 *
 * 用于根据被叫号码前缀识别所属运营商，采用"最长前缀优先"匹配。
 */
public class AiNumberSegment extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 号段ID */
    private Long segmentId;

    /** 号段前缀 */
    @Excel(name = "号段前缀")
    private String segmentPrefix;

    /** 运营商 */
    @Excel(name = "运营商", readConverterExp = "CM=中国移动,CU=中国联通,CT=中国电信,CB=中国广电,VI=虚拟运营商")
    private String carrier;

    /** 号码类型 1=手机 2=固话区号 3=虚商 4=特服号 */
    @Excel(name = "号码类型", readConverterExp = "1=手机,2=固话区号,3=虚商,4=特服号")
    private String numberType;

    /** 归属省份 */
    @Excel(name = "省份")
    private String province;

    /** 归属城市 */
    @Excel(name = "城市")
    private String city;

    /** 匹配长度 */
    private Integer matchLength;

    /** 启用状态 0=停用 1=启用 */
    private String enableFlag;

    public void setSegmentId(Long segmentId) { this.segmentId = segmentId; }

    public Long getSegmentId() { return segmentId; }

    public void setSegmentPrefix(String segmentPrefix) { this.segmentPrefix = segmentPrefix; }

    public String getSegmentPrefix() { return segmentPrefix; }

    public void setCarrier(String carrier) { this.carrier = carrier; }

    public String getCarrier() { return carrier; }

    public void setNumberType(String numberType) { this.numberType = numberType; }

    public String getNumberType() { return numberType; }

    public void setProvince(String province) { this.province = province; }

    public String getProvince() { return province; }

    public void setCity(String city) { this.city = city; }

    public String getCity() { return city; }

    public void setMatchLength(Integer matchLength) { this.matchLength = matchLength; }

    public Integer getMatchLength() { return matchLength; }

    public void setEnableFlag(String enableFlag) { this.enableFlag = enableFlag; }

    public String getEnableFlag() { return enableFlag; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("segmentId", getSegmentId())
            .append("segmentPrefix", getSegmentPrefix())
            .append("carrier", getCarrier())
            .append("numberType", getNumberType())
            .append("province", getProvince())
            .append("city", getCity())
            .append("matchLength", getMatchLength())
            .append("enableFlag", getEnableFlag())
            .toString();
    }
}
