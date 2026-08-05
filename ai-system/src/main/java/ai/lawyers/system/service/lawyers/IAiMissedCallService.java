package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiMissedCall;

/**
 * 未接来电Service接口
 *
 * @author ai-lawyers
 */
public interface IAiMissedCallService
{
    public AiMissedCall selectAiMissedCallByMissedCallId(Long missedCallId);

    public List<AiMissedCall> selectAiMissedCallList(AiMissedCall aiMissedCall);

    public int insertAiMissedCall(AiMissedCall aiMissedCall);

    public int updateAiMissedCall(AiMissedCall aiMissedCall);

    public int deleteAiMissedCallByMissedCallId(Long missedCallId);

    public int deleteAiMissedCallByMissedCallIds(Long[] missedCallIds);

    /** 未接来电统计 */
    public java.util.Map<String, Object> selectMissedCallStats();
}
