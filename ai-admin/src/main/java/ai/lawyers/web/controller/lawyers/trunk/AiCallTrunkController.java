package ai.lawyers.web.controller.lawyers.trunk;

import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.utils.poi.ExcelUtil;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.domain.lawyers.trunk.AiNumberSegment;
import ai.lawyers.system.service.lawyers.trunk.IAiCallTrunkService;
import ai.lawyers.system.service.lawyers.trunk.ICarrierRouteService;
import ai.lawyers.system.service.lawyers.trunk.gateway.GatewayHealth;

/**
 * 运营商中继线路管理 Controller
 */
@RestController
@RequestMapping("/lawyers/trunk")
public class AiCallTrunkController extends BaseController
{
    @Autowired
    private IAiCallTrunkService aiCallTrunkService;

    @Autowired
    private ICarrierRouteService carrierRouteService;

    @PreAuthorize("@ss.hasPermi('lawyers:trunk:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiCallTrunk aiCallTrunk)
    {
        startPage();
        List<AiCallTrunk> list = aiCallTrunkService.selectAiCallTrunkList(aiCallTrunk);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:trunk:export')")
    @Log(title = "运营商线路", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiCallTrunk aiCallTrunk)
    {
        List<AiCallTrunk> list = aiCallTrunkService.selectAiCallTrunkList(aiCallTrunk);
        ExcelUtil<AiCallTrunk> util = new ExcelUtil<AiCallTrunk>(AiCallTrunk.class);
        util.exportExcel(response, list, "运营商线路数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:trunk:query')")
    @GetMapping(value = "/{trunkId}")
    public AjaxResult getInfo(@PathVariable("trunkId") Long trunkId)
    {
        return success(aiCallTrunkService.selectAiCallTrunkByTrunkId(trunkId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:trunk:add')")
    @Log(title = "运营商线路", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiCallTrunk aiCallTrunk)
    {
        aiCallTrunk.setCreateBy(SecurityUtils.getUsername());
        return toAjax(aiCallTrunkService.insertAiCallTrunk(aiCallTrunk));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:trunk:edit')")
    @Log(title = "运营商线路", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiCallTrunk aiCallTrunk)
    {
        aiCallTrunk.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(aiCallTrunkService.updateAiCallTrunk(aiCallTrunk));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:trunk:edit')")
    @Log(title = "运营商线路-启停", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody AiCallTrunk aiCallTrunk)
    {
        return toAjax(aiCallTrunkService.changeEnableFlag(
                aiCallTrunk.getTrunkId(), aiCallTrunk.getEnableFlag()));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:trunk:remove')")
    @Log(title = "运营商线路", businessType = BusinessType.DELETE)
    @DeleteMapping("/{trunkIds}")
    public AjaxResult remove(@PathVariable Long[] trunkIds)
    {
        return toAjax(aiCallTrunkService.deleteAiCallTrunkByTrunkIds(trunkIds));
    }

    /**
     * 手动测试线路连通性
     */
    @PreAuthorize("@ss.hasPermi('lawyers:trunk:test')")
    @Log(title = "运营商线路-连通测试", businessType = BusinessType.OTHER)
    @PostMapping("/test/{trunkId}")
    public AjaxResult test(@PathVariable("trunkId") Long trunkId)
    {
        GatewayHealth health = aiCallTrunkService.testTrunk(trunkId);
        AjaxResult result = health.isReachable() ? AjaxResult.success("线路连通正常")
                : AjaxResult.error("线路不可达: " + health.getMessage());
        result.put("reachable", health.isReachable());
        result.put("latencyMs", health.getLatencyMs());
        result.put("registerState", health.getRegisterState());
        return result;
    }

    // ---------------------------------------------------------------- 号段管理

    @PreAuthorize("@ss.hasPermi('lawyers:trunk:list')")
    @GetMapping("/segment/list")
    public TableDataInfo segmentList(AiNumberSegment aiNumberSegment)
    {
        startPage();
        List<AiNumberSegment> list = aiCallTrunkService.selectAiNumberSegmentList(aiNumberSegment);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:trunk:add')")
    @Log(title = "号段路由", businessType = BusinessType.INSERT)
    @PostMapping("/segment")
    public AjaxResult segmentAdd(@RequestBody AiNumberSegment aiNumberSegment)
    {
        aiNumberSegment.setCreateBy(SecurityUtils.getUsername());
        return toAjax(aiCallTrunkService.insertAiNumberSegment(aiNumberSegment));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:trunk:edit')")
    @Log(title = "号段路由", businessType = BusinessType.UPDATE)
    @PutMapping("/segment")
    public AjaxResult segmentEdit(@RequestBody AiNumberSegment aiNumberSegment)
    {
        aiNumberSegment.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(aiCallTrunkService.updateAiNumberSegment(aiNumberSegment));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:trunk:remove')")
    @Log(title = "号段路由", businessType = BusinessType.DELETE)
    @DeleteMapping("/segment/{segmentIds}")
    public AjaxResult segmentRemove(@PathVariable Long[] segmentIds)
    {
        return toAjax(aiCallTrunkService.deleteAiNumberSegmentBySegmentIds(segmentIds));
    }

    /**
     * 号码运营商识别测试
     */
    @PreAuthorize("@ss.hasPermi('lawyers:trunk:query')")
    @GetMapping("/recognize")
    public AjaxResult recognize(@RequestParam("number") String number)
    {
        AjaxResult result = AjaxResult.success();
        String carrier = carrierRouteService.recognizeCarrier(number);
        result.put("number", number);
        result.put("carrier", carrier);
        result.put("carrierName", ai.lawyers.system.enums.CarrierEnum.infoOf(carrier));
        result.put("segment", carrierRouteService.recognizeSegment(number));
        result.put("candidateTrunks", carrierRouteService.selectCandidateTrunks(carrier, null));
        return result;
    }

    /**
     * 刷新号段缓存
     */
    @PreAuthorize("@ss.hasPermi('lawyers:trunk:edit')")
    @PostMapping("/segment/refresh")
    public AjaxResult refreshSegment()
    {
        carrierRouteService.refreshSegmentCache();
        return success("号段缓存已刷新");
    }
}
