package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiCallTransfer;

public interface IAiCallTransferService 
{
    public AiCallTransfer selectAiCallTransferByTransferId(Long transferId);

    public List<AiCallTransfer> selectAiCallTransferList(AiCallTransfer aiCallTransfer);

    public int insertAiCallTransfer(AiCallTransfer aiCallTransfer);

    public int deleteAiCallTransferByTransferId(Long transferId);

    public int deleteAiCallTransferByTransferIds(Long[] transferIds);

    public List<AiCallTransfer> selectAiCallTransferByRecordId(Long recordId);

    public int transferCall(Long recordId, Long fromAgentId, String fromAgentName, Long toAgentId, String toAgentName, String reason);
}
