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

    /**
     * 查询已超过 SLA 截止时间但尚未标记超时的待处理工单。
     * 供 TicketSlaScheduleTask 定时扫描使用。
     */
    public List<AiCallTicket> selectOvertimePendingTickets();

    /**
     * 标记工单为已超时并记录提醒时间。
     */
    public int markOvertime(@Param("ticketId") Long ticketId, @Param("remindTime") java.util.Date remindTime);

    /**
     * F3：发起转办时回写工单外部协同字段。
     */
    public int updateExternalInfo(@Param("ticketId") Long ticketId,
                                  @Param("externalType") String externalType,
                                  @Param("externalOrgId") Long externalOrgId,
                                  @Param("externalTicketNo") String externalTicketNo,
                                  @Param("externalStatus") String externalStatus,
                                  @Param("externalUpdateTime") java.util.Date externalUpdateTime,
                                  @Param("transferTime") java.util.Date transferTime,
                                  @Param("direction") String direction);

    /**
     * F3：外部回调回写外部状态。
     */
    public int updateExternalCallback(@Param("ticketId") Long ticketId,
                                      @Param("externalTicketNo") String externalTicketNo,
                                      @Param("externalStatus") String externalStatus,
                                      @Param("externalUpdateTime") java.util.Date externalUpdateTime);

    /**
     * F9：查询已超过 SLA 截止时间且仍未办结/归档的工单（含已标记超时，供逐级升级扫描）。
     */
    public List<AiCallTicket> selectEscalatableTickets();
}
