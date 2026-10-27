package ai.lawyers.system.service.impl.lawyers;

import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiFormDraft;
import ai.lawyers.system.mapper.lawyers.AiFormDraftMapper;
import ai.lawyers.system.service.lawyers.IAiFormDraftService;

/**
 * U4 表单草稿服务实现。
 *
 * <p>草稿内容可能包含咨询文本等敏感信息，仅按登录用户隔离存储，
 * 不做跨用户查询；删除走物理删除，提交成功后由前端主动清理。</p>
 *
 * @author ai-lawyers
 */
@Service
public class AiFormDraftServiceImpl implements IAiFormDraftService
{
    /** 与表字段 varchar(128) 对齐 */
    private static final int MAX_FORM_KEY_LENGTH = 128;

    @Autowired
    private AiFormDraftMapper formDraftMapper;

    private void assertKey(Long userId, String formKey)
    {
        if (userId == null)
        {
            throw new ServiceException("无法识别当前用户，草稿操作已拒绝");
        }
        if (StringUtils.isEmpty(formKey) || formKey.trim().length() > MAX_FORM_KEY_LENGTH)
        {
            throw new ServiceException("表单标识不合法");
        }
    }

    @Override
    public AiFormDraft selectDraft(Long userId, String formKey)
    {
        assertKey(userId, formKey);
        return formDraftMapper.selectDraft(userId, formKey.trim());
    }

    @Override
    public int saveDraft(Long userId, String formKey, String content)
    {
        assertKey(userId, formKey);
        if (StringUtils.isEmpty(content))
        {
            // 表单被清空：等价于删除，避免残留空草稿反复弹恢复提示
            return deleteDraft(userId, formKey);
        }
        AiFormDraft draft = new AiFormDraft();
        draft.setUserId(userId);
        draft.setFormKey(formKey.trim());
        draft.setContent(content);
        draft.setUpdateTime(new Date());
        return formDraftMapper.upsertDraft(draft);
    }

    @Override
    public int deleteDraft(Long userId, String formKey)
    {
        assertKey(userId, formKey);
        return formDraftMapper.deleteDraft(userId, formKey.trim());
    }
}
