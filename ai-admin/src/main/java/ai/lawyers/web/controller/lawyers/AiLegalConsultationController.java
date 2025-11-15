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
import ai.lawyers.system.domain.lawyers.AiLegalConsultation;
import ai.lawyers.system.service.lawyers.IAiLegalConsultationService;

/**
 * 法律咨询记录Controller
 * 
 * @author AI Lawyers
 */
@RestController
@RequestMapping("/lawyers/consultation")
public class AiLegalConsultationController extends BaseController
{
    @Autowired
    private IAiLegalConsultationService aiLegalConsultationService;

    /**
     * 查询法律咨询记录列表
     */
    @PreAuthorize("@ss.hasPermi('lawyers:consultation:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiLegalConsultation aiLegalConsultation)
    {
        startPage();
        List<AiLegalConsultation> list = aiLegalConsultationService.selectAiLegalConsultationList(aiLegalConsultation);
        return getDataTable(list);
    }

    /**
     * 导出法律咨询记录列表
     */
    @PreAuthorize("@ss.hasPermi('lawyers:consultation:export')")
    @Log(title = "法律咨询记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiLegalConsultation aiLegalConsultation)
    {
        List<AiLegalConsultation> list = aiLegalConsultationService.selectAiLegalConsultationList(aiLegalConsultation);
        ExcelUtil<AiLegalConsultation> util = new ExcelUtil<AiLegalConsultation>(AiLegalConsultation.class);
        util.exportExcel(response, list, "法律咨询记录数据");
    }

    /**
     * 获取法律咨询记录详细信息
     */
    @PreAuthorize("@ss.hasPermi('lawyers:consultation:query')")
    @GetMapping(value = "/{consultationId}")
    public AjaxResult getInfo(@PathVariable("consultationId") Long consultationId)
    {
        return success(aiLegalConsultationService.selectAiLegalConsultationByConsultationId(consultationId));
    }

    /**
     * 获取用户的历史咨询记录
     */
    @PreAuthorize("@ss.hasPermi('lawyers:consultation:query')")
    @GetMapping(value = "/user/{userId}")
    public AjaxResult getUserConsultations(@PathVariable("userId") Long userId)
    {
        List<AiLegalConsultation> list = aiLegalConsultationService.selectAiLegalConsultationByUserId(userId);
        return success(list);
    }

    /**
     * 新增法律咨询记录
     */
    @PreAuthorize("@ss.hasPermi('lawyers:consultation:add')")
    @Log(title = "法律咨询记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiLegalConsultation aiLegalConsultation)
    {
        aiLegalConsultation.setCreateBy(getUsername());
        // 生成AI回答
        String answer = aiLegalConsultationService.generateLegalAnswer(
            aiLegalConsultation.getQuestion(), 
            aiLegalConsultation.getCategoryId()
        );
        aiLegalConsultation.setAnswer(answer);
        aiLegalConsultation.setStatus("0"); // 状态0表示已回答
        return toAjax(aiLegalConsultationService.insertAiLegalConsultation(aiLegalConsultation));
    }

    /**
     * 修改法律咨询记录
     */
    @PreAuthorize("@ss.hasPermi('lawyers:consultation:edit')")
    @Log(title = "法律咨询记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiLegalConsultation aiLegalConsultation)
    {
        aiLegalConsultation.setUpdateBy(getUsername());
        return toAjax(aiLegalConsultationService.updateAiLegalConsultation(aiLegalConsultation));
    }

    /**
     * 删除法律咨询记录
     */
    @PreAuthorize("@ss.hasPermi('lawyers:consultation:remove')")
    @Log(title = "法律咨询记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{consultationIds}")
    public AjaxResult remove(@PathVariable Long[] consultationIds)
    {
        return toAjax(aiLegalConsultationService.deleteAiLegalConsultationByConsultationIds(consultationIds));
    }
}