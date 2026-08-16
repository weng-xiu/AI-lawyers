package ai.lawyers.system.mapper.lawyers.skill;

import java.util.List;
import ai.lawyers.system.domain.lawyers.skill.AiSkillGroup;
import ai.lawyers.system.domain.lawyers.skill.AiSkillGroupMember;

/**
 * 技能组Mapper接口
 *
 * @author ai-lawyers
 */
public interface AiSkillGroupMapper
{
    public AiSkillGroup selectAiSkillGroupByGroupId(Long groupId);

    public List<AiSkillGroup> selectAiSkillGroupList(AiSkillGroup aiSkillGroup);

    /**
     * 按咨询分类查询启用的技能组（供 B1 agentCategoryId 自动映射）
     */
    public AiSkillGroup selectByCategoryId(Long categoryId);

    public int insertAiSkillGroup(AiSkillGroup aiSkillGroup);

    public int updateAiSkillGroup(AiSkillGroup aiSkillGroup);

    public int deleteAiSkillGroupByGroupId(Long groupId);

    public int deleteAiSkillGroupByGroupIds(Long[] groupIds);

    // ---- 成员管理 ----
    public List<AiSkillGroupMember> selectMembersByGroupId(Long groupId);

    public AiSkillGroupMember selectMemberByGroupAndAgent(@org.apache.ibatis.annotations.Param("groupId") Long groupId,
                                                          @org.apache.ibatis.annotations.Param("agentId") Long agentId);

    public int batchInsertMembers(List<AiSkillGroupMember> members);

    public int deleteMembersByGroupIds(Long[] groupIds);

    public int deleteMembersByGroupAndAgent(@org.apache.ibatis.annotations.Param("groupId") Long groupId,
                                            @org.apache.ibatis.annotations.Param("agentIds") Long[] agentIds);
}
