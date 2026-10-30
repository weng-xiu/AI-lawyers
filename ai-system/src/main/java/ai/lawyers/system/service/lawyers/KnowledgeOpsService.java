package ai.lawyers.system.service.lawyers;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.KnowledgeHitStat;
import ai.lawyers.system.domain.lawyers.KnowledgeQueryOverview;
import ai.lawyers.system.domain.lawyers.KnowledgeUnmatchedQuestion;
import ai.lawyers.system.mapper.lawyers.AiKnowledgeQueryLogMapper;
import ai.lawyers.system.service.lawyers.rag.KnowledgeQueryLogRecorder;
import ai.lawyers.system.service.lawyers.rag.KnowledgeQueryTextUtils;

/**
 * F8 知识库运营分析服务。
 *
 * <p>基于 {@code ai_knowledge_query_log} 提供只读运营视图：</p>
 * <ul>
 *   <li>总览：总问句 / 命中 / 未命中 / 待处理 / 命中率 / 平均检索耗时；</li>
 *   <li>未命中问句排行：按归一化哈希聚合，展示次数、首末时间与是否已转草稿；</li>
 *   <li>高频命中知识 Top-N；</li>
 *   <li>未命中问句一键转知识草稿（复用 {@link KnowledgeDraftService}，转后标记已转化）。</li>
 * </ul>
 *
 * <p>日志表未创建时读接口降级为 {@code available=false} 与零值，页面给出建表提示，
 * 不影响 RAG 检索与知识库常规功能。统计区间默认近 7 天、最长 90 天。</p>
 *
 * @author ai-lawyers
 */
@Service
public class KnowledgeOpsService
{
    private static final Logger log = LoggerFactory.getLogger(KnowledgeOpsService.class);

    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 90;
    private static final int DEFAULT_TOP_LIMIT = 10;
    private static final int MAX_TOP_LIMIT = 50;
    private static final long WARN_INTERVAL_MS = 60_000L;

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private AiKnowledgeQueryLogMapper queryLogMapper;

    @Autowired
    private KnowledgeDraftService knowledgeDraftService;

    @Autowired
    private KnowledgeQueryLogRecorder queryLogRecorder;

    private volatile long lastReadWarn = 0L;

    /**
     * 统计区间总览。
     *
     * @param beginTime 开始时间（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss，空则近 7 天）
     * @param endTime   结束时间（同上，空则含今日）
     * @return available/total/hitCount/missCount/pendingCount/hitRate/avgLatencyMs/区间
     */
    public Map<String, Object> overview(String beginTime, String endTime)
    {
        DateRange range = resolveRange(beginTime, endTime);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("beginTime", format(range.begin));
        out.put("endTime", format(range.end));
        try
        {
            KnowledgeQueryOverview row = queryLogMapper.selectQueryOverview(range.begin, range.end);
            long total = row == null ? 0L : nz(row.getTotalCount());
            long hit = row == null ? 0L : nz(row.getHitCount());
            long miss = row == null ? 0L : nz(row.getMissCount());
            long pending = row == null ? 0L : nz(row.getPendingCount());
            out.put("available", true);
            out.put("total", total);
            out.put("hitCount", hit);
            out.put("missCount", miss);
            out.put("pendingCount", pending);
            out.put("hitRate", total > 0 ? round1(hit * 100d / total) : 0d);
            out.put("avgLatencyMs", row == null ? 0L : Math.round(az(row.getAvgLatencyMs())));
        }
        catch (Exception e)
        {
            warnReadFailure(e);
            out.put("available", false);
            out.put("total", 0L);
            out.put("hitCount", 0L);
            out.put("missCount", 0L);
            out.put("pendingCount", 0L);
            out.put("hitRate", 0d);
            out.put("avgLatencyMs", 0L);
        }
        return out;
    }

    /**
     * 未命中问句排行（按 query_hash 聚合，次数降序）。分页由 Controller 的 startPage 负责。
     */
    public List<KnowledgeUnmatchedQuestion> listUnmatchedQuestions(String beginTime, String endTime)
    {
        DateRange range = resolveRange(beginTime, endTime);
        try
        {
            List<KnowledgeUnmatchedQuestion> list =
                    queryLogMapper.selectUnmatchedQuestions(range.begin, range.end);
            return list == null ? new ArrayList<>() : list;
        }
        catch (Exception e)
        {
            warnReadFailure(e);
            return new ArrayList<>();
        }
    }

