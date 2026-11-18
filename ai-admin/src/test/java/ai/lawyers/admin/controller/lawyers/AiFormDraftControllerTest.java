package ai.lawyers.admin.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiFormDraft;
import ai.lawyers.system.service.lawyers.IAiFormDraftService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 表单草稿接口测试：当前登录用户隔离与草稿读写删。
 *
 * @author ai-lawyers
 */
class AiFormDraftControllerTest
{
    private static final Long USER_ID = 9L;

    private AiFormDraftController controller;

    private IAiFormDraftService formDraftService;

    @BeforeEach
    void setUp()
    {
        formDraftService = mock(IAiFormDraftService.class);
        controller = new AiFormDraftController();
        ReflectionTestUtils.setField(controller, "formDraftService", formDraftService);
    }

    @Test
    void 读取草稿存在时返回数据()
    {
        AiFormDraft draft = new AiFormDraft();
        draft.setFormKey("consult");
        draft.setContent("{\"content\":\"欠薪\"}");
        when(formDraftService.selectDraft(USER_ID, "consult")).thenReturn(draft);

        try (MockedStatic<SecurityUtils> mocked = Mockito.mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(USER_ID);

            AjaxResult result = controller.get("consult");

            assertSame(draft, result.get(AjaxResult.DATA_TAG));
        }
    }

    @Test
    void 草稿不存在时返回成功但不含数据()
    {
        when(formDraftService.selectDraft(USER_ID, "consult")).thenReturn(null);

        try (MockedStatic<SecurityUtils> mocked = Mockito.mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(USER_ID);

            AjaxResult result = controller.get("consult");

            assertTrue(result.isSuccess());
            assertNull(result.get(AjaxResult.DATA_TAG));
        }
    }

    @Test
    void 保存草稿使用当前登录用户()
    {
        AiFormDraft draft = new AiFormDraft();
        draft.setFormKey("consult");
        draft.setContent("{\"content\":\"欠薪\"}");

        try (MockedStatic<SecurityUtils> mocked = Mockito.mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(USER_ID);

            AjaxResult result = controller.save(draft);

            assertTrue(result.isSuccess());
            verify(formDraftService).saveDraft(USER_ID, "consult", "{\"content\":\"欠薪\"}");
        }
    }

    @Test
    void 删除草稿使用当前登录用户()
    {
        try (MockedStatic<SecurityUtils> mocked = Mockito.mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUserId).thenReturn(USER_ID);

            AjaxResult result = controller.remove("consult");

            assertTrue(result.isSuccess());
            verify(formDraftService).deleteDraft(USER_ID, "consult");
        }
    }
}
