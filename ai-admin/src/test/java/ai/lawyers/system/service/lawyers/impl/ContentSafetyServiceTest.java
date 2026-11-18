package ai.lawyers.system.service.lawyers.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.lawyers.common.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 公众端内容安全过滤测试：长度校验、敏感词命中与开关/词库配置。
 *
 * @author ai-lawyers
 */
class ContentSafetyServiceTest
{
    private ContentSafetyService service;

    @BeforeEach
    void setUp()
    {
        service = new ContentSafetyService();
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "wordsConfig", "赌博，诈骗,套现");
    }

    @Test
    void 长度常量()
    {
        assertEquals(1000, ContentSafetyService.MAX_CONTENT_LENGTH);
        assertEquals(500, ContentSafetyService.MAX_FEEDBACK_LENGTH);
        assertEquals(10, ContentSafetyService.MIN_CONTENT_LENGTH);
    }

    @Test
    void 分类为空时拒绝提交()
    {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateConsultation("", "公司拖欠工资如何维权"));

        assertEquals("请选择问题分类", ex.getMessage());
    }

    @Test
    void 问题描述不足十字时拒绝提交()
    {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateConsultation("劳动争议", "  欠薪  "));

        assertEquals("问题描述至少需要10个字", ex.getMessage());
    }

    @Test
    void 问题描述超长时拒绝提交()
    {
        String content = "欠".repeat(ContentSafetyService.MAX_CONTENT_LENGTH + 1);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateConsultation("劳动争议", content));

        assertTrue(ex.getMessage().contains("1000"));
    }

    @Test
    void 合法问题描述通过校验()
    {
        assertDoesNotThrow(() -> service.validateConsultation("劳动争议", "公司拖欠工资三个月，如何申请劳动仲裁"));
    }

    @Test
    void 问题描述命中敏感词时拒绝提交()
    {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateConsultation("其他", "有人拉我参与网络赌博活动怎么办"));

        assertTrue(ex.getMessage().contains("赌博"));
    }

    @Test
    void 反馈为空时直接通过()
    {
        assertDoesNotThrow(() -> service.validateFeedback(null));
        assertDoesNotThrow(() -> service.validateFeedback(""));
    }

    @Test
    void 反馈超长或命中敏感词时拒绝()
    {
        String tooLong = "好".repeat(ContentSafetyService.MAX_FEEDBACK_LENGTH + 1);

        assertTrue(assertThrows(ServiceException.class, () -> service.validateFeedback(tooLong))
                .getMessage().contains("500"));
        assertTrue(assertThrows(ServiceException.class, () -> service.validateFeedback("客服诱导套现"))
                .getMessage().contains("违规"));
    }

    @Test
    void 首个敏感词命中返回该词()
    {
        assertEquals("套现", service.firstHit("有人引导套现"));
    }

    @Test
    void 开关关闭或文本为空时不拦截()
    {
        ReflectionTestUtils.setField(service, "enabled", false);
        assertNull(service.firstHit("网络赌博"));

        ReflectionTestUtils.setField(service, "enabled", true);
        assertNull(service.firstHit(""));
        assertNull(service.firstHit(null));
    }

    @Test
    void 词库为空时不拦截()
    {
        ContentSafetyService emptyService = new ContentSafetyService();
        ReflectionTestUtils.setField(emptyService, "enabled", true);
        ReflectionTestUtils.setField(emptyService, "wordsConfig", "");

        assertNull(emptyService.firstHit("网络赌博"));
    }
}
