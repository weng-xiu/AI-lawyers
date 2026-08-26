package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;

public interface AiCallAgentStatusMapper
{
    public AiCallAgentStatus selectAiCallAgentStatusByAgentId(Long agentId);

    public AiCallAgentStatus selectAiCallAgentStatusByUserId(Long userId);

    public List<AiCallAgentStatus> selectAiCallAgentStatusList(AiCallAgentStatus aiCallAgentStatus);

    public int insertAiCallAgentStatus(AiCallAgentStatus aiCallAgentStatus);

    public int updateAiCallAgentStatus(AiCallAgentStatus aiCallAgentStatus);

    public int clearCurrentCall(Long agentId);

    public int deleteAiCallAgentStatusByAgentId(Long agentId);

    public int deleteAiCallAgentStatusByAgentIds(Long[] agentIds);

    /**
     * 清除指定用户的坐席绑定（将 user_id 置空，不删除运行记录）。
     */
    public int releaseUserIdByUserId(Long userId);

    /**
     * 清除指定工号的用户绑定（换绑时用于解绑旧工号）。
     */
    public int releaseUserIdByAgentId(Long agentId);

    public List<AiCallAgentStatus> selectOnlineAgents();

    public List<java.util.Map<String, Object>> selectTodayRecordsByAgent(Long agentId);
}
