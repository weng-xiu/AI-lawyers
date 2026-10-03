package ai.lawyers.system.service.impl.lawyers;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.common.utils.sign.PiiCryptoUtils;
import ai.lawyers.common.utils.uuid.IdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiSlaPolicy;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.IAiSlaPolicyService;
import ai.lawyers.system.service.lawyers.TicketFlowService;
import ai.lawyers.system.service.lawyers.queue.MessageNotifyDispatcher;
import ai.lawyers.system.service.lawyers.rag.TicketVectorService;

@Service
public class AiCallTicketServiceImpl implements IAiCallTicketService 
{
    private static final Logger log = LoggerFactory.getLogger(AiCallTicketServiceImpl.class);

    @Autowired
    private AiCallTicketMapper aiCallTicketMapper;

    @Autowired(required = false)
    private MessageNotifyDispatcher messageNotifyDispatcher;

    /** F9：按策略自动计算 SLA 截止时间（无匹配策略时不阻断建单） */
    @Autowired(required = false)
    private IAiSlaPolicyService slaPolicyService;

    /** P1-8：工单办结/归档时入语义向量索引（未装配模型时静默） */
    @Autowired(required = false)
    private TicketVectorService ticketVectorService;

    /** P1-6：工单流转合法性 + 角色校验（状态机为唯一依据） */
    @Autowired
    private TicketFlowService ticketFlowService;

    @Override
    public AiCallTicket selectAiCallTicketByTicketId(Long ticketId)
    {
        return aiCallTicketMapper.selectAiCallTicketByTicketId(ticketId);
    }

    @Override
    public AiCallTicket selectAiCallTicketByTicketNo(String ticketNo)
    {
        return aiCallTicketMapper.selectAiCallTicketByTicketNo(ticketNo);
    }

    @Override
    public List<AiCallTicket> selectAiCallTicketList(AiCallTicket aiCallTicket)
    {
        // G1-b2：号码条件（含弹屏内部调用）统一在此派生位置分片 token
        if (StringUtils.isNotEmpty(aiCallTicket.getCallerNumber())
                && aiCallTicket.getTokenGroups() == null)
        {
            aiCallTicket.setTokenGroups(PiiCryptoUtils.tokenGroups(aiCallTicket.getCallerNumber()));
        }
        return aiCallTicketMapper.selectAiCallTicketList(aiCallTicket);
    }

    @Override
    public int insertAiCallTicket(AiCallTicket aiCallTicket)
    {
        if (aiCallTicket.getTicketNo() == null || aiCallTicket.getTicketNo().isEmpty()) {
            aiCallTicket.setTicketNo(generateTicketNo());
        }
        if (aiCallTicket.getStatus() == null) {
            aiCallTicket.setStatus("0");
        }
        if (aiCallTicket.getOvertimeFlag() == null)
        {
            aiCallTicket.setOvertimeFlag(0);
        }
        // F9：未显式指定截止时间时，按业务类型+优先级匹配 SLA 策略自动计算
        if (aiCallTicket.getDueTime() == null && slaPolicyService != null)
        {
            try
            {
                String bizType = StringUtils.isNotEmpty(aiCallTicket.getExternalType())
                        ? aiCallTicket.getExternalType() : "TICKET";
                AiSlaPolicy policy = slaPolicyService.matchPolicy(bizType, aiCallTicket.getPriority());
                if (policy != null && policy.getResolveMinutes() != null)
                {
                    aiCallTicket.setDueTime(new Date(System.currentTimeMillis()
                            + policy.getResolveMinutes() * 60_000L));
                }
            }
            catch (Exception e)
            {
                log.warn("SLA 截止时间自动计算失败 ticketNo={}", aiCallTicket.getTicketNo(), e);
            }
        }
        return aiCallTicketMapper.insertAiCallTicket(aiCallTicket);
    }

    @Override
    public int updateAiCallTicket(AiCallTicket aiCallTicket)
    {
        int rows = aiCallTicketMapper.updateAiCallTicket(aiCallTicket);
        // P1-8：编辑路径办结/归档时入语义索引
        if (rows > 0 && aiCallTicket.getTicketId() != null
                && ("2".equals(aiCallTicket.getStatus()) || "3".equals(aiCallTicket.getStatus())))
        {
            indexIfReady(aiCallTicket.getTicketId());
        }
        return rows;
    }

