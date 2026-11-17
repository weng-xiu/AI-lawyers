package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 表单草稿实体测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AiFormDraftTest
{
    @Test
    void 属性读写()
    {
        Date updateTime = new Date();
        AiFormDraft draft = new AiFormDraft();
        draft.setDraftId(1L);
        draft.setUserId(10L);
        draft.setFormKey("consultForm:zhangsan");
        draft.setContent("{\"ts\":123456,\"data\":{\"name\":\"张三\"}}");
        draft.setUpdateTime(updateTime);

        assertEquals(1L, draft.getDraftId());
        assertEquals(10L, draft.getUserId());
        assertEquals("consultForm:zhangsan", draft.getFormKey());
        assertEquals("{\"ts\":123456,\"data\":{\"name\":\"张三\"}}", draft.getContent());
        assertEquals(updateTime, draft.getUpdateTime());
    }

    @Test
    void 默认值为空()
    {
        AiFormDraft draft = new AiFormDraft();

        assertNull(draft.getDraftId());
        assertNull(draft.getUserId());
        assertNull(draft.getFormKey());
        assertNull(draft.getContent());
        assertNull(draft.getUpdateTime());
    }
}
