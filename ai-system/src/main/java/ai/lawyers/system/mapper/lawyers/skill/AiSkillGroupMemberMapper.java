package ai.lawyers.system.mapper.lawyers.skill;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.skill.AiSkillGroupMember;
import ai.lawyers.system.domain.lawyers.skill.AgentLoadStat;

/**
 * 技能组成员Mapper接口
 *
 * @author ai-lawyers
 */
public interface AiSkillGroupMemberMapper
{
    public AiSkillGroupMember selectById(Long id);

    public List<AiSkillGroupMember> selectMemberList(AiSkillGroupMember member);

    /**
     * 查询某技能组下可用（成员启用 + 坐席在线空闲、非话后整理）的坐席，按技能等级、优先级排序
     */
    public List<AiSkillGroupMember> selectAvailableByGroupId(Long groupId);

    /**
     * 查询组内某坐席当日已完成通话数（least_calls 策略用）
     */
    public Integer countTodayCompletedByAgent(@Param("agentId") Long agentId);

    /**
     * 查询组内坐席最近一次通话开始时间（least_recent 策略用，越早越优先）
     */
    public java.util.Date selectLastCallStartTime(@Param("agentId") Long agentId);

    /**
     * T1-5 批量聚合：一次性返回候选坐席的「最近通话开始时间 + 今日完成通话数」，
     * 消除 least_recent/least_calls 排序时逐坐席查询的 N+1 问题。
     *
     * @param agentIds 候选坐席ID列表（非空）
     */
    public List<AgentLoadStat> selectLoadStatsByAgentIds(@Param("agentIds") List<Long> agentIds);

    public int insertMember(AiSkillGroupMember member);

    public int updateMember(AiSkillGroupMember member);

    public int deleteByIds(Long[] ids);
}
