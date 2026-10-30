package ai.lawyers.system.domain.lawyers;

/**
 * F8 知识检索总览统计（命中率/未命中/平均耗时）。
 *
 * @author ai-lawyers
 */
public class KnowledgeQueryOverview
{
    /** 总问句数 */
    private Long totalCount;

    /** 命中问句数（hit_count > 0） */
    private Long hitCount;

    /** 未命中问句数（hit_count = 0） */
    private Long missCount;

    /** 未命中且未转草稿的待处理问句数 */
    private Long pendingCount;

    /** 平均检索耗时（毫秒） */
    private Double avgLatencyMs;

    public Long getTotalCount()
    {
        return totalCount;
    }

    public void setTotalCount(Long totalCount)
    {
        this.totalCount = totalCount;
    }

    public Long getHitCount()
    {
        return hitCount;
    }

    public void setHitCount(Long hitCount)
    {
        this.hitCount = hitCount;
    }

    public Long getMissCount()
    {
        return missCount;
    }

    public void setMissCount(Long missCount)
    {
        this.missCount = missCount;
    }

    public Long getPendingCount()
    {
        return pendingCount;
    }

    public void setPendingCount(Long pendingCount)
    {
        this.pendingCount = pendingCount;
    }

    public Double getAvgLatencyMs()
    {
        return avgLatencyMs;
    }

    public void setAvgLatencyMs(Double avgLatencyMs)
    {
        this.avgLatencyMs = avgLatencyMs;
    }
}
