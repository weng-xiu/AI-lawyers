package ai.lawyers.system.service.lawyers.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ai.lawyers.system.domain.lawyers.AiUnifiedSession;

/**
 * 同渠道占用结果测试：成功/失败工厂、Redis 健康标记与既有业务主键提取。
 *
 * @author ai-lawyers
 */
class OccupyResultTest
{
    @Test
    void acquired_标记抢占成功()
    {
        AiUnifiedSession session = session("biz-1");

        OccupyResult result = OccupyResult.acquired(session, true);

        assertTrue(result.isAcquired());
        assertTrue(result.isRedisHealthy());
        assertSame(session, result.getSession());
    }

    @Test
    void blocked_标记抢占失败并携带既有会话()
    {
        AiUnifiedSession existing = session("biz-2");

        OccupyResult result = OccupyResult.blocked(existing, false);

        assertFalse(result.isAcquired());
        assertFalse(result.isRedisHealthy());
        assertSame(existing, result.getSession());
        assertEquals("biz-2", result.getExistingBizId());
    }

    @Test
    void getExistingBizId_会话为空时返回null()
    {
        OccupyResult result = OccupyResult.blocked(null, true);

        assertNull(result.getSession());
        assertNull(result.getExistingBizId());
    }

    /** 构造业务主键为指定值的会话 */
    private AiUnifiedSession session(String bizId)
    {
        AiUnifiedSession session = new AiUnifiedSession();
        session.setBizId(bizId);
        return session;
    }
}
