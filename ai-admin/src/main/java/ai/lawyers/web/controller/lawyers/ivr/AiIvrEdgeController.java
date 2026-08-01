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
import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrEdgeService;

@RestController
@RequestMapping("/lawyers/ivr/edge")
public class AiIvrEdgeController extends BaseController
{
    @Autowired
    private IAiIvrEdgeService aiIvrEdgeService;

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:edge:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiIvrEdge aiIvrEdge)
    {
        startPage();
        List<AiIvrEdge> list = aiIvrEdgeService.selectAiIvrEdgeList(aiIvrEdge);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:edge:list')")
    @GetMapping("/flow/{flowId}")
    public AjaxResult getEdgesByFlowId(@PathVariable("flowId") Long flowId)
    {
        return success(aiIvrEdgeService.selectAiIvrEdgeByFlowId(flowId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:edge:export')")
    @Log(title = "IVR连线", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiIvrEdge aiIvrEdge)
    {
        List<AiIvrEdge> list = aiIvrEdgeService.selectAiIvrEdgeList(aiIvrEdge);
        ExcelUtil<AiIvrEdge> util = new ExcelUtil<AiIvrEdge>(AiIvrEdge.class);
        util.exportExcel(response, list, "IVR连线数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:edge:query')")
    @GetMapping(value = "/{edgeId}")
    public AjaxResult getInfo(@PathVariable("edgeId") Long edgeId)
    {
        return success(aiIvrEdgeService.selectAiIvrEdgeByEdgeId(edgeId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:edge:add')")
    @Log(title = "IVR连线", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiIvrEdge aiIvrEdge)
    {
        aiIvrEdge.setCreateBy(getUsername());
        return toAjax(aiIvrEdgeService.insertAiIvrEdge(aiIvrEdge));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:edge:edit')")
    @Log(title = "IVR连线", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiIvrEdge aiIvrEdge)
    {
        aiIvrEdge.setUpdateBy(getUsername());
        return toAjax(aiIvrEdgeService.updateAiIvrEdge(aiIvrEdge));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:edge:remove')")
    @Log(title = "IVR连线", businessType = BusinessType.DELETE)
    @DeleteMapping("/{edgeIds}")
    public AjaxResult remove(@PathVariable Long[] edgeIds)
    {
        return toAjax(aiIvrEdgeService.deleteAiIvrEdgeByEdgeIds(edgeIds));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:edge:remove')")
    @Log(title = "IVR连线", businessType = BusinessType.DELETE)
    @DeleteMapping("/flow/{flowId}")
    public AjaxResult removeByFlowId(@PathVariable Long flowId)
    {
        return toAjax(aiIvrEdgeService.deleteAiIvrEdgeByFlowId(flowId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:edge:add')")
    @Log(title = "批量保存IVR连线", businessType = BusinessType.INSERT)
    @PostMapping("/batch/{flowId}")
    public AjaxResult batchAdd(@PathVariable Long flowId, @RequestBody List<AiIvrEdge> edges)
    {
        return toAjax(aiIvrEdgeService.saveFlowEdges(flowId, edges));
    }
}
