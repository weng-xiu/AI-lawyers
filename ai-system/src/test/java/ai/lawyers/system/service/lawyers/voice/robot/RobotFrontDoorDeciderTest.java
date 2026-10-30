package ai.lawyers.system.service.lawyers.voice.robot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.service.lawyers.IAiCallBlacklistService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-1：{@link RobotFrontDoorDecider} 纯规则决策单测。
 *
 * <p>时段用例以当前时刻动态计算，避免依赖固定系统时钟：构造"包含当前时刻"或
 * "完全不包含当前时刻"的窗口来分别命中/落空。</p>
 */
class RobotFrontDoorDeciderTest
{
    private RobotFrontDoorDecider decider;

    @Mock
    private IAiCallBlacklistService blacklistService;

    private static final String CALLER = "13800138000";

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        decider = new RobotFrontDoorDecider();
        ReflectionTestUtils.setField(decider, "blacklistService", blacklistService);
    }

    /** 打开两道开关、不限制时段（active-hours 留空=全天） */
    private void enableRobot(String activeHours)
    {
        ReflectionTestUtils.setField(decider, "frontDoorEnabled", true);
        ReflectionTestUtils.setField(decider, "robotEnabled", true);
        ReflectionTestUtils.setField(decider, "activeHours", activeHours);
        ReflectionTestUtils.setField(decider, "timezone", "Asia/Shanghai");
    }

    @Test
    void frontDoorDisabled_neverTakesOver()
    {
        enableRobot("");
        ReflectionTestUtils.setField(decider, "frontDoorEnabled", false);

        assertThat(decider.shouldRobotTakeOver(CALLER)).isFalse();
        verify(blacklistService, never()).isWhitelisted(CALLER);
    }

    @Test
    void robotEngineDisabled_neverTakesOver()
    {
        enableRobot("");
        ReflectionTestUtils.setField(decider, "robotEnabled", false);

        assertThat(decider.shouldRobotTakeOver(CALLER)).isFalse();
    }

    @Test
    void emptyActiveHours_meansAllDay_takesOver()
    {
        enableRobot("");

        assertThat(decider.shouldRobotTakeOver(CALLER)).isTrue();
    }

    @Test
    void withinActiveHours_takesOver()
    {
        enableRobot(sameDayWindow());

        assertThat(decider.shouldRobotTakeOver(CALLER)).isTrue();
    }

    @Test
    void outsideActiveHours_doesNotTakeOver()
    {
        enableRobot(farAwayWindow());

        assertThat(decider.shouldRobotTakeOver(CALLER)).isFalse();
        verify(blacklistService, never()).isWhitelisted(CALLER);
    }
    @Test
    void whitelistedNumber_bypassesRobot()
    {
        enableRobot("");
        when(blacklistService.isWhitelisted(CALLER)).thenReturn(true);

        assertThat(decider.shouldRobotTakeOver(CALLER)).isFalse();
    }

    @Test
    void nonWhitelistedNumber_takesOver()
    {
        enableRobot("");
        when(blacklistService.isWhitelisted(CALLER)).thenReturn(false);

        assertThat(decider.shouldRobotTakeOver(CALLER)).isTrue();
    }

    @Test
    void malformedActiveHours_failsSafe_doesNotTakeOver()
    {
        enableRobot("not-a-range");

        assertThat(decider.shouldRobotTakeOver(CALLER)).isFalse();
    }

    @Test
    void emptyCaller_takesOverWithoutWhitelistLookupCrash()
    {
        enableRobot("");

        assertThat(decider.shouldRobotTakeOver(null)).isTrue();
        verify(blacklistService, never()).isWhitelisted(null);
    }

    /**
     * 构造一个包含当前时刻、且保证不跨午夜的窗口（解析器不支持跨午夜区间）。
     * 窗口对称取 30 分钟余量，到当日 00:00 / 23:59 边界处自动收窄，至少保留 1 分钟。
     */
    private static String sameDayWindow()
    {
        java.time.LocalTime now = java.time.LocalTime.now();
        java.time.LocalTime dayStart = java.time.LocalTime.of(0, 0);
        java.time.LocalTime dayEnd = java.time.LocalTime.of(23, 59, 59);
        java.time.LocalTime start = now.minusMinutes(30).isAfter(dayStart) ? now.minusMinutes(30) : dayStart;
        java.time.LocalTime end = now.plusMinutes(30).isBefore(dayEnd) ? now.plusMinutes(30) : dayEnd;
        // 解析器不支持跨午夜区间：LocalTime 环绕会导致 start>now / end<now（如 23:34 时
        // 30 分钟余量回推/前推越过 00:00），此时收窄到当日边界，保证区间始终落在同一天
        // 且包含当前时刻（23:30–23:59 附近不再误判为落空）
        if (start.isAfter(now))
        {
            start = dayStart;
        }
        if (end.isBefore(now))
        {
            end = dayEnd;
        }
        return start.toString() + "-" + end.toString();
    }

    /**
     * 取一个保证不包含当前时刻的固定窗口。两个互斥窗口（凌晨/午后）中至少有一个
     * 不覆盖当前时刻，据此选择，保证"落空"断言稳定。
     */
    private static String farAwayWindow()
    {
        java.time.LocalTime now = java.time.LocalTime.now();
        java.time.LocalTime morningStart = java.time.LocalTime.of(1, 0);
        java.time.LocalTime morningEnd = java.time.LocalTime.of(3, 0);
        boolean inMorning = !now.isBefore(morningStart) && now.isBefore(morningEnd);
        if (inMorning)
        {
            return "13:00-15:00";
        }
        return "01:00-03:00";
    }
}
