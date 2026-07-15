package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.AiCallTicket;

public interface AiCallTicketMapper
{
    public AiCallTicket selectAiCallTicketByTicketId(Long ticketId);

    public AiCallTicket selectAiCallTicketByTicketNo(String ticketNo);

    public List<AiCallTicket> selectAiCallTicketList(AiCallTicket aiCallTicket);

    public int insertAiCallTicket(AiCallTicket aiCallTicket);

    public int updateAiCallTicket(AiCallTicket aiCallTicket);

    public int deleteAiCallTicketByTicketId(Long ticketId);

    public int deleteAiCallTicketByTicketIds(Long[] ticketIds);

    public List<AiCallTicket> selectAiCallTicketByAssignUserId(Long assignUserId);

    public int updateTicketStatus(@Param("ticketId") Long ticketId, @Param("status") String status);

    public int updateTicketProcess(@Param("ticketId") Long ticketId, @Param("processContent") String processContent, @Param("assignUserId") Long assignUserId, @Param("assignUserName") String assignUserName);

    public int archiveTicket(Long ticketId);
}