    @Override
    public int deleteAiCallTicketByTicketId(Long ticketId)
    {
        int rows = aiCallTicketMapper.deleteAiCallTicketByTicketId(ticketId);
        removeIndexIfReady(ticketId);
        return rows;
    }

    @Override
    public int deleteAiCallTicketByTicketIds(Long[] ticketIds)
    {
        int rows = aiCallTicketMapper.deleteAiCallTicketByTicketIds(ticketIds);
        if (ticketVectorService != null && ticketIds != null)
        {
            for (Long id : ticketIds)
            {
                ticketVectorService.removeTicket(id);
            }
        }
        return rows;
    }

    @Override
    public List<AiCallTicket> selectAiCallTicketByAssignUserId(Long assignUserId)
    {
        return aiCallTicketMapper.selectAiCallTicketByAssignUserId(assignUserId);
    }

    @Override
    public int updateTicketStatus(Long ticketId, String status)
    {
        if ("2".equals(status))
        {
            // 办结必须经状态机校验（含角色），不能直接跳状态
            return completeWithFlow(ticketId);
        }
        int rows = aiCallTicketMapper.updateTicketStatus(ticketId, status);
        if (rows > 0 && "3".equals(status))
        {
            indexIfReady(ticketId);
        }
        return rows;
    }

    /**
     * P1-6：办结经状态机校验——当前为处理中(1)直接 complete；兼容未受理(0)工单，
     * 在有 start 权限前提下自动补推进到处理中再 complete（保持现网"未受理也可办结"体验）。
     */
    private int completeWithFlow(Long ticketId)
    {
        AiCallTicket exist = requireTicket(ticketId);
        List<String> roles = ticketFlowService.currentRoleKeys();
        String st = exist.getStatus();
        if ("0".equals(st))
        {
            ticketFlowService.validateAction(TicketFlowService.DEFAULT_FLOW, "0", "start", roles);
            aiCallTicketMapper.updateTicketStatus(ticketId, "1");
            st = "1";
        }
        ticketFlowService.validateAction(TicketFlowService.DEFAULT_FLOW, st, "complete", roles);
        int rows = aiCallTicketMapper.updateTicketStatus(ticketId, "2");
        if (rows > 0)
        {
            indexIfReady(ticketId);
        }
        return rows;
    }

    /** 读取工单，不存在抛非法参数异常 */
    private AiCallTicket requireTicket(Long ticketId)
    {
        AiCallTicket t = aiCallTicketMapper.selectAiCallTicketByTicketId(ticketId);
        if (t == null)
        {
            throw new IllegalArgumentException("工单不存在：" + ticketId);
        }
        return t;
    }

    @Override
    public int updateTicketProcess(Long ticketId, String processContent, Long assignUserId, String assignUserName)
    {
        AiCallTicket exist = requireTicket(ticketId);
        if ("0".equals(exist.getStatus()))
        {
            // 首次受理：经状态机校验 start(0→1)及角色；处理中(1)的再分派状态不变，放行
            ticketFlowService.validateAction(TicketFlowService.DEFAULT_FLOW, "0", "start",
                    ticketFlowService.currentRoleKeys());
        }
        int rows = aiCallTicketMapper.updateTicketProcess(ticketId, processContent, assignUserId, assignUserName);
        // 分配给坐席成功后投递站内信（T5-3 消息中心）
        if (rows > 0 && assignUserId != null && messageNotifyDispatcher != null)
        {
            try
            {
                AiCallTicket ticket = aiCallTicketMapper.selectAiCallTicketByTicketId(ticketId);
                String title = ticket != null && StringUtils.isNotEmpty(ticket.getTitle())
                        ? ticket.getTitle() : String.valueOf(ticketId);
                String content = "您有新的工单待处理：" + title
                        + (StringUtils.isNotEmpty(assignUserName) ? "（处理人：" + assignUserName + "）" : "")
                        + "，请及时跟进。";
                messageNotifyDispatcher.notify(assignUserId, "5", "工单分配提醒：" + title,
                        content, "ticket", ticketId, "system");
            }
            catch (Exception e)
            {
                log.warn("工单分配站内信投递失败, ticketId={}, assignUserId={}", ticketId, assignUserId, e);
            }
        }
        return rows;
    }

