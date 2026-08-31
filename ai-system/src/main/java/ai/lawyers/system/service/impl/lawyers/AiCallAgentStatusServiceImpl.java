package ai.lawyers.system.service.impl.lawyers;

import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.mapper.lawyers.AiCallAgentStatusMapper;
import ai.lawyers.system.domain.lawyers.trunk.DialRequest;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.service.lawyers.IAiAiCallSessionService;
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;
import ai.lawyers.system.service.lawyers.CallEventPublisher;
import ai.lawyers.system.service.lawyers.queue.StatusLogDispatcher;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;

@Service
public class AiCallAgentStatusServiceImpl implements IAiCallAgentStatusService
{
    private static final Logger log = LoggerFactory.getLogger(AiCallAgentStatusServiceImpl.class);

    @Autowired
    private AiCallAgentStatusMapper aiCallAgentStatusMapper;

    @Autowired
    private IAiCallRecordService aiCallRecordService;

    /**
     * AI律师辅助会话服务（独立链路）。
     * 此处仅为"人工接听"提供一个解耦的异步触发入口：
     * 人工接听建立通话后，异步启动 AI 辅助会话，二者状态管理互不影响。
     */
    @Autowired
    private IAiAiCallSessionService aiAiCallSessionService;

    /**
     * 运营商线路外呼调度器（新建链路）。
     * 人工坐席点击外呼时，将通话交由该调度器完成号码归属识别、线路选择、
     * 并发控制、排队与故障切换，最终下发到对应运营商 SIP/PSTN 网关。
     */
    @Autowired
    private ICallDispatchService callDispatchService;

    @Autowired(required = false)
    private CallEventPublisher callEventPublisher;

    /** T4-3 坐席状态流水（异步落库，Stream 不可用时内部同步降级） */
    @Autowired(required = false)
    private StatusLogDispatcher statusLogDispatcher;

    /** T4-3 记录一条状态变更流水（失败不影响主流程） */
    private void logStatus(String fromStatus, String fromCallStatus,
                           AiCallAgentStatus after, String eventType, Long recordId)
    {
        if (statusLogDispatcher == null || after == null)
        {
            return;
        }
        try
        {
            statusLogDispatcher.log(after.getAgentId(), after.getUserId(), eventType,
                    fromStatus, after.getStatus(), fromCallStatus, after.getCallStatus(),
                    recordId, new Date());
        }
        catch (Exception e)
        {
            log.warn("记录坐席状态流水失败 agentId={} event={}", after.getAgentId(), eventType);
        }
    }

    /** 推送坐席状态变更（推送失败不影响业务） */
    private void publishStatus(AiCallAgentStatus agent, String type)
    {
        if (callEventPublisher == null || agent == null || agent.getUserId() == null) return;
        try
        {
            callEventPublisher.publishToUser(agent.getUserId(), type, agent);
        }
        catch (Exception e)
        {
            log.warn("推送坐席状态事件失败: agentId={} type={}", agent.getAgentId(), type);
        }
    }

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
    public int agentLogin(Long agentId, Long userId, String ip)
    {
        AiCallAgentStatus agent = null;
        if (agentId != null)
        {
            agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        }
        else if (userId != null)
        {
            agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByUserId(userId);
        }
        if (agent != null) {
            // 工号已绑定其他账号时禁止签入，避免越权使用他人工号
            if (userId != null && agent.getUserId() != null && !agent.getUserId().equals(userId))
            {
                return -1;
            }
            String beforeStatus = agent.getStatus();
            String beforeCall = agent.getCallStatus();
            agent.setStatus("1");
            agent.setLoginTime(new Date());
            agent.setLogoutTime(null);
            agent.setLastLoginIp(ip);
            agent.setCallStatus("0");
            if (userId != null)
            {
                agent.setUserId(userId);
            }
            aiCallAgentStatusMapper.clearCurrentCall(agent.getAgentId());
            int rc = aiCallAgentStatusMapper.updateAiCallAgentStatus(agent) > 0 ? 1 : 0;
            if (rc > 0)
            {
                publishStatus(agent, "AGENT_LOGIN");
                logStatus(beforeStatus, beforeCall, agent, "LOGIN", null);
            }
            return rc;
        }
        return 0;
    }

