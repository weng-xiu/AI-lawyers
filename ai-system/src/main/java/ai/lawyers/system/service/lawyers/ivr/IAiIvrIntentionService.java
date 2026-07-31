package ai.lawyers.system.service.lawyers.ivr;

import java.util.List;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrIntention;

public interface IAiIvrIntentionService
{
    public AiIvrIntention selectAiIvrIntentionByIntentionId(Long intentionId);

    public List<AiIvrIntention> selectAiIvrIntentionList(AiIvrIntention aiIvrIntention);

    public AiIvrIntention selectAiIvrIntentionByCode(String intentionCode);

    public List<AiIvrIntention> selectActiveIntentions();

    public int insertAiIvrIntention(AiIvrIntention aiIvrIntention);

    public int updateAiIvrIntention(AiIvrIntention aiIvrIntention);

    public int deleteAiIvrIntentionByIntentionId(Long intentionId);

    public int deleteAiIvrIntentionByIntentionIds(Long[] intentionIds);

    public AiIvrIntention matchIntention(String inputText);
}
