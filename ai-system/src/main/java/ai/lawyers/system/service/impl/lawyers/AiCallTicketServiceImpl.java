package ai.lawyers.system.service.impl.lawyers;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.DateUtils;
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
}
