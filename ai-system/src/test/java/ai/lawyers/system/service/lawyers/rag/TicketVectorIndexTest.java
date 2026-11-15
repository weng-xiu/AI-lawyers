package ai.lawyers.system.service.lawyers.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ai.lawyers.system.domain.lawyers.AiCallTicket;

/**
 * P1-8：{@link TicketVectorIndex} 工单语义向量内存索引测试。
 *
 * <p>覆盖重建时的向量过滤、upsert 新增/替换、remove、余弦近邻排序与 Top-N 截断、
 * 空输入与就绪状态。</p>
 *
 * @author ai-lawyers
 */
class TicketVectorIndexTest
{
    private TicketVectorIndex index;

    @BeforeEach
    void setUp()
    {
        index = new TicketVectorIndex();
    }

    @Test
    void rebuildLoadsOnlyValidTickets()
    {
        List<AiCallTicket> tickets = new ArrayList<>();
        tickets.add(ticket(1L, 2, VectorUtils.toBytes(new float[] { 1f, 0f })));
        tickets.add(ticket(null, 2, VectorUtils.toBytes(new float[] { 1f, 0f })));
        tickets.add(ticket(2L, null, VectorUtils.toBytes(new float[] { 1f, 0f })));
        tickets.add(ticket(3L, 0, VectorUtils.toBytes(new float[] { 1f, 0f })));
        tickets.add(ticket(4L, 2, new byte[] { 1, 2, 3 }));
        tickets.add(ticket(5L, 2, null));

        assertThat(index.rebuild(tickets)).isEqualTo(1);
        assertThat(index.size()).isEqualTo(1);
        assertThat(index.isReady()).isTrue();
    }

    @Test
    void rebuildNullClearsIndex()
    {
        index.rebuild(Arrays.asList(ticket(1L, 2, VectorUtils.toBytes(new float[] { 1f, 0f }))));

        assertThat(index.rebuild(null)).isZero();
        assertThat(index.size()).isZero();
        assertThat(index.isReady()).isFalse();
    }

    @Test
    void upsertAddsAndReplaces()
    {
        index.upsert(1L, new float[] { 1f, 0f });
        assertThat(index.size()).isEqualTo(1);

        index.upsert(1L, new float[] { 0f, 1f });
        assertThat(index.size()).isEqualTo(1);

        List<TicketVectorIndex.Hit> hits = index.searchNearest(new float[] { 0f, 1f }, 5);
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getTicketId()).isEqualTo(1L);
        assertThat(hits.get(0).getScore()).isEqualTo(1f);
    }

    @Test
    void upsertIgnoresInvalidArguments()
    {
        index.upsert(null, new float[] { 1f });
        index.upsert(1L, null);
        index.upsert(1L, new float[0]);

        assertThat(index.size()).isZero();
    }

    @Test
    void removeDeletesTicket()
    {
        index.upsert(1L, new float[] { 1f, 0f });
        index.upsert(2L, new float[] { 0f, 1f });

        index.remove(1L);
        index.remove(null);

        assertThat(index.size()).isEqualTo(1);
        assertThat(index.searchNearest(new float[] { 1f, 0f }, 5)).isEmpty();
    }

    @Test
    void searchNearestRanksAndLimits()
    {
        index.rebuild(Arrays.asList(
                ticket(1L, 2, VectorUtils.toBytes(new float[] { 1f, 0f })),
                ticket(2L, 2, VectorUtils.toBytes(new float[] { 1f, 1f })),
                ticket(3L, 2, VectorUtils.toBytes(new float[] { 0f, 1f }))));

        List<TicketVectorIndex.Hit> hits = index.searchNearest(new float[] { 1f, 0f }, 2);

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).getTicketId()).isEqualTo(1L);
        assertThat(hits.get(0).getScore()).isGreaterThan(hits.get(1).getScore());
    }

    @Test
    void searchNearestRejectsEmptyInput()
    {
        assertThat(index.searchNearest(new float[] { 1f }, 5)).isEmpty();

        index.upsert(1L, new float[] { 1f });
        assertThat(index.searchNearest(null, 5)).isEmpty();
        assertThat(index.searchNearest(new float[0], 5)).isEmpty();
    }

    @Test
    void searchNearestFiltersNonPositiveSimilarity()
    {
        index.upsert(1L, new float[] { 1f, 0f });

        assertThat(index.searchNearest(new float[] { 0f, 1f }, 5)).isEmpty();
    }

    private AiCallTicket ticket(Long ticketId, Integer dim, byte[] embedding)
    {
        AiCallTicket t = new AiCallTicket();
        t.setTicketId(ticketId);
        t.setEmbeddingDim(dim);
        t.setContentEmbedding(embedding);
        return t;
    }
}
