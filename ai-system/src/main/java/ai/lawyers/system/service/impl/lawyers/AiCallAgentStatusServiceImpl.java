package ai.lawyers.system.service.impl.lawyers;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.mapper.lawyers.AiCallAgentStatusMapper;
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;

@Service
public class AiCallAgentStatusServiceImpl implements IAiCallAgentStatusService 
{
    @Autowired
    private AiCallAgentStatusMapper aiCallAgentStatusMapper;

    @Override
    public AiCallAgentStatus selectAiCallAgentStatusByAgentId(Long agentId)
    {
        return aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
    }

    @Override
    public AiCallAgentStatus selectAiCallAgentStatusByUserId(Long userId)
    {
        return aiCallAgentStatusMapper.selectAiCallAgentStatusByUserId(userId);
    }

    @Override
    public List<AiCallAgentStatus> selectAiCallAgentStatusList(AiCallAgentStatus aiCallAgentStatus)
    {
        return aiCallAgentStatusMapper.selectAiCallAgentStatusList(aiCallAgentStatus);
    }

    @Override
    public int insertAiCallAgentStatus(AiCallAgentStatus aiCallAgentStatus)
    {
        return aiCallAgentStatusMapper.insertAiCallAgentStatus(aiCallAgentStatus);
    }

    @Override
    public int updateAiCallAgentStatus(AiCallAgentStatus aiCallAgentStatus)
    {
        return aiCallAgentStatusMapper.updateAiCallAgentStatus(aiCallAgentStatus);
    }

    @Override
    public int deleteAiCallAgentStatusByAgentId(Long agentId)
    {
        return aiCallAgentStatusMapper.deleteAiCallAgentStatusByAgentId(agentId);
    }

    @Override
    public int deleteAiCallAgentStatusByAgentIds(Long[] agentIds)
    {
        return aiCallAgentStatusMapper.deleteAiCallAgentStatusByAgentIds(agentIds);
    }

    @Override
    public List<AiCallAgentStatus> selectOnlineAgents()
    {
        return aiCallAgentStatusMapper.selectOnlineAgents();
    }

    @Override
    public int agentLogin(Long userId, String ip)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByUserId(userId);
        if (agent != null) {
            agent.setStatus("1");
            agent.setLoginTime(new Date());
            agent.setLogoutTime(null);
            agent.setLastLoginIp(ip);
            return aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
        }
        return 0;
    }

    @Override
    public int agentLogout(Long userId)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByUserId(userId);
        if (agent != null) {
            agent.setStatus("0");
            agent.setLogoutTime(new Date());
            return aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
        }
        return 0;
    }

    @Override
    public int updateAgentStatus(Long agentId, String status)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent != null) {
            agent.setStatus(status);
            if ("0".equals(status)) {
                agent.setLogoutTime(new Date());
            } else if ("1".equals(status)) {
                agent.setLoginTime(new Date());
                agent.setLogoutTime(null);
            }
            return aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
        }
        return 0;
    }
}
