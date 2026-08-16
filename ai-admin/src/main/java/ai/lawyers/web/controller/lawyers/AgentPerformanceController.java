package ai.lawyers.web.controller.lawyers;

import java.util.Date;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.common.utils.poi.ExcelUtil;
import ai.lawyers.system.domain.lawyers.stat.AgentPerformanceVO;
import ai.lawyers.system.service.lawyers.stat.IAgentPerformanceService;

/**
 * B4 坐席效能报表接口
 */
@RestController
@RequestMapping("/lawyers/performance/agent")
public class AgentPerformanceController extends BaseController
{
    @Autowired
    private IAgentPerformanceService agentPerformanceService;

    @PreAuthorize("@ss.hasPermi('lawyers:performance:agent:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(value = "agentName", required = false) String agentName,
                              @RequestParam(value = "beginTime", required = false) String beginTime,
                              @RequestParam(value = "endTime", required = false) String endTime)
    {
        Date begin = DateUtils.parseDate(beginTime);
        Date end = DateUtils.parseDate(endTime);
        startPage();
        List<AgentPerformanceVO> list = agentPerformanceService.selectAgentPerformanceList(agentName, begin, end);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:performance:agent:export')")
    @Log(title = "坐席效能报表", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response,
                       @RequestParam(value = "agentName", required = false) String agentName,
                       @RequestParam(value = "beginTime", required = false) String beginTime,
                       @RequestParam(value = "endTime", required = false) String endTime)
    {
        Date begin = DateUtils.parseDate(beginTime);
        Date end = DateUtils.parseDate(endTime);
        List<AgentPerformanceVO> list = agentPerformanceService.selectAgentPerformanceList(agentName, begin, end);
        ExcelUtil<AgentPerformanceVO> util = new ExcelUtil<AgentPerformanceVO>(AgentPerformanceVO.class);
        util.exportExcel(response, list, "坐席效能报表");
    }
}
