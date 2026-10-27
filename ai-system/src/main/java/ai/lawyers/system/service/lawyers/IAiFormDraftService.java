package ai.lawyers.system.service.lawyers;

import ai.lawyers.system.domain.lawyers.AiFormDraft;

/**
 * U4 表单草稿服务：按登录用户隔离的草稿读写。
 *
 * @author ai-lawyers
 */
public interface IAiFormDraftService
{
    /** 查询当前用户的草稿（不存在返回 null） */
    AiFormDraft selectDraft(Long userId, String formKey);

    /** 保存/覆盖草稿；内容为空等价于删除 */
    int saveDraft(Long userId, String formKey, String content);

    /** 删除草稿（提交成功/用户丢弃） */
    int deleteDraft(Long userId, String formKey);
}
