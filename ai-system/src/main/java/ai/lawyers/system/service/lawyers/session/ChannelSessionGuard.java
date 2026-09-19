package ai.lawyers.system.service.lawyers.session;

import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiUnifiedSession;
import ai.lawyers.system.mapper.lawyers.AiUnifiedSessionMapper;

/**
 * 方案B：跨渠道会话占用守卫。
 *
 * <p>职责：</p>
 * <ul>
 *   <li><b>同渠道互斥</b>：同一身份（profileId 优先、手机号兜底）在同一 channelType 同时只允许一条活跃会话。
 *       Redis {@code SET NX PX} 做热路径抢占，DB 生成列唯一键 {@code uk_active_owner} 做最终兜底；</li>
 *   <li><b>跨渠道放行</b>：占用键含 channelType，不同渠道互不阻塞（跨渠道并发由坐席侧 listActive 提示）；</li>
 *   <li><b>残留回收</b>：Redis TTL 自动过期 + 业务结束主动释放 + 看门狗回收 DB 残留（见 Service.reclaimExpired）。</li>
 * </ul>
 *
 * <p>Redis 不可用时自动降级为 DB 唯一键判定，可用性优先；总开关 {@code session.occupy.enabled=false}
 * 时直接放行（不写占用态），保证零行为变化的回退路径。</p>
 *
 * @author ai-lawyers
 */
@Component
public class ChannelSessionGuard
{
    private static final Logger log = LoggerFactory.getLogger(ChannelSessionGuard.class);

    private static final String KEY_PREFIX = "session:occupy:";

    /** 释放：仅当占用者仍是自己（bizType:bizId）才 DEL，杜绝 TTL 过期后误删新会话 */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
          + "  return redis.call('del', KEYS[1]) "
          + "else return 0 end", Long.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private AiUnifiedSessionMapper unifiedSessionMapper;

    /** 总开关：false 时不做任何占用（应急回退） */
    @Value("${session.occupy.enabled:true}")
    private boolean enabled;

    /**
     * 计算占用键，口径与 DB 生成列 active_owner 完全一致：
     * {@code profileId|'N'} : {@code callerNumber|'N'} : channelType。
     */
    public static String ownerKey(Long profileId, String callerNumber, String channelType)
    {
        String p = profileId == null ? "N" : String.valueOf(profileId);
        String n = StringUtils.isEmpty(callerNumber) ? "N" : callerNumber;
        return KEY_PREFIX + p + ":" + n + ":" + channelType;
    }

    /** 与 SQL 生成列相同的键值（不含 Redis 前缀），供 DB 兜底查询使用 */
    public static String ownerValue(Long profileId, String callerNumber, String channelType)
    {
        String p = profileId == null ? "N" : String.valueOf(profileId);
        String n = StringUtils.isEmpty(callerNumber) ? "N" : callerNumber;
        return p + ":" + n + ":" + channelType;
    }

    /**
     * 尝试开启一条活跃占用会话。
     *
     * @param session    会话（须含 profileId 或 callerNumber 至少其一、channelType、bizType、bizId）
     * @param ttlSeconds Redis 占用 TTL（秒）；结束事件丢失时兜底过期
     * @return 抢占结果；disabled 时返回 acquired 但不写占用（调用方无需特殊处理）
     */
    public OccupyResult tryStart(AiUnifiedSession session, int ttlSeconds)
    {
        if (!enabled)
        {
            return OccupyResult.acquired(session, true);
        }
        validate(session);
        String redisKey = ownerKey(session.getProfileId(), session.getCallerNumber(), session.getChannelType());
        String token = token(session);
        Date now = new Date();
        if (session.getStartTime() == null)
        {
            session.setStartTime(now);
        }
        session.setActiveFlag("1");
        session.setLastHeartbeat(now);
        session.setExpireTime(new Date(now.getTime() + ttlSeconds * 1000L));

        boolean redisHealthy = true;
        String currentToken = null;
        try
        {
            Boolean ok = stringRedisTemplate.opsForValue()
                    .setIfAbsent(redisKey, token, Duration.ofSeconds(Math.max(ttlSeconds, 1)));
            if (!Boolean.TRUE.equals(ok))
            {
                currentToken = stringRedisTemplate.opsForValue().get(redisKey);
            }
        }
        catch (Exception e)
        {
            // Redis 故障：降级 DB 唯一键判定，不阻断业务
            redisHealthy = false;
            log.warn("会话占用 Redis 抢占异常，降级 DB 唯一键 key={} err={}", redisKey, e.getMessage());
        }

        if (currentToken != null)
        {
            // Redis 命中同渠道活跃会话：取其业务主键（token=bizType:bizId:...）
            AiUnifiedSession existing = resolveExisting(session, currentToken);
            if (existing != null)
            {
                return OccupyResult.blocked(existing, redisHealthy);
            }
            // 占用值异常（脏数据/无法解析）：不信任该占用，落 DB 兜底判定
        }

        try
        {
            int rows = unifiedSessionMapper.insertActiveSession(session);
            if (rows > 0)
            {
                return OccupyResult.acquired(session, redisHealthy);
            }
        }
        catch (DuplicateKeyException dup)
        {
            // uk_active_owner 冲突：同身份同渠道已有活跃会话（Redis 失效/降级场景）
            AiUnifiedSession existing = unifiedSessionMapper.selectActiveByOwner(
                    ownerValue(session.getProfileId(), session.getCallerNumber(), session.getChannelType()));
            if (existing != null)
            {
                return OccupyResult.blocked(existing, redisHealthy);
            }
        }
        // DB 未报冲突也未插入（理论上不该发生）：保守放行
        return OccupyResult.acquired(session, redisHealthy);
    }

