package ai.lawyers.web.controller.lawyers;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.forecast.AiForecastCalendar;
import ai.lawyers.system.service.lawyers.IAiCallForecastService;

/**
 * 话务预测与智能排班 Controller（P1-9）
 *
 * <p>权限复用报表 lawyers:report:view（预测/排班/确认属报表运营链路）；
 * 日历写操作复用 lawyers:report:export（报表运营角色），避免新增菜单 SQL，
 * 如需细粒度可后续扩展 lawyers:forecast:* 权限串。</p>
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/forecast")
public class AiCallForecastController extends BaseController
{
    @Autowired
    private IAiCallForecastService forecastService;

    /** 未来 N 天逐时话务预测 */
    @PreAuthorize("@ss.hasPermi('lawyers:report:view')")
    @GetMapping("/preview")
    public AjaxResult preview(@RequestParam(defaultValue = "7") int days)
    {
        return success(forecastService.preview(days));
    }

    /** 排班建议（预测 + 逐日峰值坐席） */
    @PreAuthorize("@ss.hasPermi('lawyers:report:view')")
    @GetMapping("/staffing")
    public AjaxResult staffing(@RequestParam(defaultValue = "7") int days)
    {
        return success(forecastService.staffing(days));
    }

    /** 班组长确认（存预测/排班快照留痕） */
    @PreAuthorize("@ss.hasPermi('lawyers:report:view')")
    @Log(title = "排班计划确认", businessType = BusinessType.INSERT)
    @PostMapping("/confirm")
    public AjaxResult confirm(@RequestBody Map<String, Object> body)
    {
        int days = body.get("days") instanceof Number ? ((Number) body.get("days")).intValue() : 7;
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        Long planId = forecastService.confirm(days, remark, SecurityUtils.getUsername());
        AjaxResult result = success(planId);
        result.put("planId", planId);
        return result;
    }

    /** 节假日/事件日历列表 */
    @PreAuthorize("@ss.hasPermi('lawyers:report:view')")
    @GetMapping("/calendar/list")
    public TableDataInfo calendarList(AiForecastCalendar query)
    {
        startPage();
        List<AiForecastCalendar> list = forecastService.selectCalendarList(query);
        return getDataTable(list);
    }

    /** 新增日历条目 */
    @PreAuthorize("@ss.hasPermi('lawyers:report:export')")
    @Log(title = "预测日历", businessType = BusinessType.INSERT)
    @PostMapping("/calendar")
    public AjaxResult addCalendar(@RequestBody AiForecastCalendar calendar)
    {
        calendar.setCreateBy(SecurityUtils.getUsername());
        return toAjax(forecastService.insertCalendar(calendar));
    }

    /** 修改日历条目 */
    @PreAuthorize("@ss.hasPermi('lawyers:report:export')")
    @Log(title = "预测日历", businessType = BusinessType.UPDATE)
    @PutMapping("/calendar")
    public AjaxResult editCalendar(@RequestBody AiForecastCalendar calendar)
    {
        calendar.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(forecastService.updateCalendar(calendar));
    }

    /** 删除日历条目 */
    @PreAuthorize("@ss.hasPermi('lawyers:report:export')")
    @Log(title = "预测日历", businessType = BusinessType.DELETE)
    @DeleteMapping("/calendar/{calendarIds}")
    public AjaxResult removeCalendar(@PathVariable Long[] calendarIds)
    {
        return toAjax(forecastService.deleteCalendarByIds(calendarIds));
    }
}
