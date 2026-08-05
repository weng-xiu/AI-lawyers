package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiCallLedger;

public interface AiCallLedgerMapper
{
    public AiCallLedger selectAiCallLedgerByLedgerId(Long ledgerId);

    public AiCallLedger selectAiCallLedgerByLedgerNo(String ledgerNo);

    public List<AiCallLedger> selectAiCallLedgerList(AiCallLedger aiCallLedger);

    public int insertAiCallLedger(AiCallLedger aiCallLedger);

    public int updateAiCallLedger(AiCallLedger aiCallLedger);

    public int deleteAiCallLedgerByLedgerId(Long ledgerId);

    public int deleteAiCallLedgerByLedgerIds(Long[] ledgerIds);

    /** 工作台：今日台账平均满意度（1非常满意2满意3一般4不满意） */
    public java.util.Map<String, Object> selectTodaySatisfactionStats();
}
