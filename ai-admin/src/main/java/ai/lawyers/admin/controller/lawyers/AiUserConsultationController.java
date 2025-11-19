package ai.lawyers.admin.controller.lawyers;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
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
import org.springframework.web.multipart.MultipartFile;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.annotation.Anonymous;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.system.domain.lawyers.AiUserConsultation;
import ai.lawyers.system.domain.lawyers.AiUserEvaluation;
import ai.lawyers.system.service.lawyers.IAiUserConsultationService;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.poi.ExcelUtil;

/**
 * 用户咨询Controller
 * 
 * @author AI律师
 * @date 2023-11-19
 */
@RestController
@RequestMapping("/aiuser/consultation")
public class AiUserConsultationController extends BaseController
{
    @Autowired
    private IAiUserConsultationService aiUserConsultationService;

    /**
     * 提交法律咨询
     */
    @Log(title = "用户咨询", businessType = BusinessType.INSERT)
    @Anonymous
    @PostMapping("/submit")
    public AjaxResult submitConsultation(HttpServletRequest request)
    {
        try {
            // 获取表单数据
            String category = request.getParameter("category");
            String content = request.getParameter("content");
            
            // 获取上传的文件
            List<MultipartFile> files = ((org.springframework.web.multipart.MultipartHttpServletRequest) request).getFiles("files");
            
            // 调用服务层处理咨询提交
            AiUserConsultation consultation = aiUserConsultationService.submitConsultation(category, content, files);
            
            return AjaxResult.success("咨询提交成功", consultation);
        } catch (Exception e) {
            return AjaxResult.error("咨询提交失败：" + e.getMessage());
        }
    }

    /**
     * 获取咨询结果
     */
    @Anonymous
    @GetMapping("/result/{id}")
    public AjaxResult getConsultationResult(@PathVariable("id") Long id)
    {
        AiUserConsultation consultation = aiUserConsultationService.selectAiUserConsultationById(id);
        if (consultation == null) {
            return AjaxResult.error("咨询记录不存在");
        }
        return AjaxResult.success(consultation);
    }

    /**
     * 获取咨询信息
     */
    @GetMapping("/info/{id}")
    public AjaxResult getConsultationInfo(@PathVariable("id") Long id)
    {
        AiUserConsultation consultation = aiUserConsultationService.selectAiUserConsultationById(id);
        if (consultation == null) {
            return AjaxResult.error("咨询记录不存在");
        }
        return AjaxResult.success(consultation);
    }

    /**
     * 获取咨询历史记录
     */
    @GetMapping("/history")
    public TableDataInfo getConsultationHistory(AiUserConsultation consultation)
    {
        startPage();
        List<AiUserConsultation> list = aiUserConsultationService.selectAiUserConsultationList(consultation);
        return getDataTable(list);
    }

    /**
     * 提交咨询评价
     */
    @Log(title = "咨询评价", businessType = BusinessType.INSERT)
    @PostMapping("/evaluation")
    public AjaxResult submitEvaluation(@RequestBody AiUserEvaluation evaluation)
    {
        try {
            aiUserConsultationService.submitEvaluation(evaluation);
            return AjaxResult.success("评价提交成功");
        } catch (Exception e) {
            return AjaxResult.error("评价提交失败：" + e.getMessage());
        }
    }

    /**
     * 获取问题分类
     */
    @Anonymous
    @GetMapping("/categories")
    public AjaxResult getQuestionCategories()
    {
        return AjaxResult.success(aiUserConsultationService.getQuestionCategories());
    }

    /**
     * 删除咨询记录
     */
    @Log(title = "用户咨询", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        return toAjax(aiUserConsultationService.deleteAiUserConsultationById(id));
    }
}