package ai.lawyers.system.mapper.lawyers;

import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.AiFormDraft;

/**
 * U4 表单草稿Mapper接口
 *
 * @author ai-lawyers
 */
public interface AiFormDraftMapper
{
    /** 查询指定用户的指定表单草稿 */
    AiFormDraft selectDraft(@Param("userId") Long userId, @Param("formKey") String formKey);

    /** 覆盖写入草稿（user_id + form_key 唯一键 upsert） */
    int upsertDraft(AiFormDraft draft);

    /** 删除指定用户的指定表单草稿 */
    int deleteDraft(@Param("userId") Long userId, @Param("formKey") String formKey);
}
