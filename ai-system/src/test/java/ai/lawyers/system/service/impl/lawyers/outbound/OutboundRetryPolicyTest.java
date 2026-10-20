package ai.lawyers.system.service.impl.lawyers.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.junit.jupiter.api.Test;

import ai.lawyers.system.domain.lawyers.trunk.DialResult;

/**
 * T5-2 外呼重试策略纯逻辑测试：指数退避时间计算 + 可重试错误判定。
 *
 * <p>P5 超大类拆分后这两个纯函数移入 {@link OutboundRetryPolicy}（无状态静态工具），
 * 直接静态调用，替代旧的反射测 private 方式。</p>
 *
 * @author ai-lawyers
 */
class OutboundRetryPolicyTest
{
    private static final long BASE_MS = 1000L;

    @Test
    void backoffRetryTime_growsExponentiallyAndCapsAt10Min()
    {
        long now = System.currentTimeMillis();
        // retryTimes=1：base*2^0 = 1000ms（循环 i<min(1,8)=1 不迭代）
        long d1 = OutboundRetryPolicy.backoffRetryTime(1, BASE_MS).getTime() - now;
        assertThat(d1).isBetween(900L, 1500L);
        // retryTimes=2：base*2^1 = 2000ms
        long d2 = OutboundRetryPolicy.backoffRetryTime(2, BASE_MS).getTime() - now;
        assertThat(d2).isBetween(1900L, 2500L);
        // retryTimes=3：4000ms
        long d3 = OutboundRetryPolicy.backoffRetryTime(3, BASE_MS).getTime() - now;
        assertThat(d3).isBetween(3900L, 4500L);
        // 退避迭代最多 7 次（i<min(retryTimes,8)）：base=1000 时上限 1000*2^7=128000ms
        long d10 = OutboundRetryPolicy.backoffRetryTime(10, BASE_MS).getTime() - now;
        assertThat(d10).isBetween(127000L, 129000L);
    }

    @Test
    void backoffRetryTime_capsAtTenMinutesWhenBaseLarge()
    {
        // base=60000ms(1分钟) 时，2 次迭代即 240000ms，7 次理论 60000*128=768万ms，
        // 被 Math.min(..., 10分钟=600000ms) 封顶
        long now = System.currentTimeMillis();
        Date d8 = OutboundRetryPolicy.backoffRetryTime(8, 60000L);
        assertThat(d8.getTime() - now).as("退避不得超过 10 分钟上限").isBetween(599000L, 601000L);
    }

    @Test
    void isRetryableError_transientErrorsRetryable()
    {
        String[] retryable = { "NO_AVAILABLE_TRUNK", "TRUNK_BUSY", "CPS_LIMIT",
                "QUEUE_FULL", "GATEWAY_ERROR", "GATEWAY_REJECT", "DIAL_FAILED" };
        for (String code : retryable)
        {
            DialResult r = new DialResult();
            r.setSuccess(false);
            r.setErrorCode(code);
            assertThat(OutboundRetryPolicy.isRetryableError(r))
                    .as("错误码 %s 应判定为可重试", code).isTrue();
        }
    }

    @Test
    void isRetryableError_permanentErrorsNotRetryable()
    {
        String[] permanent = { "BLACKLISTED", "UNSUBSCRIBED", null, "INVALID_NUMBER" };
        for (String code : permanent)
        {
            DialResult r = new DialResult();
            r.setSuccess(false);
            r.setErrorCode(code);
            assertThat(OutboundRetryPolicy.isRetryableError(r))
                    .as("错误码 %s 应判定为不可重试", code).isFalse();
        }
        assertThat(OutboundRetryPolicy.isRetryableError(null)).isFalse();
    }

    @Test
    void truncate_shouldLimitLengthAndHandleNull()
    {
        assertThat(OutboundRetryPolicy.truncate(null, 10)).isNull();
        assertThat(OutboundRetryPolicy.truncate("abc", 10)).isEqualTo("abc");
        assertThat(OutboundRetryPolicy.truncate("abcdefghij", 5)).isEqualTo("abcde");
    }
}
