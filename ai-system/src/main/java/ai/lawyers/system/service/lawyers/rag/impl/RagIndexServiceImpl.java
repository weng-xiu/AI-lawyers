package ai.lawyers.system.service.lawyers.rag.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeChunkMapper;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeMapper;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.rag.IRagIndexService;
import ai.lawyers.system.service.lawyers.rag.VectorIndex;
import ai.lawyers.system.service.lawyers.rag.VectorUtils;

/**
 * T3 RAG 分块与向量索引服务实现。
 *
 * <p>分块策略：中文按句末标点（。！？；\n）切句后按目标长度聚合，块间不重叠；
 * 每块以"标题"开头并在首块附带法条，保证单块语义自洽、可独立溯源。</p>
 *
 * <p>降级：embedding 服务不可用时，分块照常落库（FULLTEXT/LIKE 关键词路可用），
 * 仅向量路缺失；相关异常只记录日志、不中断建索引。</p>
 *
 * @author ai-lawyers
 */
@Service
public class RagIndexServiceImpl implements IRagIndexService
{
    private static final Logger log = LoggerFactory.getLogger(RagIndexServiceImpl.class);

    /** 单块目标字符数 */
    private static final int DEFAULT_CHUNK_SIZE = 400;

    /** embedding 批量调用大小 */
    private static final int EMBED_BATCH = 16;

    @Autowired
    private AiLegalKnowledgeMapper knowledgeMapper;

    @Autowired
    private AiLegalKnowledgeChunkMapper chunkMapper;

    @Autowired
    private IAiModelConfigService modelConfigService;

    /** P3-C5：注入 VectorIndex 接口（VectorIndexRouter 门面），支持 memory/redis 双后端 */
    @Autowired
    private VectorIndex vectorIndex;

    /** T3 RAG 总开关（关闭则不建向量索引、检索退回旧 LIKE 知识路） */
    @Value("${ai.rag.enabled:true}")
    private boolean ragEnabled;

    /** 建索引单线程（串行，避免 embedding 并发压垮模型服务），守护线程 */
    private ExecutorService indexExecutor;

