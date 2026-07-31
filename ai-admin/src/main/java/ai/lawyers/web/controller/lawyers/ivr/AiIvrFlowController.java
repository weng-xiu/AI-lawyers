package ai.lawyers.web.controller.lawyers.ivr;

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
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrFlowService;

@RestController
@RequestMapping("/lawyers/ivr/flow")
public class AiIvrFlowController extends BaseController
{
    @Autowired
    private IAiIvrFlowService aiIvrFlowService;

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:flow:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiIvrFlow aiIvrFlow)
    {
        startPage();
        List<AiIvrFlow> list = aiIvrFlowService.selectAiIvrFlowList(aiIvrFlow);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:flow:export')")
    @Log(title = "IVR流程", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiIvrFlow aiIvrFlow)
    {
        List<AiIvrFlow> list = aiIvrFlowService.selectAiIvrFlowList(aiIvrFlow);
        ExcelUtil<AiIvrFlow> util = new ExcelUtil<AiIvrFlow>(AiIvrFlow.class);
        util.exportExcel(response, list, "IVR流程数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:flow:query')")
    @GetMapping(value = "/{flowId}")
    public AjaxResult getInfo(@PathVariable("flowId") Long flowId)
    {
        return success(aiIvrFlowService.selectAiIvrFlowByFlowId(flowId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:flow:add')")
    @Log(title = "IVR流程", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiIvrFlow aiIvrFlow)
    {
        aiIvrFlow.setCreateBy(getUsername());
        return toAjax(aiIvrFlowService.insertAiIvrFlow(aiIvrFlow));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:flow:edit')")
    @Log(title = "IVR流程", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiIvrFlow aiIvrFlow)
    {
        aiIvrFlow.setUpdateBy(getUsername());
        return toAjax(aiIvrFlowService.updateAiIvrFlow(aiIvrFlow));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:flow:remove')")
    @Log(title = "IVR流程", businessType = BusinessType.DELETE)
    @DeleteMapping("/{flowIds}")
    public AjaxResult remove(@PathVariable Long[] flowIds)
    {
        return toAjax(aiIvrFlowService.deleteAiIvrFlowByFlowIds(flowIds));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:flow:list')")
    @GetMapping("/published")
    public AjaxResult getPublishedFlows()
    {
        return success(aiIvrFlowService.selectPublishedFlows());
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:flow:list')")
    @GetMapping("/default")
    public AjaxResult getDefaultFlow()
    {
        return success(aiIvrFlowService.selectDefaultFlow());
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:flow:publish')")
    @Log(title = "IVR流程发布", businessType = BusinessType.UPDATE)
    @PostMapping("/publish/{flowId}")
    public AjaxResult publish(@PathVariable Long flowId)
    {
        return toAjax(aiIvrFlowService.publishFlow(flowId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:flow:edit')")
    @Log(title = "IVR流程设计保存", businessType = BusinessType.UPDATE)
    @PostMapping("/design")
    public AjaxResult saveDesign(@RequestBody AiIvrFlow aiIvrFlow)
    {
        return toAjax(aiIvrFlowService.saveFlowDesign(aiIvrFlow.getFlowId(), aiIvrFlow.getFlowData()));
    }
}
