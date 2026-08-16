package ai.lawyers.system.service.lawyers.agent;

import ai.lawyers.system.domain.lawyers.agent.AgentChatResult;

/**
 * 智能体对话统一服务（按 provider 路由）
 *
 * @author ai-lawyers
 */
public interface IAgentChatService
{
    /**
     * 与智能体进行一轮对话
     *
     * @param agentId      智能体ID
     * @param sessionId    会话ID（同一来电/IVR会话保持一致）
     * @param userMessage  用户本轮输入
     * @param recordId     关联通话记录ID（可空）
     * @param flowId       流程ID（可空）
     * @param nodeId       节点ID（可空）
     * @param callerNumber 主叫号码（可空）
     * @return 对话结果
     */
    AgentChatResult chat(Long agentId, String sessionId, String userMessage,
                         Long recordId, Long flowId, Long nodeId, String callerNumber);
}
