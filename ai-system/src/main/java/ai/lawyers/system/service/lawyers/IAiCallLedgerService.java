package ai.lawyers.system.service.lawyers;

import java.util.List;
import java.util.Map;
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

    /** 台账模板列表（6 类常用咨询登记模板） */
    public List<Map<String, Object>> selectLedgerTemplates();

    /** 根据来电记录ID自动填充台账字段 */
    public AiCallLedger autoFillByRecordId(Long recordId);

    /** 将台账转工单：由台账生成工单并回写 ticketId，返回新建工单ID */
    public Long transferToTicket(Long ledgerId, String createBy);
}
