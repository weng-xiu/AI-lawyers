package ai.lawyers.system.mapper.lawyers.agent;

import java.util.List;
import ai.lawyers.system.domain.lawyers.agent.AiAgentConfig;

/**
 * AI智能体配置Mapper接口
 *
 * @author ai-lawyers
 */
public interface AiAgentConfigMapper
{
    public AiAgentConfig selectAiAgentConfigByAgentId(Long agentId);

    public List<AiAgentConfig> selectAiAgentConfigList(AiAgentConfig aiAgentConfig);

    public List<AiAgentConfig> selectActiveAgentConfigs();

    public int insertAiAgentConfig(AiAgentConfig aiAgentConfig);

    public int updateAiAgentConfig(AiAgentConfig aiAgentConfig);

    public int deleteAiAgentConfigByAgentId(Long agentId);

    public int deleteAiAgentConfigByAgentIds(Long[] agentIds);
}
