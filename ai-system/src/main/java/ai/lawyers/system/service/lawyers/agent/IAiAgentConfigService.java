package ai.lawyers.system.service.lawyers.agent;

import java.util.List;
import ai.lawyers.system.domain.lawyers.agent.AiAgentConfig;

/**
 * AI智能体配置Service接口
 *
 * @author ai-lawyers
 */
public interface IAiAgentConfigService
{
    public AiAgentConfig selectAiAgentConfigByAgentId(Long agentId);

    public List<AiAgentConfig> selectAiAgentConfigList(AiAgentConfig aiAgentConfig);

    public List<AiAgentConfig> selectActiveAgentConfigs();

    public int insertAiAgentConfig(AiAgentConfig aiAgentConfig);

    public int updateAiAgentConfig(AiAgentConfig aiAgentConfig);

    public int deleteAiAgentConfigByAgentIds(Long[] agentIds);

    public int deleteAiAgentConfigByAgentId(Long agentId);

    /**
     * 测试智能体连通性（local 测试默认大模型；外部平台测试接口地址）
     *
     * @param agentId 智能体ID
     * @return 连通正常返回 true
     */
    public boolean testConnection(Long agentId);
}
