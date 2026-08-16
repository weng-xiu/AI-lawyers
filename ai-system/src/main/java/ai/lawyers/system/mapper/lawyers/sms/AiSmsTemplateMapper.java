package ai.lawyers.system.mapper.lawyers.sms;

import java.util.List;
import ai.lawyers.system.domain.lawyers.sms.AiSmsTemplate;

/**
 * 短信模板Mapper接口
 *
 * @author ai-lawyers
 */
public interface AiSmsTemplateMapper
{
    public AiSmsTemplate selectAiSmsTemplateByTemplateId(Long templateId);

    public List<AiSmsTemplate> selectAiSmsTemplateList(AiSmsTemplate aiSmsTemplate);

    public int insertAiSmsTemplate(AiSmsTemplate aiSmsTemplate);

    public int updateAiSmsTemplate(AiSmsTemplate aiSmsTemplate);

    public int deleteAiSmsTemplateByTemplateIds(Long[] templateIds);
}
