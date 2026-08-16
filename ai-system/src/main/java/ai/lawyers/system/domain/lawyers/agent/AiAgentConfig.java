package ai.lawyers.system.domain.lawyers.agent;

import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * AI智能体配置对象 ai_agent_config
 *
 * @author ai-lawyers
 */
public class AiAgentConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 智能体ID */
    private Long agentId;

    /** 智能体名称 */
    @Excel(name = "智能体名称")
    private String agentName;

    /** 平台（local/maxkb/dify/fastgpt/coze） */
    @Excel(name = "平台")
    private String provider;

    /** 外部平台对话接口地址 */
    private String apiUrl;

    /** 外部平台认证密钥 */
    private String apiKey;

    /** 平台应用/知识库ID */
    private String appId;

    /** 默认关联咨询分类ID */
    private Long categoryId;

    /** 角色设定系统提示词 */
    private String systemPrompt;

    /** 关联大模型配置ID（为空使用默认模型） */
    private Long modelId;

    /** 限定知识库ID（逗号分隔） */
    private String knowledgeIds;

    /** 是否启用多轮上下文（0否 1是） */
    @Excel(name = "多轮上下文", readConverterExp = "0=否,1=是")
    private String enableContext;

    /** 上下文保留轮数 */
    @Excel(name = "上下文轮数")
    private Integer contextRounds;

    /** 转人工关键词（逗号分隔） */
    private String handoffKeywords;

    /** 首轮欢迎语 */
    @Excel(name = "欢迎语")
    private String welcome;

    /** 状态（0停用 1启用） */
    @Excel(name = "状态", readConverterExp = "0=停用,1=启用")
    private String status;

    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public Long getAgentId() { return agentId; }

    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getAgentName() { return agentName; }

    public void setProvider(String provider) { this.provider = provider; }
    public String getProvider() { return provider; }

    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public String getApiUrl() { return apiUrl; }

    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getApiKey() { return apiKey; }

    public void setAppId(String appId) { this.appId = appId; }
    public String getAppId() { return appId; }

    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Long getCategoryId() { return categoryId; }

    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getSystemPrompt() { return systemPrompt; }

    public void setModelId(Long modelId) { this.modelId = modelId; }
    public Long getModelId() { return modelId; }

    public void setKnowledgeIds(String knowledgeIds) { this.knowledgeIds = knowledgeIds; }
    public String getKnowledgeIds() { return knowledgeIds; }

    public void setEnableContext(String enableContext) { this.enableContext = enableContext; }
    public String getEnableContext() { return enableContext; }

    public void setContextRounds(Integer contextRounds) { this.contextRounds = contextRounds; }
    public Integer getContextRounds() { return contextRounds; }

    public void setHandoffKeywords(String handoffKeywords) { this.handoffKeywords = handoffKeywords; }
    public String getHandoffKeywords() { return handoffKeywords; }

    public void setWelcome(String welcome) { this.welcome = welcome; }
    public String getWelcome() { return welcome; }

    public void setStatus(String status) { this.status = status; }
    public String getStatus() { return status; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("agentId", getAgentId())
            .append("agentName", getAgentName())
            .append("provider", getProvider())
            .append("categoryId", getCategoryId())
            .append("enableContext", getEnableContext())
            .append("contextRounds", getContextRounds())
            .append("status", getStatus())
            .append("createTime", getCreateTime())
            .toString();
    }
}
