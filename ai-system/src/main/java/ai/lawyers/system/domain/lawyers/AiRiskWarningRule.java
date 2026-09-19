package ai.lawyers.system.domain.lawyers;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 智能风险预警规则对象 ai_risk_warning_rule
 */
public class AiRiskWarningRule extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long ruleId;

    @Excel(name = "规则名称")
    private String ruleName;

    @Excel(name = "规则类型", readConverterExp = "敏感词=敏感词,情绪异常=情绪异常,异常行为=异常行为,合规风险=合规风险")
    private String ruleType;

    @Excel(name = "规则级别", readConverterExp = "1=高,2=中,3=低")
    private String ruleLevel;

    @Excel(name = "关键词")
    private String keywords;

    /** 建议转办条线 LEGAL_AID/MEDIATION/NOTARY/FORENSIC/ARBITRATION/HOTLINE_12345（F3，空=不建议） */
    @Excel(name = "建议转办条线")
    private String suggestTransferType;

    /** 默认建议协同机构ID（F3，空=坐席按条线自选） */
    private Long suggestOrgId;

    @Excel(name = "是否启用", readConverterExp = "0=禁用,1=启用")
    private String isEnabled;

    public void setRuleId(Long ruleId)
    {
        this.ruleId = ruleId;
    }

    public Long getRuleId()
    {
        return ruleId;
    }

    public void setRuleName(String ruleName)
    {
        this.ruleName = ruleName;
    }

    public String getRuleName()
    {
        return ruleName;
    }

    public void setRuleType(String ruleType)
    {
        this.ruleType = ruleType;
    }

    public String getRuleType()
    {
        return ruleType;
    }

    public void setRuleLevel(String ruleLevel)
    {
        this.ruleLevel = ruleLevel;
    }

    public String getRuleLevel()
    {
        return ruleLevel;
    }

    public void setKeywords(String keywords)
    {
        this.keywords = keywords;
    }

    public String getKeywords()
    {
        return keywords;
    }

    public void setSuggestTransferType(String suggestTransferType)
    {
        this.suggestTransferType = suggestTransferType;
    }

    public String getSuggestTransferType()
    {
        return suggestTransferType;
    }

    public void setSuggestOrgId(Long suggestOrgId)
    {
        this.suggestOrgId = suggestOrgId;
    }

    public Long getSuggestOrgId()
    {
        return suggestOrgId;
    }

    public void setIsEnabled(String isEnabled)
    {
        this.isEnabled = isEnabled;
    }

    public String getIsEnabled()
    {
        return isEnabled;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("ruleId", getRuleId())
            .append("ruleName", getRuleName())
            .append("ruleType", getRuleType())
            .append("ruleLevel", getRuleLevel())
            .append("keywords", getKeywords())
            .append("suggestTransferType", getSuggestTransferType())
            .append("suggestOrgId", getSuggestOrgId())
            .append("isEnabled", getIsEnabled())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
