package ai.lawyers.system.mapper.lawyers;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.AiKnowledgeQueryLog;
import ai.lawyers.system.domain.lawyers.KnowledgeHitStat;
import ai.lawyers.system.domain.lawyers.KnowledgeQueryOverview;
import ai.lawyers.system.domain.lawyers.KnowledgeUnmatchedQuestion;

/**
 * F8 知识检索查询日志数据层。
 *
 * @author ai-lawyers
 */
public interface AiKnowledgeQueryLogMapper
{
    /** 写入一条检索日志 */
    int insertKnowledgeQueryLog(AiKnowledgeQueryLog queryLog);

    /** 区间总览：总问句/命中/未命中/待处理/平均耗时 */
    KnowledgeQueryOverview selectQueryOverview(@Param("beginTime") Date beginTime,
                                               @Param("endTime") Date endTime);

    /** 未命中问句排行（按 query_hash 聚合，次数降序） */
    List<KnowledgeUnmatchedQuestion> selectUnmatchedQuestions(@Param("beginTime") Date beginTime,
                                                              @Param("endTime") Date endTime);

    /** 高频命中知识 Top-N */
    List<KnowledgeHitStat> selectTopKnowledge(@Param("beginTime") Date beginTime,
                                              @Param("endTime") Date endTime,
                                              @Param("limit") int limit);

    /** 未命中问句转草稿后标记已转化 */
    int markConverted(@Param("queryHash") String queryHash);
}
