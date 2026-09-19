package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiRiskWarning;

public interface AiRiskWarningMapper
{
    public AiRiskWarning selectAiRiskWarningByWarningId(Long warningId);

    /** F3：行锁加载预警（一键转办防并发重复发起），须在事务内调用 */
    public AiRiskWarning selectByIdForUpdate(Long warningId);

    public List<AiRiskWarning> selectAiRiskWarningList(AiRiskWarning aiRiskWarning);

    public int insertAiRiskWarning(AiRiskWarning aiRiskWarning);

    public int updateAiRiskWarning(AiRiskWarning aiRiskWarning);

    /** F3：一键转办后回写转办流水ID（条件：未回写过） */
    public int updateTransferRef(@org.apache.ibatis.annotations.Param("warningId") Long warningId,
                                 @org.apache.ibatis.annotations.Param("transferId") Long transferId);

    public int deleteAiRiskWarningByWarningId(Long warningId);

    public int deleteAiRiskWarningByWarningIds(Long[] warningIds);
}
