package ai.lawyers.system.service.impl.lawyers.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.skill.AiSkillGroup;
import ai.lawyers.system.domain.lawyers.skill.AiSkillGroupMember;
import ai.lawyers.system.mapper.lawyers.skill.AiSkillGroupMapper;
import ai.lawyers.system.mapper.lawyers.skill.AiSkillGroupMemberMapper;

/**
 * 技能组 Service 单测。
 *
 * <p>覆盖启用组查询、新增默认策略、级联删除成员、批量加成员（跳过已存在、
 * 默认技能等级/优先级/并发数）与成员维护透传。</p>
 *
 * @author ai-lawyers
 */
class AiSkillGroupServiceImplTest
{
    private AiSkillGroupServiceImpl service;

    @Mock private AiSkillGroupMapper groupMapper;
    @Mock private AiSkillGroupMemberMapper memberMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiSkillGroupServiceImpl();
        ReflectionTestUtils.setField(service, "aiSkillGroupMapper", groupMapper);
        ReflectionTestUtils.setField(service, "aiSkillGroupMemberMapper", memberMapper);
    }

    // ---------------- 查询 ----------------

    @Test
    void selectQueries_delegate()
    {
        AiSkillGroup group = new AiSkillGroup();
        List<AiSkillGroup> list = Collections.singletonList(group);
        when(groupMapper.selectAiSkillGroupByGroupId(1L)).thenReturn(group);
        when(groupMapper.selectAiSkillGroupList(any())).thenReturn(list);

        assertSame(group, service.selectAiSkillGroupByGroupId(1L));
        assertEquals(list, service.selectAiSkillGroupList(new AiSkillGroup()));
    }

    @Test
    void selectEnabledGroups_queriesEnabledStatus()
    {
        when(groupMapper.selectAiSkillGroupList(any())).thenReturn(Collections.emptyList());

        service.selectEnabledGroups();

        ArgumentCaptor<AiSkillGroup> captor = ArgumentCaptor.forClass(AiSkillGroup.class);
        verify(groupMapper).selectAiSkillGroupList(captor.capture());
        assertEquals("1", captor.getValue().getStatus());
    }

    @Test
    void selectByCategoryId_null_returnsNullWithoutQuery()
    {
        assertNull(service.selectByCategoryId(null));
        verifyNoInteractions(groupMapper);
    }

    @Test
    void selectByCategoryId_delegates()
    {
        AiSkillGroup group = new AiSkillGroup();
        when(groupMapper.selectByCategoryId(3L)).thenReturn(group);

        assertSame(group, service.selectByCategoryId(3L));
    }

    @Test
    void selectMembersByGroupId_delegates()
    {
        List<AiSkillGroupMember> members = Collections.singletonList(new AiSkillGroupMember());
        when(groupMapper.selectMembersByGroupId(1L)).thenReturn(members);

        assertEquals(members, service.selectMembersByGroupId(1L));
    }

    // ---------------- 新增/修改/删除 ----------------

    @Test
    void insert_fillsStatusAndStrategyDefaults()
    {
        AiSkillGroup group = new AiSkillGroup();
        when(groupMapper.insertAiSkillGroup(group)).thenReturn(1);

        assertEquals(1, service.insertAiSkillGroup(group));

        assertEquals("1", group.getStatus());
        assertEquals("round_robin", group.getStrategy());
    }

    @Test
    void insert_keepsProvidedStatusAndStrategy()
    {
        AiSkillGroup group = new AiSkillGroup();
        group.setStatus("0");
        group.setStrategy("least_busy");
        when(groupMapper.insertAiSkillGroup(group)).thenReturn(1);

        service.insertAiSkillGroup(group);

        assertEquals("0", group.getStatus());
        assertEquals("least_busy", group.getStrategy());
    }

    @Test
    void update_delegates()
    {
        AiSkillGroup group = new AiSkillGroup();
        when(groupMapper.updateAiSkillGroup(group)).thenReturn(1);

        assertEquals(1, service.updateAiSkillGroup(group));
    }

    @Test
    void delete_removesMembersBeforeGroup()
    {
        Long[] ids = {1L, 2L};
        when(groupMapper.deleteMembersByGroupIds(ids)).thenReturn(3);
        when(groupMapper.deleteAiSkillGroupByGroupIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiSkillGroupByGroupIds(ids));

        verify(groupMapper).deleteMembersByGroupIds(ids);
        verify(groupMapper).deleteAiSkillGroupByGroupIds(ids);
    }

    // ---------------- 成员维护 ----------------

    @Test
    void addMembers_emptyAgentIds_returnsZero()
    {
        assertEquals(0, service.addMembers(1L, null, 3));
        assertEquals(0, service.addMembers(1L, new Long[0], 3));
        verifyNoInteractions(groupMapper);
    }

    @Test
    void addMembers_skipsExistingAndFillsDefaults()
    {
        when(groupMapper.selectMemberByGroupAndAgent(1L, 10L)).thenReturn(new AiSkillGroupMember());
        when(groupMapper.selectMemberByGroupAndAgent(1L, 11L)).thenReturn(null);
        when(groupMapper.batchInsertMembers(any())).thenReturn(1);

        assertEquals(1, service.addMembers(1L, new Long[]{10L, 11L}, null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiSkillGroupMember>> captor = ArgumentCaptor.forClass(List.class);
        verify(groupMapper).batchInsertMembers(captor.capture());
        assertEquals(1, captor.getValue().size());
        AiSkillGroupMember member = captor.getValue().get(0);
        assertEquals(1L, member.getGroupId());
        assertEquals(11L, member.getAgentId());
        assertEquals(3, member.getSkillLevel());
        assertEquals(0, member.getPriority());
        assertEquals(1, member.getMaxConcurrent());
        assertEquals("1", member.getStatus());
    }

    @Test
    void addMembers_usesCustomSkillLevel()
    {
        when(groupMapper.selectMemberByGroupAndAgent(1L, 10L)).thenReturn(null);
        when(groupMapper.batchInsertMembers(any())).thenReturn(1);

        service.addMembers(1L, new Long[]{10L}, 1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiSkillGroupMember>> captor = ArgumentCaptor.forClass(List.class);
        verify(groupMapper).batchInsertMembers(captor.capture());
        assertEquals(1, captor.getValue().get(0).getSkillLevel());
    }

    @Test
    void addMembers_allExisting_returnsZeroWithoutBatchInsert()
    {
        when(groupMapper.selectMemberByGroupAndAgent(anyLong(), anyLong()))
                .thenReturn(new AiSkillGroupMember());

        assertEquals(0, service.addMembers(1L, new Long[]{10L, 11L}, 3));
        verify(groupMapper, never()).batchInsertMembers(any());
    }

    @Test
    void removeMembers_emptyAgentIds_returnsZero()
    {
        assertEquals(0, service.removeMembers(1L, null));
        assertEquals(0, service.removeMembers(1L, new Long[0]));
        verifyNoInteractions(groupMapper);
    }

    @Test
    void removeMembers_delegates()
    {
        Long[] agentIds = {10L, 11L};
        when(groupMapper.deleteMembersByGroupAndAgent(1L, agentIds)).thenReturn(2);

        assertEquals(2, service.removeMembers(1L, agentIds));
    }

    @Test
    void updateMember_delegates()
    {
        AiSkillGroupMember member = new AiSkillGroupMember();
        when(memberMapper.updateMember(member)).thenReturn(1);

        assertEquals(1, service.updateMember(member));
    }
}
