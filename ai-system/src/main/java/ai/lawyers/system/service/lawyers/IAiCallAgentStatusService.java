package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;

public interface IAiCallAgentStatusService 
{
    public AiCallAgentStatus selectAiCallAgentStatusByAgentId(Long agentId);

    public AiCallAgentStatus selectAiCallAgentStatusByUserId(Long userId);

    public List<AiCallAgentStatus> selectAiCallAgentStatusList(AiCallAgentStatus aiCallAgentStatus);

    public int insertAiCallAgentStatus(AiCallAgentStatus aiCallAgentStatus);

    public int updateAiCallAgentStatus(AiCallAgentStatus aiCallAgentStatus);

    public int deleteAiCallAgentStatusByAgentId(Long agentId);

    public int deleteAiCallAgentStatusByAgentIds(Long[] agentIds);

    public List<AiCallAgentStatus> selectOnlineAgents();

    public int agentLogin(Long userId, String ip);

    public int agentLogout(Long userId);

    public int updateAgentStatus(Long agentId, String status);

    public AiCallAgentStatus makeCall(Long agentId, String phone);

    public int holdCall(Long agentId);

    public int resumeCall(Long agentId);

    public int transferCall(Long agentId, Long toAgentId, String remark);

    public int consultCall(Long agentId, Long toAgentId);

    public int threeWayCall(Long agentId, Long toAgentId);

    public int afterWork(Long agentId);

    public int hangup(Long agentId);

    public int robotTakeover(Long agentId);

    public int ivrTransfer(Long agentId, String ivrNodeId);

    public int updateCallMode(Long agentId, String callMode);

    public java.util.List<java.util.Map<String, Object>> selectTodayRecordsByAgentId(Long agentId);
}
