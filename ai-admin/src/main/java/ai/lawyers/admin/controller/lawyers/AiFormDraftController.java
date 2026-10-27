package ai.lawyers.admin.controller.lawyers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiFormDraft;
import ai.lawyers.system.service.lawyers.IAiFormDraftService;

/**
 * U4 表单草稿Controller。
 *
 * <p>仅操作「当前登录用户」的草稿：userId 一律由服务端从登录态取，
 * 不接受前端传入，避免越权读取他人草稿。</p>
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/formDraft")
public class AiFormDraftController extends BaseController
{
    @Autowired
    private IAiFormDraftService formDraftService;

    /** 读取当前用户在指定表单上的草稿 */
    @GetMapping
    public AjaxResult get(@RequestParam("formKey") String formKey)
    {
        AiFormDraft draft = formDraftService.selectDraft(SecurityUtils.getUserId(), formKey);
        return draft == null ? AjaxResult.success() : AjaxResult.success(draft);
    }

    /** 保存/覆盖草稿（自动保存节流由前端控制） */
    @PostMapping
    public AjaxResult save(@RequestBody AiFormDraft formDraft)
    {
        formDraftService.saveDraft(SecurityUtils.getUserId(), formDraft.getFormKey(), formDraft.getContent());
        return AjaxResult.success();
    }

    /** 删除草稿（提交成功或用户丢弃） */
    @DeleteMapping
    public AjaxResult remove(@RequestParam("formKey") String formKey)
    {
        formDraftService.deleteDraft(SecurityUtils.getUserId(), formKey);
        return AjaxResult.success();
    }
}
