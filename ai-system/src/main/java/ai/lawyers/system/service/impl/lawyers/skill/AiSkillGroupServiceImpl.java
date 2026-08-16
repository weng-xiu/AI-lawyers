package ai.lawyers.system.service.impl.lawyers.skill;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.skill.AiSkillGroup;
import ai.lawyers.system.domain.lawyers.skill.AiSkillGroupMember;
import ai.lawyers.system.mapper.lawyers.skill.AiSkillGroupMapper;
import ai.lawyers.system.mapper.lawyers.skill.AiSkillGroupMemberMapper;
import ai.lawyers.system.service.lawyers.skill.IAiSkillGroupService;

/**
 * 技能组Service实现
 *
 * @author ai-lawyers
 */
@Service
public class AiSkillGroupServiceImpl implements IAiSkillGroupService
{
    @Autowired
    private AiSkillGroupMapper aiSkillGroupMapper;

    @Autowired
    private AiSkillGroupMemberMapper aiSkillGroupMemberMapper;

    @Override
    public AiSkillGroup selectAiSkillGroupByGroupId(Long groupId)
    {
        return aiSkillGroupMapper.selectAiSkillGroupByGroupId(groupId);
    }

    @Override
    public List<AiSkillGroup> selectAiSkillGroupList(AiSkillGroup aiSkillGroup)
    {
        return aiSkillGroupMapper.selectAiSkillGroupList(aiSkillGroup);
    }

    @Override
    public List<AiSkillGroup> selectEnabledGroups()
    {
        AiSkillGroup q = new AiSkillGroup();
        q.setStatus("1");
        return aiSkillGroupMapper.selectAiSkillGroupList(q);
    }

    @Override
    public AiSkillGroup selectByCategoryId(Long categoryId)
    {
        if (categoryId == null)
        {
            return null;
        }
        return aiSkillGroupMapper.selectByCategoryId(categoryId);
    }

    @Override
    public int insertAiSkillGroup(AiSkillGroup aiSkillGroup)
    {
        if (StringUtils.isEmpty(aiSkillGroup.getStatus()))
        {
            aiSkillGroup.setStatus("1");
        }
        if (StringUtils.isEmpty(aiSkillGroup.getStrategy()))
        {
            aiSkillGroup.setStrategy("round_robin");
        }
        return aiSkillGroupMapper.insertAiSkillGroup(aiSkillGroup);
    }

    @Override
    public int updateAiSkillGroup(AiSkillGroup aiSkillGroup)
    {
        return aiSkillGroupMapper.updateAiSkillGroup(aiSkillGroup);
    }

    @Override
    @Transactional
    public int deleteAiSkillGroupByGroupIds(Long[] groupIds)
    {
        // 同步删除组成员
        aiSkillGroupMapper.deleteMembersByGroupIds(groupIds);
        return aiSkillGroupMapper.deleteAiSkillGroupByGroupIds(groupIds);
    }

    @Override
    public List<AiSkillGroupMember> selectMembersByGroupId(Long groupId)
    {
        return aiSkillGroupMapper.selectMembersByGroupId(groupId);
    }

    @Override
    @Transactional
    public int addMembers(Long groupId, Long[] agentIds, Integer skillLevel)
    {
        if (agentIds == null || agentIds.length == 0)
        {
            return 0;
        }
        int level = skillLevel == null ? 3 : skillLevel;
        List<AiSkillGroupMember> toInsert = new ArrayList<>();
        for (Long agentId : agentIds)
        {
            // 跳过已存在关系
            AiSkillGroupMember exist = aiSkillGroupMapper.selectMemberByGroupAndAgent(groupId, agentId);
            if (exist != null)
            {
                continue;
            }
            AiSkillGroupMember m = new AiSkillGroupMember();
            m.setGroupId(groupId);
            m.setAgentId(agentId);
            m.setSkillLevel(level);
            m.setPriority(0);
            m.setMaxConcurrent(1);
            m.setStatus("1");
            toInsert.add(m);
        }
        if (toInsert.isEmpty())
        {
            return 0;
        }
        return aiSkillGroupMapper.batchInsertMembers(toInsert);
    }

    @Override
    public int removeMembers(Long groupId, Long[] agentIds)
    {
        if (agentIds == null || agentIds.length == 0)
        {
            return 0;
        }
        return aiSkillGroupMapper.deleteMembersByGroupAndAgent(groupId, agentIds);
    }

    @Override
    public int updateMember(AiSkillGroupMember member)
    {
        return aiSkillGroupMemberMapper.updateMember(member);
    }
}
