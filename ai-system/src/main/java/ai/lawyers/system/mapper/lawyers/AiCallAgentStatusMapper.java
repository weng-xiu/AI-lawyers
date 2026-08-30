package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;

public interface AiCallAgentStatusMapper
{
    /**
     * 原子抢占空闲坐席：仅当通话状态为空闲(call_status='0')时置为通话中('1')
     *
     * @param agentId 坐席ID
     * @return 影响行数：1=抢占成功；0=坐席不存在或已被并发来电抢占
     */
    public int occupyAgentIfFree(@Param("agentId") Long agentId);

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
