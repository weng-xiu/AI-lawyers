package ai.lawyers.system.service.impl.lawyers;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallTransfer;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.AiCallTransferMapper;
import ai.lawyers.system.service.lawyers.IAiCallTransferService;

@Service
public class AiCallTransferServiceImpl implements IAiCallTransferService 
{
    @Autowired
    private AiCallTransferMapper aiCallTransferMapper;

    @Autowired
    private AiCallRecordMapper aiCallRecordMapper;

    @Override
    public AiCallTransfer selectAiCallTransferByTransferId(Long transferId)
    {
        return aiCallTransferMapper.selectAiCallTransferByTransferId(transferId);
    }

    @Override
    public List<AiCallTransfer> selectAiCallTransferList(AiCallTransfer aiCallTransfer)
    {
        return aiCallTransferMapper.selectAiCallTransferList(aiCallTransfer);
    }

    @Override
    public int insertAiCallTransfer(AiCallTransfer aiCallTransfer)
    {
        return aiCallTransferMapper.insertAiCallTransfer(aiCallTransfer);
    }

    @Override
    public int deleteAiCallTransferByTransferId(Long transferId)
    {
        return aiCallTransferMapper.deleteAiCallTransferByTransferId(transferId);
    }

    @Override
    public int deleteAiCallTransferByTransferIds(Long[] transferIds)
    {
        return aiCallTransferMapper.deleteAiCallTransferByTransferIds(transferIds);
    }

    @Override
    public List<AiCallTransfer> selectAiCallTransferByRecordId(Long recordId)
    {
        return aiCallTransferMapper.selectAiCallTransferByRecordId(recordId);
    }

    @Override
    @Transactional
    public int transferCall(Long recordId, Long fromAgentId, String fromAgentName, Long toAgentId, String toAgentName, String reason)
    {
        AiCallTransfer transfer = new AiCallTransfer();
        transfer.setRecordId(recordId);
        transfer.setFromAgentId(fromAgentId);
        transfer.setFromAgentName(fromAgentName);
        transfer.setToAgentId(toAgentId);
        transfer.setToAgentName(toAgentName);
        transfer.setTransferTime(new Date());
        transfer.setReason(reason);
        aiCallTransferMapper.insertAiCallTransfer(transfer);

        AiCallRecord record = aiCallRecordMapper.selectAiCallRecordByRecordId(recordId);
        if (record != null) {
            record.setStatus("2");
            record.setTransferId(transfer.getTransferId());
            record.setAgentId(toAgentId);
            aiCallRecordMapper.updateAiCallRecord(record);
        }

        return 1;
    }
}
