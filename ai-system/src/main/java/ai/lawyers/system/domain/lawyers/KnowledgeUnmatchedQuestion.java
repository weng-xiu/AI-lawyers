package ai.lawyers.system.domain.lawyers;

import java.util.Date;

/**
 * F8 未命中问句排行行（按问句哈希聚合）。
 *
 * @author ai-lawyers
 */
public class KnowledgeUnmatchedQuestion
{
    /** 归一化问句哈希（分组键，也用于转草稿去重） */
    private String queryHash;

    /** 该组内任一条脱敏问句文本（代表问句） */
    private String queryText;

    /** 出现次数 */
    private Long askCount;

    /** 首次出现时间 */
    private Date firstTime;

    /** 最近出现时间 */
    private Date lastTime;

    /** 是否已转知识草稿：0否 1是 */
    private Integer converted;

    public String getQueryHash()
    {
        return queryHash;
    }

    public void setQueryHash(String queryHash)
    {
        this.queryHash = queryHash;
    }

    public String getQueryText()
    {
        return queryText;
    }

    public void setQueryText(String queryText)
    {
        this.queryText = queryText;
    }

    public Long getAskCount()
    {
        return askCount;
    }

    public void setAskCount(Long askCount)
    {
        this.askCount = askCount;
    }

    public Date getFirstTime()
    {
        return firstTime;
    }

    public void setFirstTime(Date firstTime)
    {
        this.firstTime = firstTime;
    }

    public Date getLastTime()
    {
        return lastTime;
    }

    public void setLastTime(Date lastTime)
    {
        this.lastTime = lastTime;
    }

    public Integer getConverted()
    {
        return converted;
    }

    public void setConverted(Integer converted)
    {
        this.converted = converted;
    }
}
