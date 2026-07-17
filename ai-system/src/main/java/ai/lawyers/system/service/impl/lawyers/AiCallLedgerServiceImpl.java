package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.common.utils.uuid.IdUtils;
import ai.lawyers.system.domain.lawyers.AiCallLedger;
import ai.lawyers.system.mapper.lawyers.AiCallLedgerMapper;
import ai.lawyers.system.service.lawyers.IAiCallLedgerService;

@Service
public class AiCallLedgerServiceImpl implements IAiCallLedgerService 
{
    @Autowired
    private AiCallLedgerMapper aiCallLedgerMapper;

    @Override
    public AiCallLedger selectAiCallLedgerByLedgerId(Long ledgerId)
    {
        return aiCallLedgerMapper.selectAiCallLedgerByLedgerId(ledgerId);
    }

    @Override
    public AiCallLedger selectAiCallLedgerByLedgerNo(String ledgerNo)
    {
        return aiCallLedgerMapper.selectAiCallLedgerByLedgerNo(ledgerNo);
    }

    @Override
    public List<AiCallLedger> selectAiCallLedgerList(AiCallLedger aiCallLedger)
    {
        return aiCallLedgerMapper.selectAiCallLedgerList(aiCallLedger);
    }

    @Override
    public int insertAiCallLedger(AiCallLedger aiCallLedger)
    {
        if (aiCallLedger.getLedgerNo() == null || aiCallLedger.getLedgerNo().isEmpty()) {
            aiCallLedger.setLedgerNo(generateLedgerNo());
        }
        return aiCallLedgerMapper.insertAiCallLedger(aiCallLedger);
    }

    @Override
    public int updateAiCallLedger(AiCallLedger aiCallLedger)
    {
        return aiCallLedgerMapper.updateAiCallLedger(aiCallLedger);
    }

    @Override
    public int deleteAiCallLedgerByLedgerId(Long ledgerId)
    {
        return aiCallLedgerMapper.deleteAiCallLedgerByLedgerId(ledgerId);
    }

    @Override
    public int deleteAiCallLedgerByLedgerIds(Long[] ledgerIds)
    {
        return aiCallLedgerMapper.deleteAiCallLedgerByLedgerIds(ledgerIds);
    }

    @Override
    public String generateLedgerNo()
    {
        return "DJ" + DateUtils.dateTimeNow("yyyyMMddHHmmss") + IdUtils.randomUUID().substring(0, 6).toUpperCase();
    }
}
