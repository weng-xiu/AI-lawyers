package ai.lawyers.system.service.lawyers.rag;

import java.io.Serializable;

/**
 * N3 RAG 引用溯源结构化字段：一条答复可追溯到具体知识文档与分块，前端据此渲染可点击来源。
 *
 * <p>与旧 {@code List<String> sources} 并存（字符串保留兼容），本类承载可机读的溯源信息，
 * 支持前端按端（后台 ai-ui / 公众端 aiuser-ui）拼接详情链接。</p>
 *
 * @author ai-lawyers
 */
public class RagSourceRef implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 知识库文档ID（可点回原文） */
    private Long knowledgeId;

    /** 分块ID（精确到命中片段） */
    private Long chunkId;

    /** 文档标题 */
    private String title;

    /** 命中分块正文（截断后的片段，供来源预览） */
    private String snippet;

    /** 分块关联法条（可空） */
    private String lawArticle;

    /** 融合得分（可空，供前端排序展示） */
    private Double score;

    /** 后台详情相对路径（ai-ui 拼接路由用） */
    private String adminPath;

    public RagSourceRef() {}

    public Long getKnowledgeId()
    {
        return knowledgeId;
    }

    public void setKnowledgeId(Long knowledgeId)
    {
        this.knowledgeId = knowledgeId;
    }

    public Long getChunkId()
    {
        return chunkId;
    }

    public void setChunkId(Long chunkId)
    {
        this.chunkId = chunkId;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getSnippet()
    {
        return snippet;
    }

    public void setSnippet(String snippet)
    {
        this.snippet = snippet;
    }

    public String getLawArticle()
    {
        return lawArticle;
    }

    public void setLawArticle(String lawArticle)
    {
        this.lawArticle = lawArticle;
    }

    public Double getScore()
    {
        return score;
    }

    public void setScore(Double score)
    {
        this.score = score;
    }

    public String getAdminPath()
    {
        return adminPath;
    }

    public void setAdminPath(String adminPath)
    {
        this.adminPath = adminPath;
    }
}
