package ai.lawyers.system.service.impl.lawyers.ivr;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrIntention;
import ai.lawyers.system.mapper.lawyers.ivr.AiIvrIntentionMapper;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrIntentionService;
import ai.lawyers.common.utils.StringUtils;

@Service
public class AiIvrIntentionServiceImpl implements IAiIvrIntentionService
{
    @Autowired
    private AiIvrIntentionMapper aiIvrIntentionMapper;

    @Override
    public AiIvrIntention selectAiIvrIntentionByIntentionId(Long intentionId)
    {
        return aiIvrIntentionMapper.selectAiIvrIntentionByIntentionId(intentionId);
    }

    @Override
    public List<AiIvrIntention> selectAiIvrIntentionList(AiIvrIntention aiIvrIntention)
    {
        return aiIvrIntentionMapper.selectAiIvrIntentionList(aiIvrIntention);
    }

    @Override
    public AiIvrIntention selectAiIvrIntentionByCode(String intentionCode)
    {
        return aiIvrIntentionMapper.selectAiIvrIntentionByCode(intentionCode);
    }

    @Override
    public List<AiIvrIntention> selectActiveIntentions()
    {
        return aiIvrIntentionMapper.selectActiveIntentions();
    }

    @Override
    public int insertAiIvrIntention(AiIvrIntention aiIvrIntention)
    {
        return aiIvrIntentionMapper.insertAiIvrIntention(aiIvrIntention);
    }

    @Override
    public int updateAiIvrIntention(AiIvrIntention aiIvrIntention)
    {
        return aiIvrIntentionMapper.updateAiIvrIntention(aiIvrIntention);
    }

    @Override
    public int deleteAiIvrIntentionByIntentionId(Long intentionId)
    {
        return aiIvrIntentionMapper.deleteAiIvrIntentionByIntentionId(intentionId);
    }

    @Override
    public int deleteAiIvrIntentionByIntentionIds(Long[] intentionIds)
    {
        return aiIvrIntentionMapper.deleteAiIvrIntentionByIntentionIds(intentionIds);
    }

    @Override
    public AiIvrIntention matchIntention(String inputText)
    {
        if (StringUtils.isEmpty(inputText)) {
            return null;
        }
        List<AiIvrIntention> intentions = selectActiveIntentions();
        for (AiIvrIntention intention : intentions) {
            if (StringUtils.isNotEmpty(intention.getRegexPattern())) {
                try {
                    Pattern pattern = Pattern.compile(intention.getRegexPattern(), Pattern.CASE_INSENSITIVE);
                    if (pattern.matcher(inputText).find()) {
                        return intention;
                    }
                } catch (Exception e) {
                    continue;
                }
            }
        }
        return null;
    }
}
