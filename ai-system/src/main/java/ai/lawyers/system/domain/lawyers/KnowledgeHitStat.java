package ai.lawyers.system.domain.lawyers;

import java.util.Date;

/**
 * F8 高频命中知识排行行。
 *
 * @author ai-lawyers
 */
public class KnowledgeHitStat
{
    /** 知识ID */
    private Long knowledgeId;

    /** 知识标题（知识已删除时为空） */
    private String title;

    /** 作为最高分命中的次数 */
    private Long hitCount;

    /** 最近命中时间 */
    private Date lastTime;

    public Long getKnowledgeId()
    {
        return knowledgeId;
    }

    public void setKnowledgeId(Long knowledgeId)
    {
        this.knowledgeId = knowledgeId;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public Long getHitCount()
    {
        return hitCount;
    }

    public void setHitCount(Long hitCount)
    {
        this.hitCount = hitCount;
    }

    public Date getLastTime()
    {
        return lastTime;
    }

    public void setLastTime(Date lastTime)
    {
        this.lastTime = lastTime;
    }
}
