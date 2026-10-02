package ai.lawyers.web.controller.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.risk.AiCallRiskRecord;
import ai.lawyers.system.service.lawyers.IAiCallBehaviorRiskService;

/**
 * 呼叫行为风控 Controller（P2-15）
 *
 * <p>权限复用高频置底 lawyers:hotspot:*（风控是置底规则的自动化上游，
 * 同一批班组长角色使用），避免新增菜单 SQL；如需细粒度可后续扩展
 * lawyers:callRisk:* 权限串。</p>
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/callRisk")
public class AiCallBehaviorRiskController extends BaseController
{
    @Autowired
    private IAiCallBehaviorRiskService riskService;

    /** 风控评估记录列表 */
    @PreAuthorize("@ss.hasPermi('lawyers:hotspot:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiCallRiskRecord query)
    {
        startPage();
        List<AiCallRiskRecord> list = riskService.selectRiskList(query);
        return getDataTable(list);
    }

    /** 手动触发一次扫描（口径与定时任务一致） */
    @PreAuthorize("@ss.hasPermi('lawyers:hotspot:edit')")
    @Log(title = "呼叫行为风控", businessType = BusinessType.OTHER)
    @PostMapping("/scan")
    public AjaxResult scan()
    {
        return success(riskService.scanRisk());
    }

    /** 复核：reviewStatus 1确认（未生成置底规则则生成） 2忽略 */
    @PreAuthorize("@ss.hasPermi('lawyers:hotspot:edit')")
    @Log(title = "呼叫行为风控复核", businessType = BusinessType.UPDATE)
    @PostMapping("/review")
    public AjaxResult review(@RequestBody AiCallRiskRecord review)
    {
        review.setReviewBy(SecurityUtils.getUsername());
        return toAjax(riskService.reviewRisk(review));
    }
}