    @Override
    public int archiveTicket(Long ticketId)
    {
        AiCallTicket exist = requireTicket(ticketId);
        // 归档必须为已办结(2)且角色允许（状态机唯一依据），防止未办结直接归档
        ticketFlowService.validateAction(TicketFlowService.DEFAULT_FLOW, exist.getStatus(),
                "archive", ticketFlowService.currentRoleKeys());
        return aiCallTicketMapper.archiveTicket(ticketId);
    }

    @Override
    public String generateTicketNo()
    {
        return "TKT" + DateUtils.dateTimeNow("yyyyMMddHHmmss") + IdUtils.randomUUID().substring(0, 6).toUpperCase();
    }

    @Override
    public List<Map<String, Object>> selectTicketTimeline(Long ticketId)
    {
        List<Map<String, Object>> timeline = new ArrayList<>();
        AiCallTicket ticket = aiCallTicketMapper.selectAiCallTicketByTicketId(ticketId);
        if (ticket == null)
        {
            return timeline;
        }
        // 1. 工单创建
        addTimelineNode(timeline, "工单创建", ticket.getCreateBy(), ticket.getCreateTime(),
                "工单已创建" + (StringUtils.isNotEmpty(ticket.getTitle()) ? "，标题：" + ticket.getTitle() : ""),
                "#0b1f4a", "el-icon-document-add");
        // 2. 工单处理
        if (ticket.getProcessTime() != null)
        {
            addTimelineNode(timeline, "工单处理", ticket.getAssignUserName(), ticket.getProcessTime(),
                    StringUtils.isNotEmpty(ticket.getProcessContent()) ? ticket.getProcessContent() : "工单已开始处理",
                    "#255A99", "el-icon-setting");
        }
        // 3. 工单完成（status=2 但没有显式完成时间，用更新时间兜底）
        if ("2".equals(ticket.getStatus()) || "3".equals(ticket.getStatus()))
        {
            Date completeTime = ticket.getCloseTime() != null ? ticket.getCloseTime() : ticket.getUpdateTime();
            addTimelineNode(timeline, "工单完成", ticket.getUpdateBy(), completeTime,
                    "工单处理完成", "#2B8C6E", "el-icon-circle-check");
        }
        // 4. 工单归档
        if ("3".equals(ticket.getStatus()) && ticket.getCloseTime() != null)
        {
            addTimelineNode(timeline, "工单归档", ticket.getUpdateBy(), ticket.getCloseTime(),
                    "工单已归档", "#7C3AED", "el-icon-folder");
        }
        // 5. 超时提醒
        if (ticket.getOvertimeFlag() != null && ticket.getOvertimeFlag() == 1)
        {
            addTimelineNode(timeline, "SLA超时提醒", "系统", ticket.getRemindTime(),
                    "工单已超过SLA截止时间，请尽快处理", "#d97706", "el-icon-warning");
        }
        return timeline;
    }

    private void addTimelineNode(List<Map<String, Object>> timeline, String title, String user,
                                 Date time, String description, String color, String icon)
    {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("title", title);
        node.put("user", user == null ? "系统" : user);
        node.put("time", time == null ? "" : DateUtils.parseDateToStr("yyyy-MM-dd HH:mm:ss", time));
        node.put("description", description);
        node.put("color", color);
        node.put("icon", icon);
        timeline.add(node);
    }

    /** P1-8：触发向量索引（服务未装配时静默） */
    private void indexIfReady(Long ticketId)
    {
        if (ticketVectorService != null)
        {
            try
            {
                ticketVectorService.indexTicketAsync(ticketId);
            }
            catch (Exception e)
            {
                log.warn("工单向量索引触发失败 ticketId={}: {}", ticketId, e.getMessage());
            }
        }
    }

    /** P1-8：从内存索引移除（服务未装配时静默） */
    private void removeIndexIfReady(Long ticketId)
    {
        if (ticketVectorService != null)
        {
            ticketVectorService.removeTicket(ticketId);
        }
    }
}
