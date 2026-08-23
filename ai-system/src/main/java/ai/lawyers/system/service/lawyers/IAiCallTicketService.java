package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiCallTicket;

public interface IAiCallTicketService 
{
    public AiCallTicket selectAiCallTicketByTicketId(Long ticketId);

    public AiCallTicket selectAiCallTicketByTicketNo(String ticketNo);

    public List<AiCallTicket> selectAiCallTicketList(AiCallTicket aiCallTicket);

    public int insertAiCallTicket(AiCallTicket aiCallTicket);

    public int updateAiCallTicket(AiCallTicket aiCallTicket);

    public int deleteAiCallTicketByTicketId(Long ticketId);

    public int deleteAiCallTicketByTicketIds(Long[] ticketIds);

    public List<AiCallTicket> selectAiCallTicketByAssignUserId(Long assignUserId);

    public int updateTicketStatus(Long ticketId, String status);

    public int updateTicketProcess(Long ticketId, String processContent, Long assignUserId, String assignUserName);

    public int archiveTicket(Long ticketId);

    public String generateTicketNo();

    /**
     * 查询工单流转记录（按工单创建/处理/完成/归档的时间点组装）。
     *
     * @param ticketId 工单ID
     * @return 流转记录列表，按时间正序排列，元素包含 title/user/time/description 等字段
     */
    public List<java.util.Map<String, Object>> selectTicketTimeline(Long ticketId);
}
