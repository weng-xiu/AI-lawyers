package ai.lawyers.system.service.lawyers.voice.copilot;

/**
 * Copilot 相似工单推荐项（仅携带列表展示字段）。
 *
 * @author ai-lawyers
 */
public class CopilotTicket
{
    private final Long ticketId;

    private final String ticketNo;

    private final String title;

    /** 状态 0待处理 1处理中 2已完成 3已归档 */
    private final String status;

    /** 内容片段（SQL 侧截断，防长文本上屏） */
    private final String contentSnippet;

    public CopilotTicket(Long ticketId, String ticketNo, String title, String status, String contentSnippet)
    {
        this.ticketId = ticketId;
        this.ticketNo = ticketNo;
        this.title = title;
        this.status = status;
        this.contentSnippet = contentSnippet;
    }

    public Long getTicketId() { return ticketId; }
    public String getTicketNo() { return ticketNo; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public String getContentSnippet() { return contentSnippet; }
}
