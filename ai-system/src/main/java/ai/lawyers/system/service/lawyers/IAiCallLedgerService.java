package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiCallLedger;

public interface IAiCallLedgerService 
{
    public AiCallLedger selectAiCallLedgerByLedgerId(Long ledgerId);

    public AiCallLedger selectAiCallLedgerByLedgerNo(String ledgerNo);

    public List<AiCallLedger> selectAiCallLedgerList(AiCallLedger aiCallLedger);

    public int insertAiCallLedger(AiCallLedger aiCallLedger);

    public int updateAiCallLedger(AiCallLedger aiCallLedger);

    public int deleteAiCallLedgerByLedgerId(Long ledgerId);

    public int deleteAiCallLedgerByLedgerIds(Long[] ledgerIds);

    public String generateLedgerNo();
}
