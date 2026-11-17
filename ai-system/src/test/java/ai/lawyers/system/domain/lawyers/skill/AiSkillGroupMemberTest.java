package ai.lawyers.system.domain.lawyers.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 技能组成员实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiSkillGroupMemberTest
{
    @Test
    void 属性读写()
    {
        Date createTime = new Date();
        Date updateTime = new Date();
        AiSkillGroupMember member = new AiSkillGroupMember();
        member.setId(1L);
        member.setGroupId(2L);
        member.setAgentId(3L);
        member.setSkillLevel(4);
        member.setPriority(5);
        member.setMaxConcurrent(2);
        member.setStatus("1");
        member.setAgentName("张三");
        member.setAgentStatus("1");
        member.setCallStatus("0");
        member.setAgentIds(new Long[] {3L, 4L});
        member.setCreateTime(createTime);
        member.setUpdateTime(updateTime);

        assertEquals(1L, member.getId());
        assertEquals(2L, member.getGroupId());
        assertEquals(3L, member.getAgentId());
        assertEquals(4, member.getSkillLevel());
        assertEquals(5, member.getPriority());
        assertEquals(2, member.getMaxConcurrent());
        assertEquals("1", member.getStatus());
        assertEquals("张三", member.getAgentName());
        assertEquals("1", member.getAgentStatus());
        assertEquals("0", member.getCallStatus());
        assertEquals(2, member.getAgentIds().length);
        assertEquals(4L, member.getAgentIds()[1]);
        assertEquals(createTime, member.getCreateTime());
        assertEquals(updateTime, member.getUpdateTime());
    }

    @Test
    void 默认值为空()
    {
        AiSkillGroupMember member = new AiSkillGroupMember();

        assertNull(member.getId());
        assertNull(member.getGroupId());
        assertNull(member.getAgentId());
        assertNull(member.getSkillLevel());
        assertNull(member.getStatus());
        assertNull(member.getAgentIds());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        AiSkillGroupMember member = new AiSkillGroupMember();
        member.setId(1L);
        member.setGroupId(2L);
        member.setAgentId(3L);
        member.setSkillLevel(4);

        String text = member.toString();

        assertTrue(text.contains("groupId"));
        assertTrue(text.contains("agentId"));
        assertTrue(text.contains("skillLevel"));
    }
}
