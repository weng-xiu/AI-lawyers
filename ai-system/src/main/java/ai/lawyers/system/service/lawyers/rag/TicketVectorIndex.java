package ai.lawyers.system.service.lawyers.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import ai.lawyers.system.domain.lawyers.AiCallTicket;

/**
 * P1-8：工单语义向量内存索引（与法条 {@link VectorIndex} 物理隔离，互不影响）。
 *
 * <p>零新中间件方案：启动/重建时从 ai_call_ticket.content_embedding 加载已办结工单向量，
 * 驻留 JVM 内存；查询暴力余弦近邻。热线办结工单规模（万级、512~1024 维）下毫秒级，
 * 数据量更大时可平滑替换为向量库（仅需替换本类）。</p>
 *
 * <p>线程模型：volatile 快照 + AtomicReference 整体替换，重建/upsert 期间读无锁。</p>
 *
 * @author ai-lawyers
 */
@Component
public class TicketVectorIndex
{
    /** 索引条目 */
    private static class Entry
    {
        final Long ticketId;
        final float[] vector;

        Entry(Long ticketId, float[] vector)
        {
            this.ticketId = ticketId;
            this.vector = vector;
        }
    }

    /** 命中条目：工单ID + 余弦相似度 */
    public static class Hit
    {
        private final Long ticketId;
        private final float score;

        public Hit(Long ticketId, float score)
        {
            this.ticketId = ticketId;
            this.score = score;
        }

        public Long getTicketId() { return ticketId; }
        public float getScore() { return score; }
    }

    private final AtomicReference<List<Entry>> snapshot = new AtomicReference<>(new ArrayList<>());

    /**
     * 用已向量化工单全量重建（仅收录向量可解析条目），返回载入条数。
     */
    public int rebuild(List<AiCallTicket> tickets)
    {
        List<Entry> entries = new ArrayList<>();
        if (tickets != null)
        {
            for (AiCallTicket t : tickets)
            {
                if (t.getTicketId() == null || t.getContentEmbedding() == null
                        || t.getEmbeddingDim() == null || t.getEmbeddingDim() <= 0)
                {
                    continue;
                }
                float[] vec = VectorUtils.fromBytes(t.getContentEmbedding());
                if (vec.length > 0)
                {
                    entries.add(new Entry(t.getTicketId(), vec));
                }
            }
        }
        snapshot.set(entries);
        return entries.size();
    }

    /** 插入或更新单条工单向量（办结索引用） */
    public void upsert(Long ticketId, float[] vector)
    {
        if (ticketId == null || vector == null || vector.length == 0)
        {
            return;
        }
        synchronized (snapshot)
        {
            List<Entry> current = snapshot.get();
            List<Entry> next = new ArrayList<>(current.size() + 1);
            boolean replaced = false;
            for (Entry e : current)
            {
                if (e.ticketId.equals(ticketId))
                {
                    next.add(new Entry(ticketId, vector));
                    replaced = true;
                }
                else
                {
                    next.add(e);
                }
            }
            if (!replaced)
            {
                next.add(new Entry(ticketId, vector));
            }
            snapshot.set(next);
        }
    }

    /** 移除单条工单向量（工单删除/重开场景） */
    public void remove(Long ticketId)
    {
        if (ticketId == null)
        {
            return;
        }
        synchronized (snapshot)
        {
            List<Entry> current = snapshot.get();
            List<Entry> next = new ArrayList<>(current.size());
            for (Entry e : current)
            {
                if (!e.ticketId.equals(ticketId))
                {
                    next.add(e);
                }
            }
            snapshot.set(next);
        }
    }

    /**
     * 余弦近邻检索，按相似度降序；只保留 score &gt; 0 的条目。
     */
    public List<Hit> searchNearest(float[] queryVector, int topN)
    {
        List<Entry> entries = snapshot.get();
        List<Hit> hits = new ArrayList<>();
        if (queryVector == null || queryVector.length == 0 || entries.isEmpty())
        {
            return hits;
        }
        for (Entry e : entries)
        {
            float score = VectorUtils.cosine(queryVector, e.vector);
            if (score > 0f)
            {
                hits.add(new Hit(e.ticketId, score));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::getScore).reversed());
        if (hits.size() > topN)
        {
            return new ArrayList<>(hits.subList(0, topN));
        }
        return hits;
    }

    public int size()
    {
        return snapshot.get().size();
    }

    public boolean isReady()
    {
        return !snapshot.get().isEmpty();
    }
}
