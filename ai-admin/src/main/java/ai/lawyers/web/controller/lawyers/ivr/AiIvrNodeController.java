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
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrNodeService;

@RestController
@RequestMapping("/lawyers/ivr/node")
public class AiIvrNodeController extends BaseController
{
    @Autowired
    private IAiIvrNodeService aiIvrNodeService;

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:node:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiIvrNode aiIvrNode)
    {
        startPage();
        List<AiIvrNode> list = aiIvrNodeService.selectAiIvrNodeList(aiIvrNode);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:node:list')")
    @GetMapping("/flow/{flowId}")
    public AjaxResult getNodesByFlowId(@PathVariable("flowId") Long flowId)
    {
        return success(aiIvrNodeService.selectAiIvrNodeByFlowId(flowId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:node:export')")
    @Log(title = "IVR节点", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiIvrNode aiIvrNode)
    {
        List<AiIvrNode> list = aiIvrNodeService.selectAiIvrNodeList(aiIvrNode);
        ExcelUtil<AiIvrNode> util = new ExcelUtil<AiIvrNode>(AiIvrNode.class);
        util.exportExcel(response, list, "IVR节点数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:node:query')")
    @GetMapping(value = "/{nodeId}")
    public AjaxResult getInfo(@PathVariable("nodeId") Long nodeId)
    {
        return success(aiIvrNodeService.selectAiIvrNodeByNodeId(nodeId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:node:add')")
    @Log(title = "IVR节点", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiIvrNode aiIvrNode)
    {
        aiIvrNode.setCreateBy(getUsername());
        return toAjax(aiIvrNodeService.insertAiIvrNode(aiIvrNode));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:node:edit')")
    @Log(title = "IVR节点", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiIvrNode aiIvrNode)
    {
        aiIvrNode.setUpdateBy(getUsername());
        return toAjax(aiIvrNodeService.updateAiIvrNode(aiIvrNode));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:node:remove')")
    @Log(title = "IVR节点", businessType = BusinessType.DELETE)
    @DeleteMapping("/{nodeIds}")
    public AjaxResult remove(@PathVariable Long[] nodeIds)
    {
        return toAjax(aiIvrNodeService.deleteAiIvrNodeByNodeIds(nodeIds));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:node:remove')")
    @Log(title = "IVR节点", businessType = BusinessType.DELETE)
    @DeleteMapping("/flow/{flowId}")
    public AjaxResult removeByFlowId(@PathVariable Long flowId)
    {
        return toAjax(aiIvrNodeService.deleteAiIvrNodeByFlowId(flowId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:ivr:node:add')")
    @Log(title = "批量保存IVR节点", businessType = BusinessType.INSERT)
    @PostMapping("/batch/{flowId}")
    public AjaxResult batchAdd(@PathVariable Long flowId, @RequestBody List<AiIvrNode> nodes)
    {
        return toAjax(aiIvrNodeService.saveFlowNodes(flowId, nodes));
    }
}
