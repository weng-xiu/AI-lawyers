package ai.lawyers.web.controller.lawyers.schedule;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.schedule.AiWorkShift;
import ai.lawyers.system.service.lawyers.schedule.IAiWorkShiftService;

/**
 * 班次定义 Controller
 */
@RestController
@RequestMapping("/lawyers/schedule/shift")
public class AiWorkShiftController extends BaseController
{
    @Autowired
    private IAiWorkShiftService aiWorkShiftService;

    /**
     * 查询班次列表
     */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiWorkShift aiWorkShift)
    {
        startPage();
        List<AiWorkShift> list = aiWorkShiftService.selectAiWorkShiftList(aiWorkShift);
        return getDataTable(list);
    }

    /**
     * 查询全部启用班次（下拉框使用）
     */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:list')")
    @GetMapping("/enabled")
    public AjaxResult enabled()
    {
        return success(aiWorkShiftService.selectAllEnabled());
    }

    /**
     * 获取班次详细信息
     */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:query')")
    @GetMapping(value = "/{shiftId}")
    public AjaxResult getInfo(@PathVariable("shiftId") Long shiftId)
    {
        return success(aiWorkShiftService.selectAiWorkShiftByShiftId(shiftId));
    }

    /**
     * 新增班次
     */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:add')")
    @Log(title = "班次定义", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiWorkShift aiWorkShift)
    {
        aiWorkShift.setCreateBy(SecurityUtils.getUsername());
        return toAjax(aiWorkShiftService.insertAiWorkShift(aiWorkShift));
    }

    /**
     * 修改班次
     */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:edit')")
    @Log(title = "班次定义", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiWorkShift aiWorkShift)
    {
        aiWorkShift.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(aiWorkShiftService.updateAiWorkShift(aiWorkShift));
    }

    /**
     * 删除班次
     */
    @PreAuthorize("@ss.hasPermi('lawyers:schedule:remove')")
    @Log(title = "班次定义", businessType = BusinessType.DELETE)
    @DeleteMapping("/{shiftIds}")
    public AjaxResult remove(@PathVariable Long[] shiftIds)
    {
        return toAjax(aiWorkShiftService.deleteAiWorkShiftByShiftIds(shiftIds));
    }
}
