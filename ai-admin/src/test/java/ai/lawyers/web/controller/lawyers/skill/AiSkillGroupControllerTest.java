package ai.lawyers.web.controller.lawyers.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.skill.AiSkillGroup;
import ai.lawyers.system.domain.lawyers.skill.AiSkillGroupMember;
import ai.lawyers.system.service.lawyers.skill.IAiSkillGroupService;

/**
 * 技能组接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiSkillGroupControllerTest
{
    @Mock
    private IAiSkillGroupService aiSkillGroupService;

    @InjectMocks
    private AiSkillGroupController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private LoginUser loginUser(String username)
    {
        SysUser user = new SysUser();
        user.setUserName(username);
        return new LoginUser(1L, 2L, user, null);
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiSkillGroup query = new AiSkillGroup();
        List<AiSkillGroup> list = new ArrayList<AiSkillGroup>();
        list.add(new AiSkillGroup());
        when(aiSkillGroupService.selectAiSkillGroupList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
    }

    @Test
    void 启用技能组与导出()
    {
        List<AiSkillGroup> list = new ArrayList<AiSkillGroup>();
        when(aiSkillGroupService.selectEnabledGroups()).thenReturn(list);
        when(aiSkillGroupService.selectAiSkillGroupList(org.mockito.ArgumentMatchers.any(AiSkillGroup.class)))
                .thenReturn(List.of(new AiSkillGroup()));

        assertSame(list, controller.listEnabled().get(AjaxResult.DATA_TAG));
        AjaxResult exported = controller.export(new AiSkillGroup());
        assertEquals(200, exported.get(AjaxResult.CODE_TAG));
    }

    @Test
    void 详情附带成员列表()
    {
        AiSkillGroup group = new AiSkillGroup();
        group.setGroupId(5L);
        List<AiSkillGroupMember> members = new ArrayList<AiSkillGroupMember>();
        when(aiSkillGroupService.selectAiSkillGroupByGroupId(5L)).thenReturn(group);
        when(aiSkillGroupService.selectMembersByGroupId(5L)).thenReturn(members);
        when(aiSkillGroupService.selectAiSkillGroupByGroupId(6L)).thenReturn(null);

        assertSame(group, controller.getInfo(5L).get(AjaxResult.DATA_TAG));
        assertSame(members, group.getMembers());
        assertNull(controller.getInfo(6L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增与修改写入操作人()
    {
        AiSkillGroup group = new AiSkillGroup();
        when(aiSkillGroupService.insertAiSkillGroup(group)).thenReturn(1);
        when(aiSkillGroupService.updateAiSkillGroup(group)).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            assertEquals(200, controller.add(group).get(AjaxResult.CODE_TAG));
            assertEquals(500, controller.edit(group).get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", group.getCreateBy());
        assertEquals("admin", group.getUpdateBy());
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] groupIds = new Long[] { 1L, 2L };
        when(aiSkillGroupService.deleteAiSkillGroupByGroupIds(groupIds)).thenReturn(2);

        assertEquals(200, controller.remove(groupIds).get(AjaxResult.CODE_TAG));
        verify(aiSkillGroupService).deleteAiSkillGroupByGroupIds(groupIds);
    }

    @Test
    void 成员查询与批量增删()
    {
        List<AiSkillGroupMember> members = new ArrayList<AiSkillGroupMember>();
        when(aiSkillGroupService.selectMembersByGroupId(5L)).thenReturn(members);
        AiSkillGroupMember add = new AiSkillGroupMember();
        add.setGroupId(5L);
        add.setAgentIds(new Long[] { 11L, 12L });
        add.setSkillLevel(2);
        AiSkillGroupMember remove = new AiSkillGroupMember();
        remove.setGroupId(5L);
        remove.setAgentIds(new Long[] { 12L });
        when(aiSkillGroupService.addMembers(5L, new Long[] { 11L, 12L }, 2)).thenReturn(2);
        when(aiSkillGroupService.removeMembers(5L, new Long[] { 12L })).thenReturn(1);

        assertSame(members, controller.members(5L).get(AjaxResult.DATA_TAG));
        assertEquals(200, controller.addMembers(add).get(AjaxResult.CODE_TAG));
        assertEquals(200, controller.removeMembers(remove).get(AjaxResult.CODE_TAG));
    }
}
