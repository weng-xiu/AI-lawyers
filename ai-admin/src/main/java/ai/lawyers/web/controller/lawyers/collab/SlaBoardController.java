package ai.lawyers.web.controller.lawyers.collab;

import java.util.Calendar;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.system.service.lawyers.stat.ISlaBoardService;

/**
 * F9 工单 SLA 可视化看板接口
 *
 * <p>提供总体达成概况、条线/人员达成率、超时 TOP、临近超时与跨域流转统计，
 * 读物化口径前直接实时聚合工单/转办表。</p>
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/sla/board")
public class SlaBoardController extends BaseController
{
    @Autowired
    private ISlaBoardService slaBoardService;

    /**
     * 看板聚合数据（一次拉取全部模块）。
     *
     * @param beginTime 建单区间起，缺省今日 00:00
     * @param endTime   建单区间止，缺省当前时刻
     * @param warnMinutes 临近超时预警窗口分钟，缺省 60
     */
    @PreAuthorize("@ss.hasPermi('lawyers:slaBoard:view')")
    @GetMapping("/data")
    public AjaxResult data(@RequestParam(value = "beginTime", required = false) String beginTime,
                           @RequestParam(value = "endTime", required = false) String endTime,
                           @RequestParam(value = "warnMinutes", required = false) Integer warnMinutes)
    {
        Date begin = DateUtils.parseDate(beginTime);
        Date end = DateUtils.parseDate(endTime);
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
        return success(slaBoardService.getSlaBoard(begin, end, warnMinutes));
    }
}
