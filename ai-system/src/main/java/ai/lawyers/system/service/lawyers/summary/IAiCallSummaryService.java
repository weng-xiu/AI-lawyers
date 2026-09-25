package ai.lawyers.system.service.lawyers.summary;

import ai.lawyers.system.domain.lawyers.AiCallRecord;

/**
 * P3-E3（一阶段）话后 AI 通话小结服务。
 *
 * <p>挂断/质检完成后基于通话 ASR 转写（复用 ai_call_record.transcript，
 * 缺失时降级使用人工登记的咨询内容与解答内容）调大模型生成结构化小结：
 * 案情摘要、争议焦点、法律意见、待办事项、回访建议，回写话单供坐席复盘与生成工单预填。</p>
 *
 * <p>可靠性约定：</p>
 * <ul>
 *   <li>状态机 0待生成/1生成中/2已生成/3失败，CAS 抢占防并发重复生成；</li>
 *   <li>已生成默认幂等跳过，force=true 可重新生成；</li>
 *   <li>模型异常/无文本等失败统一落库为状态 3（含失败原因），<b>不向调用方抛异常</b>，
 *       保证质检队列联动等异步场景不被小结失败拖垮。</li>
 * </ul>
 *
 * @author ai-lawyers
 */
public interface IAiCallSummaryService
{
    /**
     * 生成（或重新生成）话单的 AI 通话小结。
     *
     * @param recordId 话单 ID
     * @param force    true=已生成也重新生成；false=已生成则幂等跳过
     * @return 生成后的最新话单（含 aiSummaryStatus/aiSummary/aiSummaryFailReason）；
     *         话单不存在或功能开关关闭时返回 null/原话单
     */
    AiCallRecord generateSummary(Long recordId, boolean force);
}
