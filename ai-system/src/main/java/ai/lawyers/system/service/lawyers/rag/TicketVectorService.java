package ai.lawyers.system.service.lawyers.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder;

/**
 * P1-8（V2.59）：工单语义向量索引编排 + 相似工单检索。
 *
 * <p>职责：① 启动从 content_embedding 加载内存索引（不调 embedding）并异步补齐存量；
 * ② 工单办结时 {@link #indexTicketAsync} 生成向量、回填表、刷内存索引；
 * ③ {@link #findSimilar} 供 Copilot 以完整通话文本做语义近邻（embedding 不可用返回空，
 * 上层回退旧 LIKE 短语路）。建索引单线程串行，避免 embedding 并发压垮模型服务。</p>
 *
 * @author ai-lawyers
 */
@Service
public class TicketVectorService
{
    private static final Logger log = LoggerFactory.getLogger(TicketVectorService.class);

    @Autowired
    private AiCallTicketMapper ticketMapper;

    @Autowired(required = false)
    private IAiModelConfigService modelConfigService;

    @Autowired
    private TicketVectorIndex ticketIndex;

    /** 工单语义检索总开关（关闭则 Copilot 回退 LIKE 短语） */
    @Value("${ai.copilot.ticket-vector-enabled:true}")
    private boolean vectorEnabled;

    /** 相似度阈值（余弦，低于此值不推荐，避免噪声工单） */
    @Value("${ai.copilot.ticket-similarity-threshold:0.5}")
    private float similarityThreshold;

