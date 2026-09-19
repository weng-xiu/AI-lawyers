package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiExternalOrg;
import ai.lawyers.system.domain.lawyers.AiRiskWarning;
import ai.lawyers.system.domain.lawyers.AiRiskWarningRule;
import ai.lawyers.system.domain.lawyers.AiTicketTransfer;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.AiExternalOrgMapper;
import ai.lawyers.system.mapper.lawyers.AiRiskWarningMapper;
import ai.lawyers.system.mapper.lawyers.AiRiskWarningRuleMapper;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.IAiRiskWarningService;
import ai.lawyers.system.service.lawyers.IAiTicketTransferService;
import ai.lawyers.system.service.lawyers.queue.MessageNotifyDispatcher;

@Service
public class AiRiskWarningServiceImpl implements IAiRiskWarningService
{
    private static final Logger log = LoggerFactory.getLogger(AiRiskWarningServiceImpl.class);

    @Autowired
    private AiRiskWarningMapper aiRiskWarningMapper;

    @Autowired
    private AiRiskWarningRuleMapper aiRiskWarningRuleMapper;

    @Autowired
    private AiExternalOrgMapper aiExternalOrgMapper;

    @Autowired
    private AiCallRecordMapper aiCallRecordMapper;

    @Autowired
    private IAiCallTicketService aiCallTicketService;

    @Autowired
    private IAiTicketTransferService aiTicketTransferService;

    @Autowired(required = false)
    private MessageNotifyDispatcher messageNotifyDispatcher;

    /** 转办建工单时的内容/标题最大长度（与转办报文 PII 最小必要口径一致） */
    private static final int CONTENT_MAX_LENGTH = 500;
    private static final int TITLE_MAX_LENGTH = 100;

    @Override
    public AiRiskWarning selectAiRiskWarningByWarningId(Long warningId)
    {
        return aiRiskWarningMapper.selectAiRiskWarningByWarningId(warningId);
    }

    @Override
    public List<AiRiskWarning> selectAiRiskWarningList(AiRiskWarning aiRiskWarning)
    {
        return aiRiskWarningMapper.selectAiRiskWarningList(aiRiskWarning);
    }

    @Override
    public int insertAiRiskWarning(AiRiskWarning aiRiskWarning)
    {
        // F3：按命中规则补全预警类型/级别，并对"建议转办条线"做时点快照（规则事后修改不影响历史预警）
        if (aiRiskWarning.getRuleId() != null)
        {
            AiRiskWarningRule rule = aiRiskWarningRuleMapper.selectAiRiskWarningRuleByRuleId(aiRiskWarning.getRuleId());
            if (rule != null && "1".equals(rule.getIsEnabled()))
            {
                if (StringUtils.isEmpty(aiRiskWarning.getWarningType()))
                {
                    aiRiskWarning.setWarningType(rule.getRuleType());
                }
                if (StringUtils.isEmpty(aiRiskWarning.getWarningLevel()))
                {
                    aiRiskWarning.setWarningLevel(rule.getRuleLevel());
                }
                if (StringUtils.isEmpty(aiRiskWarning.getSuggestTransferType()))
                {
                    aiRiskWarning.setSuggestTransferType(rule.getSuggestTransferType());
                }
            }
        }
        if (aiRiskWarning.getStatus() == null || aiRiskWarning.getStatus().isEmpty()) {
            aiRiskWarning.setStatus("0");
        }
        if (aiRiskWarning.getWarningLevel() == null || aiRiskWarning.getWarningLevel().isEmpty()) {
            aiRiskWarning.setWarningLevel("3");
        }
        int rows = aiRiskWarningMapper.insertAiRiskWarning(aiRiskWarning);
        // 新预警生成后广播站内信（T5-3 消息中心），无明确接收人，通知全部在职用户
        if (rows > 0 && aiRiskWarning.getWarningId() != null && messageNotifyDispatcher != null)
        {
            try
            {
                String type = StringUtils.isNotEmpty(aiRiskWarning.getWarningType())
                        ? aiRiskWarning.getWarningType() : "未知类型";
                String customer = StringUtils.isNotEmpty(aiRiskWarning.getCustomerName())
                        ? "，客户：" + aiRiskWarning.getCustomerName() : "";
                String detail = StringUtils.isNotEmpty(aiRiskWarning.getContent())
                        ? "，触发内容：" + aiRiskWarning.getContent() : "";
                messageNotifyDispatcher.broadcast("3",
                        "风险预警（" + warningLevelText(aiRiskWarning.getWarningLevel()) + "）：" + type,
                        "产生一条" + warningLevelText(aiRiskWarning.getWarningLevel()) + "级风险预警，类型：" + type
                                + customer + detail + "，请及时处理。",
                        "warning", aiRiskWarning.getWarningId(), "system");
            }
            catch (Exception e)
            {
                log.warn("风险预警站内信广播失败, warningId={}", aiRiskWarning.getWarningId(), e);
            }
        }
        return rows;
    }

