package ai.lawyers.system.service.impl.lawyers.outbound;

import java.util.Date;

import ai.lawyers.system.domain.lawyers.trunk.DialResult;

/**
 * 外呼重试策略纯函数工具（P5 超大类拆分）。
 *
 * <p>从 {@link OutboundExecutionServiceImpl} 抽出的无状态纯逻辑：
 * 指数退避时间计算、可重试错误判定、失败原因截断。门面、拨号器、落库收尾器共用，
 * 单测可直接静态调用（替代旧反射测 private 的方式）。</p>
 *
 * @author ai-lawyers
 */
public final class OutboundRetryPolicy
{
    private static final long MAX_BACKOFF_MS = 10L * 60 * 1000;

    private OutboundRetryPolicy()
    {
    }

    /** T2-2 指数退避：base * 2^(retryTimes-1)，上限 10 分钟 */
    public static Date backoffRetryTime(int retryTimes, long baseMs)
    {
        long backoff = baseMs;
        for (int i = 1; i < Math.min(retryTimes, 8); i++)
        {
            backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
        }
        return new Date(System.currentTimeMillis() + backoff);
    }

    /** 可重试错误判定：无可用线路/线路忙/限流/队列满/网关错误等瞬时故障 */
    public static boolean isRetryableError(DialResult result)
    {
        if (result == null || result.getErrorCode() == null)
        {
            return false;
        }
        switch (result.getErrorCode())
        {
            case "NO_AVAILABLE_TRUNK":
            case "TRUNK_BUSY":
            case "CPS_LIMIT":
            case "QUEUE_FULL":
            case "GATEWAY_ERROR":
            case "GATEWAY_REJECT":
            case "DIAL_FAILED":
                return true;
            default:
                return false;
        }
    }

    /** 失败原因入库截断（fail_reason 列宽 500） */
    public static String truncate(String s, int max)
    {
        if (s == null)
        {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
