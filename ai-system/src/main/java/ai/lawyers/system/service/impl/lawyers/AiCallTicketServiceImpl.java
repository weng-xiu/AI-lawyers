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
import ai.lawyers.common.utils.uuid.IdUtils;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;

@Service
public class AiCallTicketServiceImpl implements IAiCallTicketService 
{
    @Autowired
    private AiCallTicketMapper aiCallTicketMapper;

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
        return aiCallTicketMapper.insertAiCallTicket(aiCallTicket);
    }

    @Override
    public int updateAiCallTicket(AiCallTicket aiCallTicket)
    {
        return aiCallTicketMapper.updateAiCallTicket(aiCallTicket);
    }

    @Override
    public int deleteAiCallTicketByTicketId(Long ticketId)
    {
        return aiCallTicketMapper.deleteAiCallTicketByTicketId(ticketId);
    }

    @Override
    public int deleteAiCallTicketByTicketIds(Long[] ticketIds)
    {
        return aiCallTicketMapper.deleteAiCallTicketByTicketIds(ticketIds);
    }

    @Override
    public List<AiCallTicket> selectAiCallTicketByAssignUserId(Long assignUserId)
    {
        return aiCallTicketMapper.selectAiCallTicketByAssignUserId(assignUserId);
    }

    @Override
    public int updateTicketStatus(Long ticketId, String status)
    {
        return aiCallTicketMapper.updateTicketStatus(ticketId, status);
    }

    @Override
    public int updateTicketProcess(Long ticketId, String processContent, Long assignUserId, String assignUserName)
    {
        return aiCallTicketMapper.updateTicketProcess(ticketId, processContent, assignUserId, assignUserName);
    }

    @Override
    public int archiveTicket(Long ticketId)
    {
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
}
