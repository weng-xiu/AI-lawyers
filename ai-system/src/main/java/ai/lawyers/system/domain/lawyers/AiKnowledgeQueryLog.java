package ai.lawyers.system.domain.lawyers;

import java.io.Serializable;
import java.util.Date;

/**
 * F8 知识检索查询日志（ai_knowledge_query_log）。
 *
 * <p>每次 RAG 检索异步落一条记录，用于运营侧统计知识命中率、未命中问句排行；
 * 问句文本已脱敏并截断，分组使用归一化问句哈希，避免直接暴露敏感信息。</p>
 *
 * @author ai-lawyers
 */
public class AiKnowledgeQueryLog implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 日志ID */
    private Long logId;

    /** 脱敏并截断后的问句文本 */
    private String queryText;

    /** 归一化问句 SHA-256 前32位（分组键） */
    private String queryHash;

    /** 调用场景 agent/voice_robot/copilot/other */
    private String scene;

    /** 命中知识分块数 */
    private Integer hitCount;

    /** 最高分命中知识ID（无命中为空） */
    private Long topKnowledgeId;

    /** 命中路由 fused/vector/keyword/none */
    private String route;

    /** 检索耗时（毫秒） */
    private Integer latencyMs;

    /** 是否已转知识草稿：0否 1是 */
    private Integer converted;

    /** 记录时间 */
    private Date createTime;

    public Long getLogId()
    {
        return logId;
    }

    public void setLogId(Long logId)
    {
        this.logId = logId;
    }

    public String getQueryText()
    {
        return queryText;
    }

    public void setQueryText(String queryText)
    {
        this.queryText = queryText;
    }

    public String getQueryHash()
    {
        return queryHash;
    }

    public void setQueryHash(String queryHash)
    {
        this.queryHash = queryHash;
    }

    public String getScene()
    {
        return scene;
    }

    public void setScene(String scene)
    {
        this.scene = scene;
    }

    public Integer getHitCount()
    {
        return hitCount;
    }

    public void setHitCount(Integer hitCount)
    {
        this.hitCount = hitCount;
    }

    public Long getTopKnowledgeId()
    {
        return topKnowledgeId;
    }

    public void setTopKnowledgeId(Long topKnowledgeId)
    {
        this.topKnowledgeId = topKnowledgeId;
    }

    public String getRoute()
    {
        return route;
    }

    public void setRoute(String route)
    {
        this.route = route;
    }

    public Integer getLatencyMs()
    {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs)
    {
        this.latencyMs = latencyMs;
    }

    public Integer getConverted()
    {
        return converted;
    }

    public void setConverted(Integer converted)
    {
        this.converted = converted;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }
}
