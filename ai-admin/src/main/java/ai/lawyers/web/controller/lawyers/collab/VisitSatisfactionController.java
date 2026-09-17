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
import ai.lawyers.system.service.lawyers.stat.IVisitSatisfactionService;

/**
 * F5 智能回访满意度闭环 —— 回访满意度归因分析看板接口
 *
 * <p>聚合电话回访满意度、图文咨询评价、不满意原因规则归因、情绪极性、回访任务运营与逾期分级。</p>
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/visit/satisfaction")
public class VisitSatisfactionController extends BaseController
{
    /** 默认统计近 30 天 */
    private static final int DEFAULT_DAYS = 30;

    @Autowired
    private IVisitSatisfactionService visitSatisfactionService;

    /**
     * 看板聚合数据（一次拉取全部模块）。
     *
     * @param beginTime 区间起（按回访时间/评价时间），缺省近 30 天
     * @param endTime   区间止，缺省当前时刻
     */
    @PreAuthorize("@ss.hasPermi('lawyers:visitBoard:view')")
    @GetMapping("/board")
    public AjaxResult board(@RequestParam(value = "beginTime", required = false) String beginTime,
                            @RequestParam(value = "endTime", required = false) String endTime)
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
            if (end == null)
            {
                end = new Date();
            }
            if (begin == null)
            {
                cal.add(Calendar.DAY_OF_MONTH, -DEFAULT_DAYS + 1);
                begin = cal.getTime();
            }
        }
        return success(visitSatisfactionService.getBoard(begin, end));
    }
}
