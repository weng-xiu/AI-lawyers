package ai.lawyers.web.controller.lawyers;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.system.domain.lawyers.stat.AiSatisfactionForecast;
import ai.lawyers.system.service.lawyers.IAiSatisfactionForecastService;

/**
 * 预测性满意度 Controller（P1-11）
 *
 * <p>权限复用报表 lawyers:report:view（满意度报表同源消费方），避免新增
 * 菜单 SQL；如需细粒度可后续扩展 lawyers:satisfactionForecast:* 权限串。</p>
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/satisfactionForecast")
public class AiSatisfactionForecastController extends BaseController
{
    @Autowired
    private IAiSatisfactionForecastService forecastService;

    /** 区间预测汇总（分布/均分/实际对照一致率） */
    @PreAuthorize("@ss.hasPermi('lawyers:report:view')")
    @GetMapping("/summary")
    public AjaxResult summary(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date beginTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime)
    {
        Map<String, Object> summary = forecastService.summary(beginTime, endTime);
        return success(summary);
    }

    /** 区间逐条预测明细（分页） */
    @PreAuthorize("@ss.hasPermi('lawyers:report:view')")
    @GetMapping("/list")
    public TableDataInfo list(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date beginTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize)
    {
        List<AiSatisfactionForecast> list = forecastService.list(beginTime, endTime, pageNum, pageSize);
        return getDataTable(list);
    }
}