    /** 建索引单线程（守护线程，串行） */
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
                Thread t = new Thread(r, "ticket-vector-index-" + n.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });
        if (vectorEnabled)
        {
            loadIndexOnStartup();
            indexExecutor.submit(this::backfillInternal);
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

    public boolean isReady()
    {
        return vectorEnabled && ticketIndex.isReady();
    }

    /** 内存索引当前条数 */
    public int indexedCount()
    {
        return ticketIndex.size();
    }

    /**
     * 从内存索引即时移除工单（工单删除时调用；表中行随 DELETE 一并清除）。
     */
    public void removeTicket(Long ticketId)
    {
        ticketIndex.remove(ticketId);
    }

    /**
     * 异步索引单条工单（办结/归档时调用）；向量已存在也会幂等重算。
     */
    public void indexTicketAsync(Long ticketId)
    {
        if (!vectorEnabled || ticketId == null)
        {
            return;
        }
        indexExecutor.submit(() -> indexTicketInternal(ticketId));
    }

    /**
     * 全量重建：对全部办结/归档工单重新 embedding（异步）。
     */
    public void rebuildAllAsync()
    {
        if (!vectorEnabled)
        {
            log.info("工单语义检索已关闭，跳过全量重建");
            return;
        }
        indexExecutor.submit(this::rebuildAllInternal);
    }

    /**
     * 语义相似工单检索。
     *
     * @param queryText       查询文本（完整通话文本，语义比短语完整）
     * @param excludeRecordId 排除当前通话记录ID（可空）
     * @param limit           返回条数
     * @return 按相似度降序的办结工单；向量路不可用/无命中返回空列表（上层回退 LIKE）
     */
    public List<AiCallTicket> findSimilar(String queryText, Long excludeRecordId, int limit)
    {
        if (!vectorEnabled || !ticketIndex.isReady()
                || StringUtils.isEmpty(queryText) || limit <= 0)
        {
            return new ArrayList<>();
        }
        try
        {
            List<float[]> qVec = modelConfigService.embedTexts(
                    Collections.singletonList(queryText), AiModelCallLogRecorder.SCENE_RAG);
            if (qVec == null || qVec.isEmpty() || qVec.get(0).length == 0)
            {
                return new ArrayList<>();
            }
            // 多取候选：阈值过滤与排除当前记录后仍够 limit
            List<TicketVectorIndex.Hit> hits = ticketIndex.searchNearest(
                    qVec.get(0), Math.max(limit * 3, limit));
            List<Long> ids = new ArrayList<>();
            Map<Long, Float> scores = new LinkedHashMap<>();
            float threshold = similarityThreshold;
            for (TicketVectorIndex.Hit hit : hits)
            {
                if (hit.getScore() >= threshold && !ids.contains(hit.getTicketId()))
                {
                    ids.add(hit.getTicketId());
                    scores.put(hit.getTicketId(), hit.getScore());
                }
            }
            if (ids.isEmpty())
            {
                return new ArrayList<>();
            }
            List<AiCallTicket> tickets = ticketMapper.selectAiCallTicketByIds(ids);
            List<AiCallTicket> out = new ArrayList<>();
            if (tickets != null)
            {
                // 按相似度顺序输出（selectAiCallTicketByIds 不保证顺序）
                Map<Long, AiCallTicket> byId = new LinkedHashMap<>();
                for (AiCallTicket t : tickets)
                {
                    byId.put(t.getTicketId(), t);
                }
                for (Long id : ids)
                {
                    AiCallTicket t = byId.get(id);
                    if (t != null && (excludeRecordId == null
                            || !excludeRecordId.equals(t.getRecordId())))
                    {
                        out.add(t);
                        if (out.size() >= limit)
                        {
                            break;
                        }
                    }
                }
            }
            return out;
        }
        catch (Exception e)
        {
            log.debug("工单语义检索跳过（embedding 不可用）：{}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 启动加载已有向量（无 embedding 调用） */
    private void loadIndexOnStartup()
    {
        try
        {
            List<AiCallTicket> tickets = ticketMapper.selectVectorIndexTickets();
            int loaded = ticketIndex.rebuild(tickets);
            log.info("工单向量索引启动加载完成：{} 条", loaded);
        }
        catch (Exception e)
        {
            // 列/表尚未迁移时降级，不影响启动
            log.warn("工单向量索引启动加载失败（将在补齐任务中重试）：{}", e.getMessage());
        }
    }

    /** 补齐存量已办结但无向量的工单 */
    private void backfillInternal()
    {
        try
        {
            List<AiCallTicket> pending = ticketMapper.selectClosedTicketsWithoutEmbedding();
            if (pending == null || pending.isEmpty())
            {
                return;
            }
            log.info("工单向量补齐：{} 条待处理", pending.size());
            for (AiCallTicket t : pending)
            {
                try
                {
                    indexTicketInternal(t.getTicketId());
                }
                catch (Exception e)
                {
                    log.warn("工单向量补齐失败 ticketId={}: {}", t.getTicketId(), e.getMessage());
                }
            }
        }
        catch (Exception e)
        {
            log.warn("工单向量补齐扫描失败：{}", e.getMessage());
        }
    }

    /** 全量重建内部逻辑 */
    private void rebuildAllInternal()
    {
        try
        {
            // 循环补齐直到没有待处理工单（每批 200）
            int total = 0;
            List<AiCallTicket> pending;
            do
            {
                pending = ticketMapper.selectClosedTicketsWithoutEmbedding();
                if (pending == null || pending.isEmpty())
                {
                    break;
                }
                for (AiCallTicket t : pending)
                {
                    indexTicketInternal(t.getTicketId());
                    total++;
                }
            }
            while (pending.size() >= 200);
            log.info("工单向量全量重建完成：{} 条，当前索引 {}", total, ticketIndex.size());
        }
        catch (Exception e)
        {
            log.warn("工单向量全量重建失败：{}", e.getMessage());
        }
    }

    /** 读工单 → embedding(title+content) → 回填表 → 刷内存索引 */
    private void indexTicketInternal(Long ticketId)
    {
        try
        {
            if (modelConfigService == null)
            {
                throw new IllegalStateException("模型配置服务未装配");
            }
            AiCallTicket ticket = ticketMapper.selectAiCallTicketByTicketId(ticketId);
            if (ticket == null
                    || (!"2".equals(ticket.getStatus()) && !"3".equals(ticket.getStatus())))
            {
                // 工单不存在或未办结：从索引移除
                ticketIndex.remove(ticketId);
                return;
            }
            String text = buildIndexText(ticket);
            if (StringUtils.isEmpty(text))
            {
                return;
            }
            List<float[]> vectors = modelConfigService.embedTexts(
                    Collections.singletonList(text), AiModelCallLogRecorder.SCENE_RAG_INDEX);
            if (vectors == null || vectors.isEmpty() || vectors.get(0).length == 0)
            {
                log.warn("工单 embedding 返回空 ticketId={}", ticketId);
                return;
            }
            float[] vec = vectors.get(0);
            ticketMapper.updateTicketEmbedding(ticketId, VectorUtils.toBytes(vec), vec.length);
            ticketIndex.upsert(ticketId, vec);
        }
        catch (Exception e)
        {
            log.warn("工单向量索引失败 ticketId={}: {}", ticketId, e.getMessage());
        }
    }

    /** 索引文本：标题 + 内容（标题强化主题语义） */
    private String buildIndexText(AiCallTicket ticket)
    {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotEmpty(ticket.getTitle()))
        {
            sb.append(ticket.getTitle().trim());
        }
        if (StringUtils.isNotEmpty(ticket.getContent()))
        {
            if (sb.length() > 0)
            {
                sb.append('。');
            }
            sb.append(ticket.getContent().trim());
        }
        return sb.toString();
    }
}
