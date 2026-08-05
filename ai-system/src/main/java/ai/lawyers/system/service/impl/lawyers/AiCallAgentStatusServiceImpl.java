package ai.lawyers.system.service.impl.lawyers;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.mapper.lawyers.AiCallAgentStatusMapper;
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;

@Service
public class AiCallAgentStatusServiceImpl implements IAiCallAgentStatusService
{
    @Autowired
    private AiCallAgentStatusMapper aiCallAgentStatusMapper;

    @Autowired
    private IAiCallRecordService aiCallRecordService;

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
            agent.setCallStatus("0");
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
            agent.setCallStatus("0");
            agent.setCurrentCallId(null);
            agent.setCurrentCallPhone(null);
            agent.setCallStartTime(null);
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
                agent.setCallStatus("0");
                agent.setCurrentCallId(null);
                agent.setCurrentCallPhone(null);
                agent.setCallStartTime(null);
            } else if ("1".equals(status)) {
                agent.setLoginTime(new Date());
                agent.setLogoutTime(null);
            }
            return aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
        }
        return 0;
    }

    @Override
    public AiCallAgentStatus makeCall(Long agentId, String phone)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent == null) {
            return null;
        }
        // 创建外呼记录
        AiCallRecord record = new AiCallRecord();
        record.setCallerNumber(phone);
        record.setCallTime(new Date());
        record.setAgentId(agentId);
        record.setStatus("0");
        record.setCreateBy(agent.getAgentName());
        aiCallRecordService.insertAiCallRecord(record);

        // 更新座席当前通话状态
        agent.setCallStatus("1");
        agent.setCurrentCallId(record.getRecordId());
        agent.setCurrentCallPhone(phone);
        agent.setCallStartTime(new Date());
        agent.setStatus("2");
        aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
        return agent;
    }

    @Override
    public int holdCall(Long agentId)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent == null || !"1".equals(agent.getCallStatus())) {
            return 0;
        }
        agent.setCallStatus("2");
        return aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
    }

    @Override
    public int resumeCall(Long agentId)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent == null || !"2".equals(agent.getCallStatus())) {
            return 0;
        }
        agent.setCallStatus("1");
        return aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
    }

    @Override
    public int transferCall(Long agentId, Long toAgentId, String remark)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent == null || agent.getCurrentCallId() == null) {
            return 0;
        }
        // 更新当前通话记录状态为已转接
        AiCallRecord record = aiCallRecordService.selectAiCallRecordByRecordId(agent.getCurrentCallId());
        if (record != null) {
            record.setStatus("2");
            record.setEndTime(new Date());
            if (record.getDuration() == null && record.getCallTime() != null) {
                record.setDuration((int) ((System.currentTimeMillis() - record.getCallTime().getTime()) / 1000));
            }
            aiCallRecordService.updateAiCallRecord(record);
        }
        // 重置座席状态
        agent.setCallStatus("0");
        agent.setCurrentCallId(null);
        agent.setCurrentCallPhone(null);
        agent.setCallStartTime(null);
        agent.setStatus("1");
        if (remark != null) {
            agent.setRemark("转接给座席[" + toAgentId + "]：" + remark);
        }
        return aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
    }

    @Override
    public int consultCall(Long agentId, Long toAgentId)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent == null || !"1".equals(agent.getCallStatus())) {
            return 0;
        }
        agent.setCallStatus("3");
        agent.setRemark("咨询座席[" + toAgentId + "]");
        return aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
    }

    @Override
    public int threeWayCall(Long agentId, Long toAgentId)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent == null || !"1".equals(agent.getCallStatus())) {
            return 0;
        }
        agent.setCallStatus("4");
        agent.setRemark("三方通话加入座席[" + toAgentId + "]");
        return aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
    }

    @Override
    public int afterWork(Long agentId)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent == null) {
            return 0;
        }
        agent.setCallStatus("5");
        agent.setStatus("2");
        return aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
    }

    @Override
    public int hangup(Long agentId)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent == null) {
            return 0;
        }
        // 结束当前通话记录
        if (agent.getCurrentCallId() != null) {
            AiCallRecord record = aiCallRecordService.selectAiCallRecordByRecordId(agent.getCurrentCallId());
            if (record != null) {
                record.setStatus("1");
                record.setEndTime(new Date());
                if (record.getCallTime() != null) {
                    record.setDuration((int) ((System.currentTimeMillis() - record.getCallTime().getTime()) / 1000));
                }
                aiCallRecordService.updateAiCallRecord(record);
            }
        }
        agent.setCallStatus("0");
        agent.setCurrentCallId(null);
        agent.setCurrentCallPhone(null);
        agent.setCallStartTime(null);
        agent.setStatus("1");
        return aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
    }

    @Override
    public int robotTakeover(Long agentId)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent == null || agent.getCurrentCallId() == null) {
            return 0;
        }
        // 机器人接管：在当前通话记录备注，并释放座席
        AiCallRecord record = aiCallRecordService.selectAiCallRecordByRecordId(agent.getCurrentCallId());
        if (record != null) {
            String oldRemark = record.getRemark() == null ? "" : record.getRemark();
            record.setRemark(oldRemark + "；座席[" + agentId + "]已转交机器人接管");
            aiCallRecordService.updateAiCallRecord(record);
        }
        agent.setCallStatus("0");
        agent.setCurrentCallId(null);
        agent.setCurrentCallPhone(null);
        agent.setCallStartTime(null);
        agent.setStatus("1");
        return aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
    }

    @Override
    public int ivrTransfer(Long agentId, String ivrNodeId)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent == null || agent.getCurrentCallId() == null) {
            return 0;
        }
        AiCallRecord record = aiCallRecordService.selectAiCallRecordByRecordId(agent.getCurrentCallId());
        if (record != null) {
            record.setStatus("2");
            record.setEndTime(new Date());
            if (record.getCallTime() != null) {
                record.setDuration((int) ((System.currentTimeMillis() - record.getCallTime().getTime()) / 1000));
            }
            String oldRemark = record.getRemark() == null ? "" : record.getRemark();
            record.setRemark(oldRemark + "；转IVR节点[" + ivrNodeId + "]");
            aiCallRecordService.updateAiCallRecord(record);
        }
        agent.setCallStatus("0");
        agent.setCurrentCallId(null);
        agent.setCurrentCallPhone(null);
        agent.setCallStartTime(null);
        agent.setStatus("1");
        return aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
    }

    @Override
    public int updateCallMode(Long agentId, String callMode)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent == null) {
            return 0;
        }
        agent.setCallMode(callMode);
        return aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
    }

    @Override
    public List<java.util.Map<String, Object>> selectTodayRecordsByAgentId(Long agentId)
    {
        return aiCallAgentStatusMapper.selectTodayRecordsByAgent(agentId);
    }
}
