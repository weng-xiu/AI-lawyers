package ai.lawyers.web.controller.lawyers;

import java.util.Date;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.poi.ExcelUtil;
import ai.lawyers.system.domain.lawyers.AiVideoConsult;
import ai.lawyers.system.domain.lawyers.AiVideoConsultLog;
import ai.lawyers.system.service.lawyers.IAiVideoConsultLogService;
import ai.lawyers.system.service.lawyers.IAiVideoConsultService;

/**
 * 视频咨询Controller
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/videoConsult")
public class AiVideoConsultController extends BaseController
{
    @Autowired
    private IAiVideoConsultService aiVideoConsultService;

    @Autowired
    private IAiVideoConsultLogService aiVideoConsultLogService;

    @PreAuthorize("@ss.hasPermi('lawyers:videoConsult:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiVideoConsult aiVideoConsult)
    {
        startPage();
        List<AiVideoConsult> list = aiVideoConsultService.selectAiVideoConsultList(aiVideoConsult);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:videoConsult:export')")
    @Log(title = "视频咨询", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiVideoConsult aiVideoConsult)
    {
        List<AiVideoConsult> list = aiVideoConsultService.selectAiVideoConsultList(aiVideoConsult);
        ExcelUtil<AiVideoConsult> util = new ExcelUtil<AiVideoConsult>(AiVideoConsult.class);
        util.exportExcel(response, list, "视频咨询数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:videoConsult:query')")
    @GetMapping(value = "/{consultId}")
    public AjaxResult getInfo(@PathVariable("consultId") Long consultId)
    {
        return success(aiVideoConsultService.selectAiVideoConsultByConsultId(consultId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:videoConsult:add')")
    @Log(title = "视频咨询", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiVideoConsult video)
    {
        video.setCreateBy(getUsername());
        if (video.getAgentName() == null || video.getAgentName().isEmpty()) {
            video.setAgentName(getUsername());
        }
        if (video.getStatus() == null || video.getStatus().isEmpty()) {
            video.setStatus("0");
        }
        video.setStartTime(new Date());
        video.setConsultNo("VIDEO" + System.currentTimeMillis());
        return toAjax(aiVideoConsultService.insertAiVideoConsult(video));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:videoConsult:edit')")
    @Log(title = "视频咨询", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiVideoConsult video)
    {
        video.setUpdateBy(getUsername());
        if ("2".equals(video.getStatus()) && video.getEndTime() == null) {
            video.setEndTime(new Date());
        }
        return toAjax(aiVideoConsultService.updateAiVideoConsult(video));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:videoConsult:remove')")
    @Log(title = "视频咨询", businessType = BusinessType.DELETE)
    @DeleteMapping("/{consultIds}")
    public AjaxResult remove(@PathVariable Long[] consultIds)
    {
        return toAjax(aiVideoConsultService.deleteAiVideoConsultByConsultIds(consultIds));
    }

    /** 获取视频咨询的事件日志 */
    @PreAuthorize("@ss.hasPermi('lawyers:videoConsult:query')")
    @GetMapping("/log/{consultId}")
    public AjaxResult getLogs(@PathVariable("consultId") Long consultId)
    {
        AiVideoConsultLog log = new AiVideoConsultLog();
        log.setConsultId(consultId);
        return success(aiVideoConsultLogService.selectAiVideoConsultLogList(log));
    }

    /** 记录事件日志 */
    @PostMapping("/log")
    public AjaxResult addLog(@RequestBody AiVideoConsultLog log)
    {
        log.setEventTime(new Date());
        return toAjax(aiVideoConsultLogService.insertAiVideoConsultLog(log));
    }
}
