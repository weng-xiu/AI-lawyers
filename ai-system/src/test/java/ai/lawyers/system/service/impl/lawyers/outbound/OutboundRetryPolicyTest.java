package ai.lawyers.system.service.impl.lawyers.outbound;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Date;
import org.junit.jupiter.api.Test;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.service.impl.lawyers.outbound.OutboundExecutionServiceImpl;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T5-2 外呼重试策略纯逻辑测试：指数退避时间计算 + 可重试错误判定。
 *
 * <p>这两个方法是不依赖 DB/Redis 的纯函数（private），通过反射调用。</p>
 *
 * @author ai-lawyers
 */
class OutboundRetryPolicyTest
{
    private final OutboundExecutionServiceImpl service = new OutboundExecutionServiceImpl();

    private Object invoke(String method, Class<?>[] types, Object... args) throws Exception
    {
        Method m = OutboundExecutionServiceImpl.class.getDeclaredMethod(method, types);
        m.setAccessible(true);
        return m.invoke(service, args);
    }

    @Test
    void backoffRetryTime_growsExponentiallyAndCapsAt10Min() throws Exception
    {
        // 设置退避基数为 1000ms，便于断言
        Field f = OutboundExecutionServiceImpl.class.getDeclaredField("retryBackoffBaseMs");
        f.setAccessible(true);
        f.setLong(service, 1000L);

        long now = System.currentTimeMillis();
        // retryTimes=1：base*2^0 = 1000ms（循环 i<min(1,8)=1 不迭代）
        long d1 = ((Date) invoke("backoffRetryTime", new Class[]{int.class}, 1)).getTime() - now;
        assertThat(d1).isBetween(900L, 1500L);
        // retryTimes=2：base*2^1 = 2000ms
        long d2 = ((Date) invoke("backoffRetryTime", new Class[]{int.class}, 2)).getTime() - now;
        assertThat(d2).isBetween(1900L, 2500L);
        // retryTimes=3：4000ms
        long d3 = ((Date) invoke("backoffRetryTime", new Class[]{int.class}, 3)).getTime() - now;
        assertThat(d3).isBetween(3900L, 4500L);
        // 退避迭代最多 7 次（i<min(retryTimes,8)）：base=1000 时上限 1000*2^7=128000ms
        long d10 = ((Date) invoke("backoffRetryTime", new Class[]{int.class}, 10)).getTime() - now;
        assertThat(d10).isBetween(127000L, 129000L);
    }

    @Test
    void backoffRetryTime_capsAtTenMinutesWhenBaseLarge() throws Exception
    {
        // base=60000ms(1分钟) 时，2 次迭代即 240000ms，7 次理论 60000*128=768万ms，
        // 被 Math.min(..., 10分钟=600000ms) 封顶
        Field f = OutboundExecutionServiceImpl.class.getDeclaredField("retryBackoffBaseMs");
        f.setAccessible(true);
        f.setLong(service, 60000L);

        long now = System.currentTimeMillis();
        long d8 = ((Date) invoke("backoffRetryTime", new Class[]{int.class}, 8)).getTime() - now;
        assertThat(d8).as("退避不得超过 10 分钟上限").isBetween(599000L, 601000L);
    }

    @Test
    void isRetryableError_transientErrorsRetryable() throws Exception
    {
        String[] retryable = {"NO_AVAILABLE_TRUNK", "TRUNK_BUSY", "CPS_LIMIT",
                "QUEUE_FULL", "GATEWAY_ERROR", "GATEWAY_REJECT", "DIAL_FAILED"};
        for (String code : retryable)
        {
            DialResult r = new DialResult();
            r.setSuccess(false);
            r.setErrorCode(code);
            Object result = invoke("isRetryableError", new Class[]{DialResult.class}, r);
            assertThat(result).as("错误码 %s 应判定为可重试", code).isEqualTo(true);
        }
    }

    @Test
    void isRetryableError_permanentErrorsNotRetryable() throws Exception
    {
        // 空号码/鉴权失败等永久错误不应重试
        String[] permanent = {"INVALID_NUMBER", "AUTH_FAILED", "BLACKLISTED", null};
        for (String code : permanent)
        {
            DialResult r = new DialResult();
            r.setSuccess(false);
            r.setErrorCode(code);
            Object result = invoke("isRetryableError", new Class[]{DialResult.class}, r);
            assertThat(result).as("错误码 %s 不应重试", code).isEqualTo(false);
        }
        // null result 也安全返回 false
        Object nullResult = invoke("isRetryableError", new Class[]{DialResult.class}, new Object[]{null});
        assertThat(nullResult).isEqualTo(false);
    }
}
