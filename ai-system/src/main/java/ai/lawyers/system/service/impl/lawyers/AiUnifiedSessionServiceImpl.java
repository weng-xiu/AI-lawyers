package ai.lawyers.system.service.impl.lawyers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiUnifiedSession;
import ai.lawyers.system.mapper.lawyers.AiUnifiedSessionMapper;
import ai.lawyers.system.service.lawyers.IAiUnifiedSessionService;

/**
 * 跨渠道统一会话索引 Service 实现（F6）
 *
 * @author ai-lawyers
 */
@Service
public class AiUnifiedSessionServiceImpl implements IAiUnifiedSessionService
{
    @Autowired
    private AiUnifiedSessionMapper unifiedSessionMapper;

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
