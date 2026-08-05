package ai.lawyers.web.controller.lawyers;

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
import ai.lawyers.system.domain.lawyers.AiNotice;
import ai.lawyers.system.service.lawyers.IAiNoticeService;

@RestController
@RequestMapping("/lawyers/workbench/notice")
public class AiNoticeController extends BaseController
{
    @Autowired
    private IAiNoticeService aiNoticeService;

    @PreAuthorize("@ss.hasPermi('lawyers:workbench:notice:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiNotice aiNotice)
    {
        startPage();
        List<AiNotice> list = aiNoticeService.selectAiNoticeList(aiNotice);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:workbench:notice:export')")
    @Log(title = "工作台公告", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiNotice aiNotice)
    {
        List<AiNotice> list = aiNoticeService.selectAiNoticeList(aiNotice);
        ExcelUtil<AiNotice> util = new ExcelUtil<AiNotice>(AiNotice.class);
        util.exportExcel(response, list, "工作台公告数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:workbench:notice:query')")
    @GetMapping(value = "/{noticeId}")
    public AjaxResult getInfo(@PathVariable("noticeId") Long noticeId)
    {
        return success(aiNoticeService.selectAiNoticeByNoticeId(noticeId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:workbench:notice:add')")
    @Log(title = "工作台公告", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiNotice aiNotice)
    {
        aiNotice.setCreateBy(getUsername());
        return toAjax(aiNoticeService.insertAiNotice(aiNotice));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:workbench:notice:edit')")
    @Log(title = "工作台公告", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiNotice aiNotice)
    {
        aiNotice.setUpdateBy(getUsername());
        return toAjax(aiNoticeService.updateAiNotice(aiNotice));
    }

    /** 发布公告：状态置为发布并记录发布时间 */
    @PreAuthorize("@ss.hasPermi('lawyers:workbench:notice:edit')")
    @Log(title = "发布公告", businessType = BusinessType.OTHER)
    @PutMapping("/publish/{noticeId}")
    public AjaxResult publish(@PathVariable("noticeId") Long noticeId)
    {
        AiNotice notice = new AiNotice();
        notice.setNoticeId(noticeId);
        notice.setStatus("1");
        notice.setPublishTime(new java.util.Date());
        notice.setUpdateBy(getUsername());
        return toAjax(aiNoticeService.updateAiNotice(notice));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:workbench:notice:remove')")
    @Log(title = "工作台公告", businessType = BusinessType.DELETE)
    @DeleteMapping("/{noticeIds}")
    public AjaxResult remove(@PathVariable Long[] noticeIds)
    {
        return toAjax(aiNoticeService.deleteAiNoticeByNoticeIds(noticeIds));
    }
}
