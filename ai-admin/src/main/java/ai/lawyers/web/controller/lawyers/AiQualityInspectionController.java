package ai.lawyers.web.controller.lawyers;

import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
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
import ai.lawyers.common.utils.poi.ExcelUtil;
import ai.lawyers.system.domain.lawyers.quality.AiQualityInspection;
import ai.lawyers.system.service.lawyers.quality.IAiQualityInspectionService;

/**
 * 智能质检 Controller（T4-1）
 *
 * <p>录音就绪后经 quality-transcribe 队列自动 ASR 转写 + AI 四维评分；本接口提供
 * 质检记录查询、人工复核闭环与手动触发重检。</p>
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/quality")
public class AiQualityInspectionController extends BaseController
{
    @Autowired
    private IAiQualityInspectionService qualityInspectionService;

    /** P1-7：质检模板数据层与校验支撑 */
    @Autowired
    private ai.lawyers.system.mapper.lawyers.quality.AiQualityTemplateMapper qualityTemplateMapper;

    @Autowired
    private ai.lawyers.system.service.impl.lawyers.quality.QualityTemplateSupport qualityTemplateSupport;

    /**
     * 质检记录列表
     */
    @PreAuthorize("@ss.hasPermi('lawyers:quality:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiQualityInspection query)
    {
        startPage();
        List<AiQualityInspection> list = qualityInspectionService.selectInspectionList(query);
        return getDataTable(list);
    }

    /**
     * 导出质检记录
     */
    @PreAuthorize("@ss.hasPermi('lawyers:quality:export')")
    @Log(title = "智能质检记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiQualityInspection query)
    {
        List<AiQualityInspection> list = qualityInspectionService.selectInspectionList(query);
        ExcelUtil<AiQualityInspection> util = new ExcelUtil<AiQualityInspection>(AiQualityInspection.class);
        util.exportExcel(response, list, "智能质检记录数据");
    }

    /**
     * 质检记录详情（含转写文本、维度评分、违禁明细）
     */
    @PreAuthorize("@ss.hasPermi('lawyers:quality:query')")
    @GetMapping(value = "/{inspectionId}")
    public AjaxResult getInfo(@PathVariable("inspectionId") Long inspectionId)
    {
        return success(qualityInspectionService.selectInspectionById(inspectionId));
    }

    /**
     * 人工复核（通过/驳回整改 + 复核评语）
     */
    @PreAuthorize("@ss.hasPermi('lawyers:quality:review')")
    @Log(title = "智能质检复核", businessType = BusinessType.UPDATE)
    @PutMapping("/review")
    public AjaxResult review(@RequestBody AiQualityInspection inspection)
    {
        inspection.setReviewerId(getUserId());
        inspection.setReviewerName(getUsername());
        return toAjax(qualityInspectionService.review(inspection));
    }

    /**
     * 手动触发/重检指定话单（异步执行，立即返回）
     */
    @PreAuthorize("@ss.hasPermi('lawyers:quality:inspect')")
    @Log(title = "手动触发质检", businessType = BusinessType.OTHER)
    @PostMapping("/inspect/{recordId}")
    public AjaxResult inspect(@PathVariable("recordId") Long recordId)
    {
        try
        {
            qualityInspectionService.inspect(recordId);
            return success();
        }
        catch (Exception e)
        {
            return AjaxResult.error("质检触发失败：" + e.getMessage());
        }
    }

    /**
     * P1-7：被检坐席对本人质检结果发起申诉（登录坐席即可，服务端校验本人归属）。
     */
    @Log(title = "质检申诉", businessType = BusinessType.INSERT)
    @PostMapping("/appeal/{inspectionId}")
    public AjaxResult appeal(@PathVariable("inspectionId") Long inspectionId, String appealReason)
    {
        try
        {
            return toAjax(qualityInspectionService.appeal(inspectionId, appealReason, getUserId()));
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * P1-7：班组长复核申诉（维持原判/申诉成立改分 + 复核说明），复用质检复核权限。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:quality:review')")
    @Log(title = "质检申诉复核", businessType = BusinessType.UPDATE)
    @PutMapping("/appeal/review")
    public AjaxResult appealReview(@RequestBody AiQualityInspection inspection)
    {
        inspection.setReviewerId(getUserId());
        inspection.setReviewerName(getUsername());
        try
        {
            return toAjax(qualityInspectionService.appealReview(inspection));
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * P1-7：质检模板列表（维度/权重/prompt 管理）。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:quality:review')")
    @GetMapping("/template/list")
    public AjaxResult templateList(ai.lawyers.system.domain.lawyers.quality.AiQualityTemplate query)
    {
        return success(qualityTemplateMapper.selectTemplateList(query));
    }

    /**
     * P1-7：新增质检模板。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:quality:review')")
    @Log(title = "质检模板", businessType = BusinessType.INSERT)
    @PostMapping("/template")
    public AjaxResult addTemplate(
            @RequestBody ai.lawyers.system.domain.lawyers.quality.AiQualityTemplate template)
    {
        try
        {
            qualityTemplateSupport.validate(template);
            template.setCreateBy(getUsername());
            qualityTemplateMapper.insertTemplate(template);
            return success(template.getTemplateId());
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * P1-7：修改质检模板。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:quality:review')")
    @Log(title = "质检模板", businessType = BusinessType.UPDATE)
    @PutMapping("/template")
    public AjaxResult editTemplate(
            @RequestBody ai.lawyers.system.domain.lawyers.quality.AiQualityTemplate template)
    {
        try
        {
            if (template.getTemplateId() == null
                    || qualityTemplateMapper.selectTemplateById(template.getTemplateId()) == null)
            {
                return AjaxResult.error("质检模板不存在");
            }
            qualityTemplateSupport.validate(template);
            template.setUpdateBy(getUsername());
            return toAjax(qualityTemplateMapper.updateTemplate(template));
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * P1-7：将指定模板设为唯一生效默认模板（停用模板不可设默认）。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:quality:review')")
    @Log(title = "质检模板-设默认", businessType = BusinessType.UPDATE)
    @PutMapping("/template/default/{templateId}")
    public AjaxResult setDefaultTemplate(@PathVariable("templateId") Long templateId)
    {
        ai.lawyers.system.domain.lawyers.quality.AiQualityTemplate template =
                qualityTemplateMapper.selectTemplateById(templateId);
        if (template == null)
        {
            return AjaxResult.error("质检模板不存在");
        }
        if ("1".equals(template.getStatus()))
        {
            return AjaxResult.error("停用状态的模板不能设为生效模板");
        }
        qualityTemplateMapper.clearDefault();
        ai.lawyers.system.domain.lawyers.quality.AiQualityTemplate up =
                new ai.lawyers.system.domain.lawyers.quality.AiQualityTemplate();
        up.setTemplateId(templateId);
        up.setIsDefault("1");
        up.setUpdateBy(getUsername());
        return toAjax(qualityTemplateMapper.updateTemplate(up));
    }
}