    @PostConstruct
    public void init()
    {
        indexExecutor = Executors.newSingleThreadExecutor(new ThreadFactory()
        {
            private final AtomicInteger n = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r)
            {
                Thread t = new Thread(r, "rag-index-" + n.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });
        if (ragEnabled)
        {
            // 启动先加载已有向量快照（无 embedding 调用），随后异步全量补齐
            loadIndexOnStartup();
            indexExecutor.submit(this::rebuildAllInternal);
        }
    }

    @PreDestroy
    public void shutdown()
    {
        if (indexExecutor != null)
        {
            indexExecutor.shutdown();
        }
    }

    @Override
    public void rebuildAllAsync()
    {
        if (!ragEnabled)
        {
            log.info("RAG 已关闭，跳过全量重建索引");
            return;
        }
        indexExecutor.submit(this::rebuildAllInternal);
    }

    @Override
    public void rebuildKnowledgeAsync(Long knowledgeId)
    {
        if (!ragEnabled || knowledgeId == null)
        {
            return;
        }
        indexExecutor.submit(() -> rebuildKnowledgeInternal(knowledgeId));
    }

    @Override
    public void onKnowledgeDeleted(Long[] knowledgeIds)
    {
        if (!ragEnabled || knowledgeIds == null || knowledgeIds.length == 0)
        {
            return;
        }
        indexExecutor.submit(() ->
        {
            try
            {
                chunkMapper.deleteByKnowledgeIds(knowledgeIds);
                vectorIndex.rebuild(chunkMapper.selectAllChunksForIndex());
                log.info("RAG 已清理删除知识的分块并刷新索引，数量={}", knowledgeIds.length);
            }
            catch (Exception e)
            {
                log.warn("RAG 清理删除知识分块失败：{}", e.getMessage());
            }
        });
    }

    @Override
    public void loadIndexOnStartup()
    {
        try
        {
            List<AiLegalKnowledgeChunk> chunks = chunkMapper.selectAllChunksForIndex();
            int loaded = vectorIndex.rebuild(chunks);
            log.info("RAG 内存向量索引启动加载完成：分块{} 向量条目{}", chunks == null ? 0 : chunks.size(), loaded);
        }
        catch (Exception e)
        {
            // 分块表尚未建表等情况：降级，不影响启动
            log.warn("RAG 内存向量索引启动加载失败（将在全量重建时重试）：{}", e.getMessage());
        }
    }

    @Override
    public int indexedVectorCount()
    {
        return vectorIndex.size();
    }

    /** 全量重建：清空 → 分块 → 向量化 → 刷新内存索引 */
    private void rebuildAllInternal()
    {
        try
        {
            List<AiLegalKnowledge> knows = knowledgeMapper.selectIndexableKnowledge();
            if (knows == null || knows.isEmpty())
            {
                log.info("RAG 全量重建：无启用且审核通过的知识");
                vectorIndex.rebuild(Collections.emptyList());
                return;
            }
            // 清空旧分块（表不存在时异常向上抛出，由调用方捕获）
            chunkMapper.deleteAll();
            List<AiLegalKnowledgeChunk> allChunks = new ArrayList<>();
            for (AiLegalKnowledge k : knows)
            {
                try
                {
                    List<AiLegalKnowledgeChunk> chunks = chunkKnowledge(k);
                    for (AiLegalKnowledgeChunk c : chunks)
                    {
                        chunkMapper.insertChunk(c);
                    }
                    allChunks.addAll(chunks);
                }
                catch (Exception e)
                {
                    log.warn("RAG 知识分块失败 knowledgeId={}: {}", k.getKnowledgeId(), e.getMessage());
                }
            }
            embedChunks(allChunks);
            // 重新从库加载（含回填的 embedding 字节）刷新内存索引
            int loaded = vectorIndex.rebuild(chunkMapper.selectAllChunksForIndex());
            log.info("RAG 全量重建完成：知识{} 分块{} 向量条目{}", knows.size(), allChunks.size(), loaded);
        }
        catch (Exception e)
        {
            log.warn("RAG 全量重建失败（关键词路仍可用）：{}", e.getMessage());
        }
    }

    /** 单条知识重建 */
    private void rebuildKnowledgeInternal(Long knowledgeId)
    {
        try
        {
            AiLegalKnowledge k = knowledgeMapper.selectAiLegalKnowledgeById(knowledgeId);
            chunkMapper.deleteByKnowledgeId(knowledgeId);
            if (k == null || !"0".equals(k.getStatus()) || !"1".equals(k.getAuditStatus()))
            {
                // 知识被删/停用/未过审：仅清理旧分块后刷新索引
                vectorIndex.rebuild(chunkMapper.selectAllChunksForIndex());
                log.info("RAG 单条重建：knowledgeId={} 不可索引，已清理分块", knowledgeId);
                return;
            }
            List<AiLegalKnowledgeChunk> chunks = chunkKnowledge(k);
            for (AiLegalKnowledgeChunk c : chunks)
            {
                chunkMapper.insertChunk(c);
            }
            // embedChunks 内部已批量回填 embedding 到库
            embedChunks(chunks);
            vectorIndex.rebuild(chunkMapper.selectAllChunksForIndex());
            log.info("RAG 单条重建完成 knowledgeId={} 分块{}", knowledgeId, chunks.size());
        }
        catch (Exception e)
        {
            log.warn("RAG 单条重建失败 knowledgeId={}：{}", knowledgeId, e.getMessage());
        }
    }

    /**
     * 分块：标题开头 + 按句末标点切句后聚合到目标长度；首块附带法条。
     */
    private List<AiLegalKnowledgeChunk> chunkKnowledge(AiLegalKnowledge k)
    {
        List<AiLegalKnowledgeChunk> result = new ArrayList<>();
        String content = StringUtils.isNotEmpty(k.getContent()) ? k.getContent().trim() : "";
        if (content.isEmpty())
        {
            return result;
        }
        List<String> sentences = splitSentences(content);
        StringBuilder current = new StringBuilder();
        int chunkIndex = 0;
        for (String sentence : sentences)
        {
            if (current.length() > 0 && current.length() + sentence.length() > DEFAULT_CHUNK_SIZE)
            {
                result.add(buildChunk(k, chunkIndex++, current.toString(), chunkIndex == 1));
                current.setLength(0);
            }
            current.append(sentence);
        }
        if (current.length() > 0)
        {
            result.add(buildChunk(k, chunkIndex, current.toString(), chunkIndex == 0));
        }
        return result;
    }

    private AiLegalKnowledgeChunk buildChunk(AiLegalKnowledge k, int index, String body, boolean firstChunk)
    {
        AiLegalKnowledgeChunk c = new AiLegalKnowledgeChunk();
        c.setKnowledgeId(k.getKnowledgeId());
        c.setTitle(k.getTitle());
        c.setChunkIndex(index);
        c.setCategoryId(k.getCategoryId());
        c.setLawArticle(k.getLawArticle());
        c.setSource(k.getSource());
        c.setStatus("0");
        c.setCreateBy("rag-index");
        StringBuilder text = new StringBuilder();
        text.append(k.getTitle() == null ? "" : k.getTitle()).append("。");
        if (firstChunk && StringUtils.isNotEmpty(k.getLawArticle()))
        {
            text.append(k.getLawArticle()).append("。");
        }
        text.append(body);
        c.setChunkContent(text.toString());
        c.setEmbeddingDim(0);
        return c;
    }

    /** 中文句切分：按 。！？；!?;\n 切句并保留标点 */
    private List<String> splitSentences(String text)
    {
        List<String> sentences = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++)
        {
            char ch = text.charAt(i);
            sb.append(ch);
            if (ch == '。' || ch == '！' || ch == '？' || ch == '；'
                    || ch == '!' || ch == '?' || ch == ';' || ch == '\n')
            {
                String s = sb.toString().trim();
                if (!s.isEmpty())
                {
                    sentences.add(s);
                }
                sb.setLength(0);
            }
        }
        String tail = sb.toString().trim();
        if (!tail.isEmpty())
        {
            sentences.add(tail);
        }
        return sentences;
    }

