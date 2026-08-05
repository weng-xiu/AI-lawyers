package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiMissedCall;

/**
 * 未接来电Mapper接口
 *
 * @author ai-lawyers
 */
public interface AiMissedCallMapper
{
    public AiMissedCall selectAiMissedCallByMissedCallId(Long missedCallId);

    public List<AiMissedCall> selectAiMissedCallList(AiMissedCall aiMissedCall);

    public int insertAiMissedCall(AiMissedCall aiMissedCall);

    public int updateAiMissedCall(AiMissedCall aiMissedCall);

    public int deleteAiMissedCallByMissedCallId(Long missedCallId);

    public int deleteAiMissedCallByMissedCallIds(Long[] missedCallIds);

    /** 未接来电统计：今日未接、本周未接、已回拨、总数 */
    public java.util.Map<String, Object> selectMissedCallStats();
}
