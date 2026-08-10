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
import ai.lawyers.system.domain.lawyers.AiPaidLegalService;
import ai.lawyers.system.service.lawyers.IAiPaidLegalService;

/**
 * 有偿法律服务Controller
 * 
 * @author AI Lawyers
 */
@RestController
@RequestMapping("/lawyers/paidLegalService")
public class AiPaidLegalServiceController extends BaseController
{
    @Autowired
    private IAiPaidLegalService paidService;

    /**
     * 查询有偿法律服务列表
     */
    @PreAuthorize("@ss.hasPermi('lawyers:paidService:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiPaidLegalService query)
    {
        startPage();
        List<AiPaidLegalService> list = paidService.selectAiPaidLegalServiceList(query);
        return getDataTable(list);
    }

    /**
     * 导出有偿法律服务列表
     */
    @PreAuthorize("@ss.hasPermi('lawyers:paidService:export')")
    @Log(title = "有偿法律服务", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiPaidLegalService query)
    {
        List<AiPaidLegalService> list = paidService.selectAiPaidLegalServiceList(query);
        ExcelUtil<AiPaidLegalService> util = new ExcelUtil<AiPaidLegalService>(AiPaidLegalService.class);
        util.exportExcel(response, list, "有偿法律服务数据");
    }

    /**
     * 获取有偿法律服务详细信息
     */
    @PreAuthorize("@ss.hasPermi('lawyers:paidService:query')")
    @GetMapping(value = "/{paidId}")
    public AjaxResult getInfo(@PathVariable("paidId") Long paidId)
    {
        return success(paidService.selectAiPaidLegalServiceByPaidId(paidId));
    }

    /**
     * 新增有偿法律服务
     */
    @PreAuthorize("@ss.hasPermi('lawyers:paidService:add')")
    @Log(title = "有偿法律服务", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiPaidLegalService data)
    {
        data.setCreateBy(getUsername());
        return toAjax(paidService.insertAiPaidLegalService(data));
    }

    /**
     * 修改有偿法律服务
     */
    @PreAuthorize("@ss.hasPermi('lawyers:paidService:edit')")
    @Log(title = "有偿法律服务", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiPaidLegalService data)
    {
        data.setUpdateBy(getUsername());
        return toAjax(paidService.updateAiPaidLegalService(data));
    }

    /**
     * 删除有偿法律服务
     */
    @PreAuthorize("@ss.hasPermi('lawyers:paidService:remove')")
    @Log(title = "有偿法律服务", businessType = BusinessType.DELETE)
    @DeleteMapping("/{paidIds}")
    public AjaxResult remove(@PathVariable Long[] paidIds)
    {
        return toAjax(paidService.deleteAiPaidLegalServiceByPaidIds(paidIds));
    }
}
