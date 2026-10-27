package ai.lawyers.system.domain.lawyers;

import java.io.Serializable;
import java.util.Date;

/**
 * U4 表单防丢失：服务端草稿对象 ai_form_draft
 *
 * <p>按「用户 + 表单标识」唯一，页面自动保存时覆盖写入，
 * 提交成功或用户选择丢弃后删除。前端 localStorage 仍是首选（零延迟），
 * 服务端草稿用于换浏览器/清缓存后仍能恢复。</p>
 *
 * @author ai-lawyers
 */
public class AiFormDraft implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 草稿ID */
    private Long draftId;

    /** 所属用户ID */
    private Long userId;

    /** 表单标识（页面:用户名） */
    private String formKey;

    /** 表单内容（JSON：{ts, data}） */
    private String content;

    /** 最后保存时间 */
    private Date updateTime;

    public Long getDraftId()
    {
        return draftId;
    }

    public void setDraftId(Long draftId)
    {
        this.draftId = draftId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getFormKey()
    {
        return formKey;
    }

    public void setFormKey(String formKey)
    {
        this.formKey = formKey;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }
}