    /** 结束占用：释放 Redis + 置 DB 非活跃 */
    public void release(String bizType, String bizId, AiUnifiedSession identity)
    {
        try
        {
            unifiedSessionMapper.finishSession(bizType, bizId, new Date());
        }
        catch (Exception e)
        {
            log.warn("会话占用 DB 结束失败 bizType={} bizId={} err={}", bizType, bizId, e.getMessage());
        }
        if (enabled && identity != null)
        {
            try
            {
                String redisKey = ownerKey(identity.getProfileId(), identity.getCallerNumber(), identity.getChannelType());
                stringRedisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(redisKey),
                        bizType + ":" + bizId);
            }
            catch (Exception e)
            {
                // Redis 释放失败不影响结束：TTL/看门狗兜底
                log.debug("会话占用 Redis 释放失败 bizType={} bizId={} err={}", bizType, bizId, e.getMessage());
            }
        }
    }

    /** 续心跳（仅 Redis TTL；DB 心跳由 Service 负责），失败静默，靠 TTL/看门狗兜底 */
    public boolean renew(String redisKey, String token, int ttlSeconds)
    {
        if (!enabled)
        {
            return true;
        }
        try
        {
            Long r = stringRedisTemplate.execute(new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then "
                  + "  return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end", Long.class),
                    Collections.singletonList(redisKey), token, String.valueOf(ttlSeconds * 1000L));
            return r != null && r > 0;
        }
        catch (Exception e)
        {
            log.debug("会话占用续期失败 key={} err={}", redisKey, e.getMessage());
            return false;
        }
    }

    /** 查某身份当前所有渠道的活跃会话（档案 + 手机号双键合并） */
    public java.util.List<AiUnifiedSession> listActive(Long profileId, String callerNumber)
    {
        java.util.LinkedHashMap<Long, AiUnifiedSession> merged = new java.util.LinkedHashMap<>();
        if (profileId != null)
        {
            unifiedSessionMapper.selectActiveByProfileId(profileId)
                    .forEach(s -> merged.put(s.getSessionId(), s));
        }
        if (StringUtils.isNotEmpty(callerNumber))
        {
            unifiedSessionMapper.selectActiveByCallerNumber(callerNumber)
                    .forEach(s -> merged.putIfAbsent(s.getSessionId(), s));
        }
        return new java.util.ArrayList<>(merged.values());
    }

    private AiUnifiedSession resolveExisting(AiUnifiedSession request, String token)
    {
        // 占用键为"身份+渠道"粒度：库中该 owner 的活跃记录即既有会话；
        // token（bizType:bizId）仅用于释放时 CAS 比对，这里不参与定位。
        return unifiedSessionMapper.selectActiveByOwner(
                ownerValue(request.getProfileId(), request.getCallerNumber(), request.getChannelType()));
    }

    /**
     * 占用值（CAS 令牌）：bizType:bizId。
     * 不含 nodeId，使看门狗可用同一会话的 bizType:bizId 安全比对删除（新占用者令牌必然不同）；
     * 实例维度的可观察性由 DB 行 create_time 等承载。
     */
    private String token(AiUnifiedSession s)
    {
        return s.getBizType() + ":" + s.getBizId();
    }

    private void validate(AiUnifiedSession s)
    {
        if (s == null || StringUtils.isEmpty(s.getChannelType())
                || StringUtils.isEmpty(s.getBizType()) || StringUtils.isEmpty(s.getBizId()))
        {
            throw new IllegalArgumentException("会话占用缺少 channelType/bizType/bizId");
        }
        if (s.getProfileId() == null && StringUtils.isEmpty(s.getCallerNumber()))
        {
            throw new IllegalArgumentException("会话占用必须有 profileId 或 callerNumber");
        }
    }
}
