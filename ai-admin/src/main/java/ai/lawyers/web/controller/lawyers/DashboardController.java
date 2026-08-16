package ai.lawyers.web.controller.lawyers;

import java.util.Calendar;
import java.util.Date;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.system.service.lawyers.stat.IDashboardService;

/**
 * B4 运营大屏聚合接口
 */
@RestController
@RequestMapping("/lawyers/dashboard")
public class DashboardController extends BaseController
{
    @Autowired
    private IDashboardService dashboardService;

    /** 大屏聚合数据（一次拉取全部模块） */
    @PreAuthorize("@ss.hasPermi('lawyers:dashboard:view')")
    @GetMapping("/data")
    public AjaxResult data(@RequestParam(value = "beginTime", required = false) String beginTime,
                           @RequestParam(value = "endTime", required = false) String endTime)
    {
        Date begin = DateUtils.parseDate(beginTime);
        Date end = DateUtils.parseDate(endTime);
        // 未指定起止时间时默认统计今日
        if (begin == null || end == null)
        {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            if (begin == null)
            {
                begin = cal.getTime();
            }
            if (end == null)
            {
                end = new Date();
            }
        }
        return success(dashboardService.getDashboardData(begin, end));
    }
}
