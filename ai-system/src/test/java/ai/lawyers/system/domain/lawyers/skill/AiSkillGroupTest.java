package ai.lawyers.system.domain.lawyers.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 技能组实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiSkillGroupTest
{
    @Test
    void 属性读写()
    {
        AiSkillGroupMember member = new AiSkillGroupMember();
        member.setAgentId(9L);
        AiSkillGroup group = new AiSkillGroup();
        group.setGroupId(1L);
        group.setGroupName("法律咨询组");
        group.setGroupCode("LAW_CONSULT");
        group.setCategoryId(2L);
        group.setStrategy("round_robin");
        group.setMaxWait(60);
        group.setWrapUpTime(30);
        group.setServiceLevelThreshold(20);
        group.setOverflowGroupId(3L);
        group.setStatus("1");
        group.setCategoryName("法律咨询");
        group.setMemberCount(5);
        group.setMembers(List.of(member));

        assertEquals(1L, group.getGroupId());
        assertEquals("法律咨询组", group.getGroupName());
        assertEquals("LAW_CONSULT", group.getGroupCode());
        assertEquals(2L, group.getCategoryId());
        assertEquals("round_robin", group.getStrategy());
        assertEquals(60, group.getMaxWait());
        assertEquals(30, group.getWrapUpTime());
        assertEquals(20, group.getServiceLevelThreshold());
        assertEquals(3L, group.getOverflowGroupId());
        assertEquals("1", group.getStatus());
        assertEquals("法律咨询", group.getCategoryName());
        assertEquals(5, group.getMemberCount());
        assertEquals(1, group.getMembers().size());
        assertEquals(9L, group.getMembers().get(0).getAgentId());
    }

    @Test
    void 默认值为空()
    {
        AiSkillGroup group = new AiSkillGroup();

        assertNull(group.getGroupId());
        assertNull(group.getGroupName());
        assertNull(group.getStrategy());
        assertNull(group.getStatus());
        assertNull(group.getMembers());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        AiSkillGroup group = new AiSkillGroup();
        group.setGroupId(1L);
        group.setGroupName("法律咨询组");
        group.setGroupCode("LAW_CONSULT");
        group.setStatus("1");

        String text = group.toString();

        assertTrue(text.contains("法律咨询组"));
        assertTrue(text.contains("LAW_CONSULT"));
        assertTrue(text.contains("groupId"));
    }
}