    private String warningLevelText(String level)
    {
        if ("1".equals(level))
        {
            return "高";
        }
        if ("2".equals(level))
        {
            return "中";
        }
        return "低";
    }

    @Override
    public int updateAiRiskWarning(AiRiskWarning aiRiskWarning)
    {
        return aiRiskWarningMapper.updateAiRiskWarning(aiRiskWarning);
    }

    @Override
    public int deleteAiRiskWarningByWarningIds(Long[] warningIds)
    {
        return aiRiskWarningMapper.deleteAiRiskWarningByWarningIds(warningIds);
    }

    @Override
    public List<AiExternalOrg> selectTransferOrgs(String externalType)
    {
        return aiExternalOrgMapper.selectTransferOptions(externalType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTicketTransfer transferByWarning(Long warningId, Long orgId, String remark, String operator)
    {
        if (warningId == null)
        {
            throw new ServiceException("预警ID不能为空");
        }
        // 1. 行锁加载，防坐席双击/并发重复转办
        AiRiskWarning warning = aiRiskWarningMapper.selectByIdForUpdate(warningId);
        if (warning == null)
        {
            throw new ServiceException("风险预警不存在");
        }
        // 2. 已转办：幂等返回既有流水（transferOut 对同一工单+机构亦幂等，双重保险）
        if (warning.getTransferId() != null)
        {
            AiTicketTransfer existing = aiTicketTransferService.selectTransferById(warning.getTransferId());
            if (existing != null)
            {
                log.info("[F3] 预警 {} 已转办，幂等返回流水 transferId={}", warningId, existing.getTransferId());
                return existing;
            }
        }
        // 3. 建议条线（联表查询为快照优先、回退规则当前值）
        String externalType = warning.getSuggestTransferType();
        if (StringUtils.isEmpty(externalType))
        {
            throw new ServiceException("该预警未配置建议转办条线，无法一键转办");
        }
        // 4. 目标机构：入参优先，缺省取命中规则的默认建议机构
        Long targetOrgId = orgId;
        if (targetOrgId == null && warning.getRuleId() != null)
        {
            AiRiskWarningRule rule = aiRiskWarningRuleMapper.selectAiRiskWarningRuleByRuleId(warning.getRuleId());
            if (rule != null)
            {
                targetOrgId = rule.getSuggestOrgId();
            }
        }
        if (targetOrgId == null)
        {
            throw new ServiceException("请选择转办目标机构");
        }
        AiExternalOrg targetOrg = aiExternalOrgMapper.selectAiExternalOrgByOrgId(targetOrgId);
        if (targetOrg == null)
        {
            throw new ServiceException("协同机构不存在");
        }
        if ("1".equals(targetOrg.getStatus()))
        {
            throw new ServiceException("协同机构已停用，无法转办");
        }
        if (!externalType.equals(targetOrg.getExternalType()))
        {
            throw new ServiceException("目标机构条线与建议条线不一致");
        }

        // 5. 自动建单（预警来源为通话时回填通话记录与来电号码；建单即按 F9 SLA 策略自动算截止时间）
        AiCallTicket ticket = new AiCallTicket();
        AiCallRecord record = resolveCallRecord(warning);
        if (record != null)
        {
            ticket.setRecordId(record.getRecordId());
            ticket.setCallerNumber(record.getCallerNumber());
            ticket.setCallerName(StringUtils.isNotEmpty(warning.getCustomerName())
                    ? warning.getCustomerName() : record.getCallerName());
        }
        else
        {
            ticket.setCallerName(warning.getCustomerName());
        }
        ticket.setTitle(truncate("风险预警转办（" + StringUtils.defaultString(warning.getWarningType(), "风险") + "）",
                TITLE_MAX_LENGTH));
        ticket.setContent(buildTicketContent(warning, record, remark));
        ticket.setPriority("1".equals(warning.getWarningLevel()) ? "1" : "2");
        ticket.setStatus("0");
        ticket.setOvertimeFlag(0);
        ticket.setDirection("OUT");
        ticket.setCreateBy(operator);
        aiCallTicketService.insertAiCallTicket(ticket);

        // 6. 复用 F3 既有转办能力（PII 最小必要报文/签名/幂等/重试/人工兜底均沿用）
        AiTicketTransfer transfer = aiTicketTransferService.transferOut(
                ticket.getTicketId(), targetOrgId, remark, operator);

        // 7. 回写预警转办流水
        int rows = aiRiskWarningMapper.updateTransferRef(warningId, transfer.getTransferId());
        if (rows == 0)
        {
            // 行锁保护下不应发生；发生说明预警被其他路径并发处理，抛出触发回滚，避免悬空流水
            throw new ServiceException("预警已被其他操作处理，转办已中止");
        }
        log.info("[F3] 风险预警 {} 一键转办完成 ticketNo={} org={} transferId={}",
                warningId, ticket.getTicketNo(), targetOrg.getOrgName(), transfer.getTransferId());
        return transfer;
    }

    /** 通话来源预警解析通话记录（sourceId 非法或记录缺失时返回 null，不阻断建单） */
    private AiCallRecord resolveCallRecord(AiRiskWarning warning)
    {
        if (!"通话".equals(warning.getSourceType()) || warning.getSourceId() == null)
        {
            return null;
        }
        try
        {
            return aiCallRecordMapper.selectAiCallRecordByRecordId(warning.getSourceId());
        }
        catch (Exception e)
        {
            log.warn("[F3] 预警来源通话记录解析失败 sourceId={}", warning.getSourceId(), e);
            return null;
        }
    }

    private String buildTicketContent(AiRiskWarning warning, AiCallRecord record, String remark)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("本工单由风险预警一键转办自动生成。\n");
        sb.append("预警类型：").append(StringUtils.defaultString(warning.getWarningType(), "-")).append("\n");
        sb.append("预警级别：").append(warningLevelText(warning.getWarningLevel())).append("\n");
        sb.append("客户：").append(StringUtils.isNotEmpty(warning.getCustomerName())
                ? warning.getCustomerName() : "-").append("\n");
        if (record != null && StringUtils.isNotEmpty(record.getCallerNumber()))
        {
            sb.append("来电号码：").append(record.getCallerNumber()).append("\n");
        }
        sb.append("触发内容：").append(StringUtils.defaultString(warning.getContent(), "-")).append("\n");
        if (StringUtils.isNotEmpty(remark))
        {
            sb.append("坐席备注：").append(remark).append("\n");
        }
        return truncate(sb.toString(), CONTENT_MAX_LENGTH);
    }

    private String truncate(String text, int max)
    {
        if (text == null)
        {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
