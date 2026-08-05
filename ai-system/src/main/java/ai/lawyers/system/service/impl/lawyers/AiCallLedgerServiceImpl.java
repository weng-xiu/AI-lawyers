package ai.lawyers.system.service.impl.lawyers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.common.utils.uuid.IdUtils;
import ai.lawyers.system.domain.lawyers.AiCallLedger;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.mapper.lawyers.AiCallLedgerMapper;
import ai.lawyers.system.service.lawyers.IAiCallLedgerService;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;

@Service
public class AiCallLedgerServiceImpl implements IAiCallLedgerService
{
    @Autowired
    private AiCallLedgerMapper aiCallLedgerMapper;

    @Autowired
    private IAiCallRecordService aiCallRecordService;

    @Autowired
    private IAiCallTicketService aiCallTicketService;

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

    @Override
    public List<Map<String, Object>> selectLedgerTemplates()
    {
        List<Map<String, Object>> templates = new ArrayList<>();
        // 6 类常用咨询登记模板
        templates.add(buildTemplate("1", "民事纠纷咨询", "民事咨询", "婚姻家庭", "1", "接待当事人关于民事纠纷的来电咨询，记录诉求与争议焦点。"));
        templates.add(buildTemplate("2", "刑事辩护咨询", "刑事咨询", "辩护代理", "1", "接待当事人关于刑事案件辩护/代理的咨询，记录案件阶段与基本情况。"));
        templates.add(buildTemplate("3", "行政诉讼咨询", "行政咨询", "行政复议", "1", "接待当事人关于行政争议/行政复议/诉讼的咨询，记录行政行为及诉求。"));
        templates.add(buildTemplate("4", "商事合同咨询", "商事咨询", "合同纠纷", "1", "接待当事人关于商事合同纠纷的咨询，记录合同类型与争议条款。"));
        templates.add(buildTemplate("5", "劳动争议咨询", "劳动咨询", "工资社保", "1", "接待劳动者/用人单位关于劳动争议的咨询，记录用工关系与争议事项。"));
        templates.add(buildTemplate("6", "知识产权咨询", "知识产权", "专利商标", "1", "接待当事人关于知识产权（专利/商标/著作权）的咨询，记录权属及侵权情况。"));
        return templates;
    }

    private Map<String, Object> buildTemplate(String templateId, String templateName, String categoryName,
                                              String subCategory, String serviceType, String contentTemplate)
    {
        Map<String, Object> tpl = new HashMap<>();
        tpl.put("templateId", templateId);
        tpl.put("templateName", templateName);
        tpl.put("categoryName", categoryName);
        tpl.put("subCategory", subCategory);
        tpl.put("serviceType", serviceType);
        tpl.put("sourceChannel", "电话咨询");
        tpl.put("contentTemplate", contentTemplate);
        return tpl;
    }

    @Override
    public AiCallLedger autoFillByRecordId(Long recordId)
    {
        AiCallRecord record = aiCallRecordService.selectAiCallRecordByRecordId(recordId);
        AiCallLedger ledger = new AiCallLedger();
        if (record != null) {
            ledger.setRecordId(record.getRecordId());
            ledger.setCallerName(record.getCallerName());
            ledger.setCallerPhone(record.getCallerNumber());
            ledger.setCallerAddress(record.getCallerAddress());
            ledger.setCategoryId(record.getCategoryId());
            ledger.setCategoryName(record.getCategoryName());
            ledger.setConsultContent(record.getContent());
            ledger.setLawyerAnswer(record.getAnswer());
            ledger.setLawyerName(record.getAgentName());
            ledger.setServiceType("1");
            ledger.setSourceChannel("电话咨询");
            // 通话时长(秒) 转为 咨询时长(分钟)
            if (record.getDuration() != null && record.getDuration() > 0) {
                ledger.setConsultDuration(Math.max(1, record.getDuration() / 60));
            }
        }
        return ledger;
    }

    @Override
    public Long transferToTicket(Long ledgerId, String createBy)
    {
        AiCallLedger ledger = aiCallLedgerMapper.selectAiCallLedgerByLedgerId(ledgerId);
        if (ledger == null) {
            return null;
        }
        // 若已关联工单，直接返回已有工单ID
        if (ledger.getTicketId() != null) {
            return ledger.getTicketId();
        }
        AiCallTicket ticket = new AiCallTicket();
        ticket.setRecordId(ledger.getRecordId());
        ticket.setCallerNumber(ledger.getCallerPhone());
        ticket.setCallerName(ledger.getCallerName());
        String title = (ledger.getCategoryName() != null ? ledger.getCategoryName() : "咨询") + "转办工单";
        ticket.setTitle(title);
        ticket.setContent(ledger.getConsultContent());
        ticket.setPriority("2");
        ticket.setStatus("0");
        ticket.setCreateBy(createBy);
        aiCallTicketService.insertAiCallTicket(ticket);
        // 回写工单ID到台账
        AiCallLedger update = new AiCallLedger();
        update.setLedgerId(ledgerId);
        update.setTicketId(ticket.getTicketId());
        update.setUpdateBy(createBy);
        aiCallLedgerMapper.updateAiCallLedger(update);
        return ticket.getTicketId();
    }
}
