package ai.lawyers.system.service.impl.lawyers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiUnifiedSession;
import ai.lawyers.system.mapper.lawyers.AiUnifiedSessionMapper;
import ai.lawyers.system.service.lawyers.IAiUnifiedSessionService;
import ai.lawyers.system.service.lawyers.session.ChannelSessionGuard;
import ai.lawyers.system.service.lawyers.session.OccupyResult;

/**
 * 跨渠道统一会话索引 Service 实现（F6 + 方案B 同渠道互斥/跨渠道提示）
 *
 * @author ai-lawyers
 */
@Service
public class AiUnifiedSessionServiceImpl implements IAiUnifiedSessionService
{
    private static final Logger log = LoggerFactory.getLogger(AiUnifiedSessionServiceImpl.class);

    /** 看门狗单轮最多回收条数，避免长事务锁表 */
    private static final int WATCHDOG_BATCH = 200;

    /** 看门狗删除 Redis 占用：仅当值仍为该会话令牌时才删（CAS 防误删新会话） */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
          + "  return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    @Autowired
    private AiUnifiedSessionMapper unifiedSessionMapper;

    @Autowired
    private ChannelSessionGuard sessionGuard;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public int recordSession(AiUnifiedSession session)
    {
        if (session == null || StringUtils.isEmpty(session.getBizType())
                || StringUtils.isEmpty(session.getBizId()))
        {
            return 0;
        }
        if (session.getStartTime() == null)
        {
            session.setStartTime(new Date());
        }
        return unifiedSessionMapper.insertIgnoreAiUnifiedSession(session);
    }

    @Override
    public int finishSession(String bizType, String bizId, Date endTime)
    {
        if (StringUtils.isEmpty(bizType) || StringUtils.isEmpty(bizId))
        {
            return 0;
        }
        return unifiedSessionMapper.finishSession(bizType, bizId, endTime == null ? new Date() : endTime);
    }

    @Override
    public OccupyResult startActive(AiUnifiedSession session, int ttlSeconds)
    {
        if (session == null || StringUtils.isEmpty(session.getBizType())
                || StringUtils.isEmpty(session.getBizId()))
        {
            throw new IllegalArgumentException("活跃会话缺少 bizType/bizId");
        }
        return sessionGuard.tryStart(session, ttlSeconds);
    }

    @Override
    public void heartbeat(String bizType, String bizId)
    {
        if (StringUtils.isEmpty(bizType) || StringUtils.isEmpty(bizId))
        {
            return;
        }
        unifiedSessionMapper.heartbeat(bizType, bizId, new Date());
    }

    @Override
    public List<AiUnifiedSession> listActive(Long profileId, String callerNumber)
    {
        if (profileId == null && StringUtils.isEmpty(callerNumber))
        {
            return new ArrayList<>();
        }
        return sessionGuard.listActive(profileId, callerNumber);
    }

    @Override
    public int reclaimExpired()
    {
        List<AiUnifiedSession> expired = unifiedSessionMapper.selectExpiredActive(new Date(), WATCHDOG_BATCH);
        int reclaimed = 0;
        for (AiUnifiedSession s : expired)
        {
            int rows = unifiedSessionMapper.forceFinish(s.getSessionId(), new Date());
            if (rows > 0)
            {
                reclaimed += rows;
                removeRedisKeySafe(s);
                log.info("[会话看门狗] 回收残留活跃会话 sessionId={} bizType={} bizId={} owner={}",
                        s.getSessionId(), s.getBizType(), s.getBizId(), s.getActiveOwner());
            }
        }
        return reclaimed;
    }

    /** 看门狗 CAS 删除 Redis 占用：仅当值仍为该会话令牌时才删，避免误删 TTL 后新建立的占用 */
    private void removeRedisKeySafe(AiUnifiedSession s)
    {
        if (stringRedisTemplate == null)
        {
            return;
        }
        try
        {
            String key = ChannelSessionGuard.ownerKey(s.getProfileId(), s.getCallerNumber(), s.getChannelType());
            stringRedisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(key),
                    s.getBizType() + ":" + s.getBizId());
        }
        catch (Exception e)
        {
            // Redis 异常不影响 DB 回收结果，残留 key 由 TTL 兜底
            log.debug("[会话看门狗] Redis 占用删除失败 sessionId={} err={}", s.getSessionId(), e.getMessage());
        }
    }

    @Override
    public List<AiUnifiedSession> timeline(Long profileId, String callerNumber)
    {
        Map<Long, AiUnifiedSession> merged = new LinkedHashMap<>();
        if (profileId != null)
        {
            for (AiUnifiedSession s : unifiedSessionMapper.selectByProfileId(profileId))
            {
                merged.put(s.getSessionId(), s);
            }
        }
        if (StringUtils.isNotEmpty(callerNumber))
        {
            for (AiUnifiedSession s : unifiedSessionMapper.selectByCallerNumber(callerNumber))
            {
                merged.putIfAbsent(s.getSessionId(), s);
            }
        }
        List<AiUnifiedSession> result = new ArrayList<>(merged.values());
        result.sort(Comparator.comparing(AiUnifiedSession::getStartTime,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return result;
    }
}