    /**
     * 高频命中知识 Top-N（默认 10，最大 50）。
     */
    public List<KnowledgeHitStat> topKnowledge(String beginTime, String endTime, Integer limit)
    {
        DateRange range = resolveRange(beginTime, endTime);
        int lim = (limit != null && limit >= 1 && limit <= MAX_TOP_LIMIT) ? limit : DEFAULT_TOP_LIMIT;
        try
        {
            List<KnowledgeHitStat> list =
                    queryLogMapper.selectTopKnowledge(range.begin, range.end, lim);
            return list == null ? new ArrayList<>() : list;
        }
        catch (Exception e)
        {
            warnReadFailure(e);
            return new ArrayList<>();
        }
    }

    /**
     * 未命中问句一键转知识草稿，成功后把该哈希的历史日志标记为已转化。
     *
     * @return 新知识ID
     * @throws IllegalArgumentException 问句为空/已转草稿/模型不可用
     */
    public Long draftFromQuestion(String question, String queryHash)
    {
        String hash = StringUtils.isNotEmpty(queryHash) ? queryHash.trim()
                : KnowledgeQueryTextUtils.hash(question);
        Long knowledgeId = knowledgeDraftService.draftFromQuery(question, hash);
        if (knowledgeId != null)
        {
            queryLogRecorder.markConverted(hash);
        }
        return knowledgeId;
    }

    /**
     * 统计区间解析：默认近 7 天（含今日），最长 90 天；非法参数回退默认值。
     */
    DateRange resolveRange(String beginTime, String endTime)
    {
        LocalDate today = LocalDate.now();
        LocalDateTime begin = today.minusDays(DEFAULT_DAYS - 1).atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        LocalDateTime parsedEnd = parseDateTime(endTime, true);
        if (parsedEnd != null)
        {
            end = parsedEnd;
        }
        LocalDateTime parsedBegin = parseDateTime(beginTime, false);
        if (parsedBegin != null)
        {
            begin = parsedBegin;
        }
        if (begin.isAfter(end))
        {
            LocalDateTime tmp = begin;
            begin = end;
            end = tmp;
        }
        if (Duration.between(begin, end).toDays() > MAX_DAYS)
        {
            begin = end.minusDays(MAX_DAYS);
        }
        return new DateRange(Timestamp.valueOf(begin), Timestamp.valueOf(end));
    }

    /** 解析日期：yyyy-MM-dd（结束时间补到 23:59:59）或 yyyy-MM-dd HH:mm[:ss] */
    private LocalDateTime parseDateTime(String value, boolean endOfDay)
    {
        if (StringUtils.isEmpty(value))
        {
            return null;
        }
        String v = value.trim();
        try
        {
            if (v.length() == 10)
            {
                LocalDate date = LocalDate.parse(v);
                return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
            }
            String normalized = v.replace('T', ' ');
            if (normalized.length() >= 19)
            {
                normalized = normalized.substring(0, 19);
                return LocalDateTime.parse(normalized, DATE_TIME);
            }
            if (normalized.length() == 16)
            {
                return LocalDateTime.parse(normalized, HH_MM);
            }
        }
        catch (Exception e)
        {
            log.debug("知识运营统计区间解析失败，使用默认值：{}", value);
        }
        return null;
    }

    private static String format(Timestamp timestamp)
    {
        return timestamp.toLocalDateTime().format(DATE_TIME);
    }

    private static long nz(Long value)
    {
        return value == null ? 0L : value;
    }

    private static double az(Double value)
    {
        return value == null ? 0d : value;
    }

    private static double round1(double value)
    {
        return Math.round(value * 10d) / 10d;
    }

    private void warnReadFailure(Exception e)
    {
        long now = System.currentTimeMillis();
        if (now - lastReadWarn > WARN_INTERVAL_MS)
        {
            lastReadWarn = now;
            log.warn("知识检索查询日志读取失败（限频告警），请确认 SQL ai_system_knowledge_query_log_20261030.sql 已执行：{}",
                    e.getMessage());
        }
    }

    /** 统计区间（左闭右开） */
    static class DateRange
    {
        final Timestamp begin;
        final Timestamp end;

        DateRange(Timestamp begin, Timestamp end)
        {
            this.begin = begin;
            this.end = end;
        }
    }
}
