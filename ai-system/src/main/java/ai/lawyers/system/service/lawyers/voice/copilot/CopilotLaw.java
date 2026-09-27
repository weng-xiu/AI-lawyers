package ai.lawyers.system.service.lawyers.voice.copilot;

/**
 * Copilot 法条推荐项（含 chunkId 供点击溯源）。
 *
 * @author ai-lawyers
 */
public class CopilotLaw
{
    /** 知识分块ID（溯源详情） */
    private final Long chunkId;

    /** 知识标题 */
    private final String title;

    /** 法律条号（冗余溯源） */
    private final String lawArticle;

    /** 出处来源 */
    private final String source;

    public CopilotLaw(Long chunkId, String title, String lawArticle, String source)
    {
        this.chunkId = chunkId;
        this.title = title;
        this.lawArticle = lawArticle;
        this.source = source;
    }

    public Long getChunkId() { return chunkId; }
    public String getTitle() { return title; }
    public String getLawArticle() { return lawArticle; }
    public String getSource() { return source; }
}