    /**
     * 批量向量化分块并回填 embedding 字节到实体；失败仅告警（关键词路仍可用）。
     */
    private void embedChunks(List<AiLegalKnowledgeChunk> chunks)
    {
        if (chunks == null || chunks.isEmpty())
        {
            return;
        }
        try
        {
            for (int i = 0; i < chunks.size(); i += EMBED_BATCH)
            {
                int end = Math.min(i + EMBED_BATCH, chunks.size());
                List<AiLegalKnowledgeChunk> batch = chunks.subList(i, end);
                List<String> texts = new ArrayList<>(batch.size());
                for (AiLegalKnowledgeChunk c : batch)
                {
                    texts.add(c.getChunkContent());
                }
                List<float[]> vectors = modelConfigService.embedTexts(texts);
                for (int j = 0; j < batch.size() && j < vectors.size(); j++)
                {
                    float[] vec = vectors.get(j);
                    AiLegalKnowledgeChunk c = batch.get(j);
                    c.setEmbedding(VectorUtils.toBytes(vec));
                    c.setEmbeddingDim(vec.length);
                    // 全量重建场景直接回填库；单条重建场景由调用方统一回填
                    chunkMapper.updateChunkEmbedding(c.getChunkId(), c.getEmbedding(), vec.length);
                }
            }
        }
        catch (Exception e)
        {
            log.warn("RAG 向量化失败，本次仅建立关键词索引：{}", e.getMessage());
        }
    }
}