    @Override
    public int agentLogout(Long agentId, Long userId)
    {
        AiCallAgentStatus agent = null;
        if (agentId != null)
        {
            agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        }
        else if (userId != null)
        {
            agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByUserId(userId);
        }
        if (agent != null) {
            String beforeStatus = agent.getStatus();
            String beforeCall = agent.getCallStatus();
            agent.setStatus("0");
            agent.setLogoutTime(new Date());
            agent.setCallStatus("0");
            aiCallAgentStatusMapper.clearCurrentCall(agent.getAgentId());
            int rc = aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
            if (rc > 0)
            {
                publishStatus(agent, "AGENT_LOGOUT");
                logStatus(beforeStatus, beforeCall, agent, "LOGOUT", null);
            }
            return rc;
        }
        return 0;
    }

    @Override
    public int updateAgentStatus(Long agentId, String status)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent != null) {
            String beforeStatus = agent.getStatus();
            String beforeCall = agent.getCallStatus();
            agent.setStatus(status);
            if ("0".equals(status)) {
                agent.setLogoutTime(new Date());
                agent.setCallStatus("0");
                aiCallAgentStatusMapper.clearCurrentCall(agent.getAgentId());
            } else if ("1".equals(status)) {
                agent.setLoginTime(new Date());
                agent.setLogoutTime(null);
            }
            int rc = aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
            if (rc > 0)
            {
                publishStatus(agent, "AGENT_STATUS");
                logStatus(beforeStatus, beforeCall, agent, "STATUS", null);
            }
            return rc;
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
        String beforeStatus = agent.getStatus();
        String beforeCall = agent.getCallStatus();
        // 创建外呼记录
        AiCallRecord record = new AiCallRecord();
        record.setCallerNumber(phone);
        record.setCallTime(new Date());
        record.setAgentId(agentId);
        record.setStatus("0");
        record.setCreateBy(agent.getAgentName());
        aiCallRecordService.insertAiCallRecord(record);

        // 更新座席当前通话状态（人工链路状态机，独立维护）
        agent.setCallStatus("1");
        agent.setCurrentCallId(record.getRecordId());
        agent.setCurrentCallPhone(phone);
        agent.setCallStartTime(new Date());
        agent.setStatus("2");
        aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
        publishStatus(agent, "CALL_START");
        logStatus(beforeStatus, beforeCall, agent, "MAKE_CALL", record.getRecordId());

        // 解耦触发：人工接听后异步启动 AI 辅助会话（独立链路，不影响人工状态机）
        triggerAiAssistAsync(record.getRecordId(), agentId, phone, agent.getAgentName());

        // 经运营商线路调度器下发外呼（号码归属识别 → 选路 → 并发控制 → 故障切换）
        dispatchOutbound(record, agent);

        return agent;
    }

    /**
     * 将人工外呼交由运营商线路调度器下发。
     * 调度器基于被叫号码前缀识别归属运营商，自动选择对应 SIP/PSTN 线路，
     * 并完成并发控制、排队与故障自动切换。下发结果仅影响人工链路状态机，
     * 不会反向阻塞或破坏 AI 辅助会话链路。
     */
    @Async
    public void dispatchOutbound(AiCallRecord record, AiCallAgentStatus agent)
    {
        try
        {
            DialRequest req = new DialRequest();
            req.setCalleeNumber(agent.getCurrentCallPhone());
            req.setAgentId(agent.getAgentId());
            req.setRecordId(record.getRecordId());
            req.setCallerNumber(record.getCallerNumber());
            DialResult result = callDispatchService.dialWithQueue(req);
            if (result == null || !result.isSuccess())
            {
                // 无可用线路或下发失败：回滚人工链路状态机，避免座席"假忙"
                rollbackAfterDispatchFail(agent, record,
                        result == null ? "null" : result.getMessage());
            }
        }
        catch (Exception e)
        {
            log.error("[makeCall] 运营商线路下发异常 recordId={}", record.getRecordId(), e);
            rollbackAfterDispatchFail(agent, record, e.getMessage());
        }
    }

    /** 外呼下发失败/异常：回滚座席状态机并记录回滚流水 */
    private void rollbackAfterDispatchFail(AiCallAgentStatus agent, AiCallRecord record, String reason)
    {
        String beforeStatus = agent.getStatus();
        String beforeCall = agent.getCallStatus();
        agent.setCallStatus("0");
        agent.setCurrentCallId(null);
        agent.setCurrentCallPhone(null);
        agent.setCallStartTime(null);
        agent.setStatus("1");
        aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
        logStatus(beforeStatus, beforeCall, agent, "DISPATCH_FAIL_ROLLBACK",
                record != null ? record.getRecordId() : null);
        log.warn("[makeCall] 运营商线路下发失败，已回滚座席状态 recordId={} reason={}",
                record != null ? record.getRecordId() : null, reason);
    }

    /**
     * 独立触发 AI 辅助会话（异步，失败不影响人工通话）。
     * 这是人工链路与 AI 辅助链路之间唯一的"触发边界"，通过异步解耦，
     * AI 辅助会话后续的状态流转完全在 AiAiCallSessionServiceImpl 内部独立管理。
     */
    @Async
    public void triggerAiAssistAsync(Long recordId, Long agentId, String phone, String agentName)
    {
        try
        {
            aiAiCallSessionService.startAssistSession(recordId, agentId, phone, agentName);
        }
        catch (Exception ignored)
        {
            // AI辅助启动失败绝不影响人工通话
        }
    }

    @Override
    public int holdCall(Long agentId)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent == null || !"1".equals(agent.getCallStatus())) {
            return 0;
        }
        String beforeStatus = agent.getStatus();
        String beforeCall = agent.getCallStatus();
        agent.setCallStatus("2");
        int rc = aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
        if (rc > 0) logStatus(beforeStatus, beforeCall, agent, "HOLD", agent.getCurrentCallId());
        return rc;
    }

    @Override
    public int resumeCall(Long agentId)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent == null || !"2".equals(agent.getCallStatus())) {
            return 0;
        }
        String beforeStatus = agent.getStatus();
        String beforeCall = agent.getCallStatus();
        agent.setCallStatus("1");
        int rc = aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
        if (rc > 0) logStatus(beforeStatus, beforeCall, agent, "RESUME", agent.getCurrentCallId());
        return rc;
    }

    @Override
    public int transferCall(Long agentId, Long toAgentId, String remark)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent == null || agent.getCurrentCallId() == null) {
            return 0;
        }
        String beforeStatus = agent.getStatus();
        String beforeCall = agent.getCallStatus();
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
        agent.setStatus("1");
        if (remark != null) {
            agent.setRemark("转接给座席[" + toAgentId + "]：" + remark);
        }
        aiCallAgentStatusMapper.clearCurrentCall(agentId);
        int rc = aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
        if (rc > 0) logStatus(beforeStatus, beforeCall, agent, "TRANSFER", agent.getCurrentCallId());
        return rc;
    }

    @Override
    public int consultCall(Long agentId, Long toAgentId)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent == null || !"1".equals(agent.getCallStatus())) {
            return 0;
        }
        String beforeStatus = agent.getStatus();
        String beforeCall = agent.getCallStatus();
        agent.setCallStatus("3");
        agent.setRemark("咨询座席[" + toAgentId + "]");
        int rc = aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
        if (rc > 0) logStatus(beforeStatus, beforeCall, agent, "CONSULT", agent.getCurrentCallId());
        return rc;
    }

    @Override
    public int threeWayCall(Long agentId, Long toAgentId)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent == null || !"1".equals(agent.getCallStatus())) {
            return 0;
        }
        String beforeStatus = agent.getStatus();
        String beforeCall = agent.getCallStatus();
        agent.setCallStatus("4");
        agent.setRemark("三方通话加入座席[" + toAgentId + "]");
        int rc = aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
        if (rc > 0) logStatus(beforeStatus, beforeCall, agent, "THREE_WAY", agent.getCurrentCallId());
        return rc;
    }

    @Override
    public int afterWork(Long agentId)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent == null) {
            return 0;
        }
        String beforeStatus = agent.getStatus();
        String beforeCall = agent.getCallStatus();
        agent.setCallStatus("5");
        agent.setStatus("2");
        int rc = aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
        if (rc > 0) logStatus(beforeStatus, beforeCall, agent, "AFTER_WORK", agent.getCurrentCallId());
        return rc;
    }

    @Override
    public int hangup(Long agentId)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent == null) {
            return 0;
        }
        String beforeStatus = agent.getStatus();
        String beforeCall = agent.getCallStatus();
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
        Long recordId = agent.getCurrentCallId();
        agent.setCallStatus("0");
        agent.setStatus("1");
        aiCallAgentStatusMapper.clearCurrentCall(agentId);
        int rc = aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
        if (rc > 0)
        {
            publishStatus(agent, "CALL_END");
            logStatus(beforeStatus, beforeCall, agent, "HANGUP", recordId);
        }
        return rc;
    }

    @Override
    public int robotTakeover(Long agentId)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent == null || agent.getCurrentCallId() == null) {
            return 0;
        }
        String beforeStatus = agent.getStatus();
        String beforeCall = agent.getCallStatus();
        // 机器人接管：在当前通话记录备注，并释放座席
        AiCallRecord record = aiCallRecordService.selectAiCallRecordByRecordId(agent.getCurrentCallId());
        if (record != null) {
            String oldRemark = record.getRemark() == null ? "" : record.getRemark();
            record.setRemark(oldRemark + "；座席[" + agentId + "]已转交机器人接管");
            aiCallRecordService.updateAiCallRecord(record);
        }
        Long recordId = agent.getCurrentCallId();
        agent.setCallStatus("0");
        agent.setStatus("1");
        aiCallAgentStatusMapper.clearCurrentCall(agentId);
        int rc = aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
        if (rc > 0) logStatus(beforeStatus, beforeCall, agent, "ROBOT_TAKEOVER", recordId);
        return rc;
    }

    @Override
    public int ivrTransfer(Long agentId, String ivrNodeId)
    {
        AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent == null || agent.getCurrentCallId() == null) {
            return 0;
        }
        String beforeStatus = agent.getStatus();
        String beforeCall = agent.getCallStatus();
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
        Long recordId = agent.getCurrentCallId();
        agent.setCallStatus("0");
        agent.setStatus("1");
        aiCallAgentStatusMapper.clearCurrentCall(agentId);
        int rc = aiCallAgentStatusMapper.updateAiCallAgentStatus(agent);
        if (rc > 0) logStatus(beforeStatus, beforeCall, agent, "IVR_TRANSFER", recordId);
        return rc;
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

    @Override
    public void syncAgentFromUser(Long userId, Long agentId, String agentName,
                                   String sipExtension, String callMode, String operator)
    {
        if (userId == null) return;

        // 1. 先查询该用户之前绑定的坐席记录
        AiCallAgentStatus oldBinding = aiCallAgentStatusMapper.selectAiCallAgentStatusByUserId(userId);
        Long oldAgentId = oldBinding != null ? oldBinding.getAgentId() : null;

        if (agentId == null)
        {
            // 用户未配置坐席工号：清除旧绑定（不删除运行记录）
            if (oldAgentId != null)
            {
                aiCallAgentStatusMapper.releaseUserIdByAgentId(oldAgentId);
            }
            return;
        }

        // 工号未变化：仅更新配置
        if (agentId.equals(oldAgentId) && oldBinding != null)
        {
            if (agentName != null && !agentName.isEmpty())
            {
                oldBinding.setAgentName(agentName);
            }
            oldBinding.setSipExtension(sipExtension);
            oldBinding.setCallMode(callMode != null ? callMode : "0");
            oldBinding.setUpdateBy(operator);
            aiCallAgentStatusMapper.updateAiCallAgentStatus(oldBinding);
            return;
        }

        // 2. 换绑到新工号：先解绑旧工号
        if (oldAgentId != null)
        {
            aiCallAgentStatusMapper.releaseUserIdByAgentId(oldAgentId);
        }

        // 3. 查询目标工号是否已存在
        AiCallAgentStatus target = aiCallAgentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);

        if (target != null)
        {
            // 工号已存在：检查是否绑定了其他用户
            if (target.getUserId() != null && !target.getUserId().equals(userId))
            {
                throw new ai.lawyers.common.exception.ServiceException(
                    "坐席工号 " + agentId + " 已绑定其他用户，无法重复绑定");
            }
            // 更新绑定关系和配置（不覆盖运行时状态字段）
            target.setUserId(userId);
            if (agentName != null && !agentName.isEmpty())
            {
                target.setAgentName(agentName);
            }
            target.setSipExtension(sipExtension);
            target.setCallMode(callMode != null ? callMode : "0");
            target.setUpdateBy(operator);
            aiCallAgentStatusMapper.updateAiCallAgentStatus(target);
        }
        else
        {
            // 工号不存在：新建坐席状态记录
            AiCallAgentStatus newAgent = new AiCallAgentStatus();
            newAgent.setAgentId(agentId);
            newAgent.setUserId(userId);
            newAgent.setAgentName(agentName != null && !agentName.isEmpty() ? agentName : ("坐席" + agentId));
            newAgent.setSipExtension(sipExtension);
            newAgent.setCallMode(callMode != null ? callMode : "0");
            newAgent.setStatus("0");
            newAgent.setCallStatus("0");
            newAgent.setCreateBy(operator);
            newAgent.setRemark("通过用户管理创建");
            aiCallAgentStatusMapper.insertAiCallAgentStatus(newAgent);
        }
    }

    @Override
    public void releaseAgentByUserId(Long userId)
    {
        if (userId == null) return;
        aiCallAgentStatusMapper.releaseUserIdByUserId(userId);
    }
}
