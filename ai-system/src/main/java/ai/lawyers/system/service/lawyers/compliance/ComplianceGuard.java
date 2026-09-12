package ai.lawyers.system.service.lawyers.compliance;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.DesensitizedUtil;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallBlacklist;
import ai.lawyers.system.mapper.lawyers.AiCallBlacklistMapper;

/**
 * W4: 外呼/短信合规守卫。
 *
 * <p>统一收口三类合规拦截：</p>
 * <ol>
 *   <li>外呼时段限制（allowed-hours），避免扰民；</li>
 *   <li>退订名单拦截（短信退订后，呼叫与短信均禁止）；</li>
 *   <li>黑名单拦截（含 list_type=3 退订名单）。</li>
 * </ol>
 *
 * @author ai-lawyers
 */
@Service
public class ComplianceGuard
{
    private static final Logger log = LoggerFactory.getLogger(ComplianceGuard.class);

    /** 名单类型：退订名单（黑名单 1 / 白名单 2 / 退订 3） */
    public static final int LIST_TYPE_UNSUBSCRIBE = 3;

    @Autowired(required = false)
    private AiCallBlacklistMapper blacklistMapper;

    /** 外呼合规总开关 */
    @Value("${call.outbound.compliance.enabled:true}")
    private boolean complianceEnabled;

    /** 允许外呼时段，格式 "HH:mm-HH:mm"，多段逗号分隔 */
    @Value("${call.outbound.compliance.allowed-hours:09:00-21:00}")
    private String allowedHours;

    /** 时区 */
    @Value("${call.outbound.compliance.timezone:Asia/Shanghai}")
    private String timezone;

    /**
     * 判断当前是否处于允许外呼时段。
     *
     * @return true 允许外呼；false 当前为非服务时段，应跳过
     */
    public boolean isCallingAllowed()
    {
        if (!complianceEnabled)
        {
            return true;
        }
        if (StringUtils.isEmpty(allowedHours))
        {
            return true;
        }
        try
        {
            LocalTime now = LocalTime.now(ZoneId.of(timezone));
            String[] segments = allowedHours.split(",");
            for (String seg : segments)
            {
                String[] range = seg.trim().split("-");
                if (range.length != 2)
                {
                    continue;
                }
                LocalTime start = LocalTime.parse(range[0].trim());
                LocalTime end = LocalTime.parse(range[1].trim());
                if (now.equals(start) || now.equals(end)
                        || (now.isAfter(start) && now.isBefore(end)))
                {
                    return true;
                }
            }
            return false;
        }
        catch (Exception e)
        {
            log.warn("外呼时段配置解析失败，默认放行 allowed-hours={}: {}", allowedHours, e.getMessage());
            return true;
        }
    }

    /**
     * 判断号码是否被退订（不可外呼/发短信）。
     * 退订名单写入 ai_call_blacklist（list_type=3），实现"一处退订，呼短均禁"。
     */
    public boolean isUnsubscribed(String phone)
    {
        if (StringUtils.isEmpty(phone) || blacklistMapper == null)
        {
            return false;
        }
        try
        {
            java.util.List<AiCallBlacklist> list = blacklistMapper.selectByPhone(phone);
            if (list == null || list.isEmpty())
            {
                return false;
            }
            Date now = new Date();
            for (AiCallBlacklist item : list)
            {
                if (item.getListType() != null && item.getListType() == LIST_TYPE_UNSUBSCRIBE
                        && isEffective(item, now))
                {
                    return true;
                }
            }
            return false;
        }
        catch (Exception e)
        {
            log.warn("退订名单查询异常，默认放行 phone={}: {}", DesensitizedUtil.mobilePhone(phone), e.getMessage());
            return false;
        }
    }

    /**
     * 号码是否可外呼（时段 + 退订 + 黑名单综合判断）。
     *
     * @return null 表示允许；非 null 表示拦截原因
     */
    public String checkOutboundAllowed(String phone)
    {
        if (!isCallingAllowed())
        {
            return "OUT_OF_HOURS";
        }
        if (isUnsubscribed(phone))
        {
            return "UNSUBSCRIBED";
        }
        return null;
    }

    /**
     * 号码是否可发短信（退订拦截）。
     *
     * @return true 允许发送；false 已退订，拒绝发送
     */
    public boolean isSmsAllowed(String phone)
    {
        return !isUnsubscribed(phone);
    }

    /** 判断黑白名单条目当前是否生效 */
    private boolean isEffective(AiCallBlacklist item, Date now)
    {
        if (item.getStatus() == null || item.getStatus() != 1)
        {
            return false;
        }
        if (item.getEffectiveStart() != null && now.before(item.getEffectiveStart()))
        {
            return false;
        }
        if (item.getEffectiveEnd() != null && now.after(item.getEffectiveEnd()))
        {
            return false;
        }
        return true;
    }
}
