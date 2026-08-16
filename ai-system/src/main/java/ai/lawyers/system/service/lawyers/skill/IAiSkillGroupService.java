package ai.lawyers.system.service.lawyers.skill;

import java.util.List;
import ai.lawyers.system.domain.lawyers.skill.AiSkillGroup;
import ai.lawyers.system.domain.lawyers.skill.AiSkillGroupMember;

/**
 * 技能组Service接口
 *
 * @author ai-lawyers
 */
public interface IAiSkillGroupService
{
    public AiSkillGroup selectAiSkillGroupByGroupId(Long groupId);

    public List<AiSkillGroup> selectAiSkillGroupList(AiSkillGroup aiSkillGroup);

    /**
     * 查询所有启用的技能组（供下拉选择）
     */
    public List<AiSkillGroup> selectEnabledGroups();

    /**
     * 按咨询分类查技能组（B1 agentCategoryId 自动映射）
     */
    public AiSkillGroup selectByCategoryId(Long categoryId);

    public int insertAiSkillGroup(AiSkillGroup aiSkillGroup);

    public int updateAiSkillGroup(AiSkillGroup aiSkillGroup);

    public int deleteAiSkillGroupByGroupIds(Long[] groupIds);

    // ---- 成员管理 ----
    public List<AiSkillGroupMember> selectMembersByGroupId(Long groupId);

    /**
     * 批量添加成员（自动跳过已存在的组成关系）
     */
    public int addMembers(Long groupId, Long[] agentIds, Integer skillLevel);

    public int removeMembers(Long groupId, Long[] agentIds);

    public int updateMember(AiSkillGroupMember member);
}
