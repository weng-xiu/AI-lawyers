package ai.lawyers.system.mapper.lawyers.quality;

import java.util.List;
import ai.lawyers.system.domain.lawyers.quality.AiQualityTemplate;

/**
 * P1-7：质检评分模板数据层。
 *
 * @author ai-lawyers
 */
public interface AiQualityTemplateMapper
{
    /** 查全部模板（管理页） */
    List<AiQualityTemplate> selectTemplateList(AiQualityTemplate query);

    /** 查当前生效模板（is_default=1 且 status=0） */
    AiQualityTemplate selectActiveTemplate();

    AiQualityTemplate selectTemplateById(Long templateId);

    int insertTemplate(AiQualityTemplate template);

    int updateTemplate(AiQualityTemplate template);

    /** 清除全部默认标记（设新默认前调用） */
    int clearDefault();
}
