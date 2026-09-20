package ai.lawyers.system.service.impl.lawyers;

import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiHotspotSuppress;
import ai.lawyers.system.domain.lawyers.AiHotspotSuppressLog;
import ai.lawyers.system.mapper.lawyers.AiHotspotSuppressMapper;
import ai.lawyers.system.service.lawyers.IAiHotspotSuppressService;

/**
 * 高频置底 Service 实现
 *
 * @author ai-lawyers
 */
@Service
public class AiHotspotSuppressServiceImpl implements IAiHotspotSuppressService
{
    private static final Logger log = LoggerFactory.getLogger(AiHotspotSuppressServiceImpl.class);

    private static final String MATCH_PHONE = "PHONE";
    private static final String MATCH_KEYWORD = "KEYWORD";

    @Autowired
    private AiHotspotSuppressMapper suppressMapper;

    @Override
    public List<AiHotspotSuppress> selectSuppressList(AiHotspotSuppress query)
    {
        return suppressMapper.selectSuppressList(query);
    }

    @Override
    public AiHotspotSuppress selectSuppressById(Long suppressId)
    {
        return suppressMapper.selectSuppressById(suppressId);
    }

    @Override
    public int insertSuppress(AiHotspotSuppress suppress)
    {
        normalizeAndValidate(suppress);
        return suppressMapper.insertSuppress(suppress);
    }

    @Override
    public int updateSuppress(AiHotspotSuppress suppress)
    {
        if (suppress.getSuppressId() == null)
        {
            throw new ServiceException("规则ID不能为空");
        }
        normalizeAndValidate(suppress);
        return suppressMapper.updateSuppress(suppress);
    }

    @Override
    public int deleteSuppressByIds(Long[] suppressIds)
    {
        return suppressMapper.deleteSuppressByIds(suppressIds);
    }

    @Override
    public List<AiHotspotSuppressLog> selectLogList(AiHotspotSuppressLog query)
    {
        return suppressMapper.selectLogList(query);
    }

    @Override
    public AiHotspotSuppress matchInbound(String callerNumber, String content, String channelUuid, String calleeNumber)
    {
        // 号码规则：查全部规则后按生效条件在内存过滤（规则量级有限）
        List<AiHotspotSuppress> rules = suppressMapper.selectSuppressList(new AiHotspotSuppress());
        if (rules == null || rules.isEmpty())
        {
            return null;
        }
        for (AiHotspotSuppress rule : rules)
        {
            boolean matched = false;
            if (MATCH_PHONE.equals(rule.getMatchType()))
            {
                matched = StringUtils.isNotEmpty(callerNumber)
                        && callerNumber.equals(rule.getMatchValue());
            }
            else if (MATCH_KEYWORD.equals(rule.getMatchType()))
            {
                matched = StringUtils.isNotEmpty(content)
                        && content.contains(rule.getMatchValue());
            }
            if (matched && isActive(rule))
            {
                // 先留痕累计，再判定窗口内是否已达频次阈值；未达阈值仅记录、不处置
                int recent = recordHit(rule, callerNumber, channelUuid, calleeNumber);
                if (withinFrequency(rule, recent))
                {
                    return rule;
                }
            }
        }
        return null;
    }

    /** 规则是否在启用且在生效期内 */
    private boolean isActive(AiHotspotSuppress rule)
    {
        if (!"0".equals(rule.getStatus()))
        {
            return false;
        }
        Date now = new Date();
        if (rule.getEffectiveStart() != null && now.before(rule.getEffectiveStart()))
        {
            return false;
        }
        if (rule.getEffectiveEnd() != null && now.after(rule.getEffectiveEnd()))
        {
            return false;
        }
        return true;
    }

    /**
     * 频次阈值：未配置（triggerCount=0）直接处置；否则要求时间窗内累计命中数已达阈值。
     * 即"高频"语义——累计到一定次数才处置，避免首次来电即被置底。
     */
    private boolean withinFrequency(AiHotspotSuppress rule, int recent)
    {
        Integer trigger = rule.getTriggerCount();
        if (trigger == null || trigger <= 0)
        {
            return true;
        }
        return recent >= trigger;
    }

    /** 记录命中：日志 + 累计计数；返回当前时间窗内（含本次）命中数，留痕失败返回 0 */
    private int recordHit(AiHotspotSuppress rule, String callerNumber,
                          String channelUuid, String calleeNumber)
    {
        try
        {
            AiHotspotSuppressLog hit = new AiHotspotSuppressLog();
            hit.setSuppressId(rule.getSuppressId());
            hit.setRuleName(rule.getRuleName());
            hit.setDirection("INBOUND");
            hit.setMatchType(rule.getMatchType());
            hit.setMatchValue(rule.getMatchValue());
            hit.setCallerNumber(callerNumber);
            hit.setCalleeNumber(calleeNumber);
            hit.setAction(rule.getAction());
            hit.setPriority(rule.getPriorityLevel());
            hit.setChannelUuid(channelUuid);
            suppressMapper.insertLog(hit);
            suppressMapper.incrementHitCount(rule.getSuppressId());

            Integer window = rule.getWindowSeconds();
            Date windowStart = window != null && window > 0
                    ? new Date(System.currentTimeMillis() - window * 1000L)
                    : new Date(0);
            return suppressMapper.countRecentHits(rule.getSuppressId(), windowStart);
        }
        catch (Exception ex)
        {
            // 命中留痕失败不阻断主处置：无频次要求时照常返回规则
            log.warn("[Hotspot] 命中日志写入失败 rule={}: {}", rule.getRuleName(), ex.getMessage());
            Integer trigger = rule.getTriggerCount();
            return trigger == null || trigger <= 0 ? 1 : 0;
        }
    }

    /** 保存前归一与校验 */
    private void normalizeAndValidate(AiHotspotSuppress suppress)
    {
        if (StringUtils.isEmpty(suppress.getRuleName()))
        {
            throw new ServiceException("规则名称不能为空");
        }
        if (StringUtils.isEmpty(suppress.getMatchType()))
        {
            suppress.setMatchType(MATCH_PHONE);
        }
        if (StringUtils.isEmpty(suppress.getMatchValue()))
        {
            throw new ServiceException("匹配值不能为空");
        }
        if (StringUtils.isEmpty(suppress.getAction()))
        {
            suppress.setAction("PRIORITY");
        }
        if (suppress.getPriorityLevel() == null)
        {
            suppress.setPriorityLevel(-100);
        }
        if (suppress.getTriggerCount() == null)
        {
            suppress.setTriggerCount(0);
        }
        if (suppress.getWindowSeconds() == null)
        {
            suppress.setWindowSeconds(0);
        }
        if (StringUtils.isEmpty(suppress.getStatus()))
        {
            suppress.setStatus("0");
        }
        // 置底动作优先级必须为负，否则无法沉底
        if ("PRIORITY".equals(suppress.getAction()) && suppress.getPriorityLevel() >= 0)
        {
            throw new ServiceException("置底降权动作的优先级必须为负数");
        }
    }
}
