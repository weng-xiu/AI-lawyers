package ai.lawyers.system.service.lawyers.voice.robot;

import java.time.LocalTime;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.service.lawyers.IAiCallBlacklistService;

/**
 * P0-1：机器人前置分流"决策层"（纯规则，零网络、零模型调用）。
 *
 * <p>入站主链路在话单建立后、人工 ACD 分配前调用 {@link #shouldRobotTakeOver}，
 * 决定该通来电是否应由语音机器人先接。判定顺序：</p>
 * <ol>
 *   <li>前置分流总开关（{@code ai.voice-robot.front-door-enabled}，默认关闭）；</li>
 *   <li>机器人对话引擎总开关（{@code ai.voice-robot.enabled}，默认关闭），
 *       引擎未就绪不允许前置分流；</li>
 *   <li>每日生效时段（{@code active-hours}，多段 {@code HH:mm-HH:mm} 逗号分隔，
 *       留空=全天），用于夜间/溢出时段先灰度；</li>
 *   <li>白名单直通（命中 {@code isWhitelisted} 的号码直接走人工、不经机器人）。</li>
 * </ol>
 *
 * <p>本类只做<b>决策</b>，不负责媒体接管。真正的"机器人接听 + 双向播报 + 转人工
 * 媒体交回"需在 FreeSWITCH 入站桥接就绪后于入站处理器内接线，当前未就绪。</p>
 *
 * @author ai-lawyers
 */
@Component
public class RobotFrontDoorDecider
{
    private static final Logger log = LoggerFactory.getLogger(RobotFrontDoorDecider.class);

    /** 前置分流总开关（独立于对话引擎开关），默认关闭 */
    @Value("${ai.voice-robot.front-door-enabled:false}")
    private boolean frontDoorEnabled;

    /** 机器人对话引擎总开关：引擎未就绪时前置分流不得生效 */
    @Value("${ai.voice-robot.enabled:false}")
    private boolean robotEnabled;

    /** 机器人前置分流每日生效时段，格式 "HH:mm-HH:mm"，多段逗号分隔；留空=全天 */
    @Value("${ai.voice-robot.active-hours:}")
    private String activeHours;

    /** 时区 */
    @Value("${ai.voice-robot.timezone:Asia/Shanghai}")
    private String timezone;

    @Autowired(required = false)
    private IAiCallBlacklistService blacklistService;

    /**
     * 判定该通来电是否应由机器人前置接管。
     *
     * @param caller 主叫号码
     * @return true=机器人接管；false=走正常人工分配
     */
    public boolean shouldRobotTakeOver(String caller)
    {
        if (!frontDoorEnabled || !robotEnabled)
        {
            return false;
        }
        if (!isWithinActiveHours())
        {
            return false;
        }
        // 白名单号码直通人工，跳过机器人
        if (StringUtils.isNotEmpty(caller) && blacklistService != null
                && blacklistService.isWhitelisted(caller))
        {
            log.info("[FrontDoor] 白名单号码直通人工，跳过机器人: {}", caller);
            return false;
        }
        return true;
    }

    /**
     * 当前是否处于机器人前置分流的每日生效时段。
     * active-hours 留空表示全天生效；配置解析失败时安全回退为不接管。
     */
    private boolean isWithinActiveHours()
    {
        if (StringUtils.isEmpty(activeHours))
        {
            return true;
        }
        try
        {
            LocalTime now = LocalTime.now(ZoneId.of(timezone));
            String[] segments = activeHours.split(",");
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
            log.warn("[FrontDoor] 生效时段配置解析失败，回退不接管 active-hours={}: {}",
                    activeHours, e.getMessage());
            return false;
        }
    }
}
