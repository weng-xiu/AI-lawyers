package ai.lawyers.system.service.lawyers.trunk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallEventDedupMapper;

/**
 * P3-B4：PBX 事件幂等守卫（第一道防线）。
 *
 * <p>背景：ESL 订阅同时含 {@code CHANNEL_HANGUP} 与 {@code CHANNEL_HANGUP_COMPLETE}
 * （挂断一次通话两个事件都会到达），PBX 重发、多实例重复消费、网络重连补发
 * 都会让同一事件进入多次，导致 finishCall 双释放线路并发/全局并发、指标双计。</p>
 *
 * <p>判重键：{@code source|eventKey|eventName} 落 {@code ai_call_event_dedup} 主键，
 * INSERT IGNORE 返回 1=首次到达（放行），0=重复（丢弃）。</p>
 *
 * <p><b>故障策略 fail-open</b>：幂等表写入异常时放行本次事件并 WARN——
 * 宁可依赖第二道防线（状态机终态兜底，见
 * {@code CallDispatchServiceImpl.onCallEvent}）承担偶发重复，
 * 也不因幂等表故障阻断全部事件流。</p>
 *
 * @author ai-lawyers
 */
@Component
public class CallEventIdempotencyGuard
{
    private static final Logger log = LoggerFactory.getLogger(CallEventIdempotencyGuard.class);

    @Autowired
    private AiCallEventDedupMapper dedupMapper;

    /**
     * 判定事件是否首次到达。
     *
     * @param source    事件来源（如 "ESL:10.0.0.1:8021"）
     * @param eventKey  事件对象键（通道 UUID）；为空时不具备判重条件，直接放行
     * @param eventName 规范化事件名（调用方须先做 HANGUP_COMPLETE→HANGUP 归一）
     * @return true=首次到达应处理；false=重复事件应丢弃
     */
    public boolean firstSeen(String source, String eventKey, String eventName)
    {
        if (StringUtils.isEmpty(eventKey) || StringUtils.isEmpty(eventName))
        {
            return true;
        }
        String src = source == null ? "" : source;
        String dedupKey = src + "|" + eventKey + "|" + eventName;
        try
        {
            return dedupMapper.insertIgnore(dedupKey, src, eventKey, eventName) == 1;
        }
        catch (Exception e)
        {
            // fail-open：幂等表异常不阻断事件流，由状态机终态兜底
            log.warn("[B4] 事件幂等写入异常，放行本次事件 key={} err={}", dedupKey, e.getMessage());
            return true;
        }
    }
}
