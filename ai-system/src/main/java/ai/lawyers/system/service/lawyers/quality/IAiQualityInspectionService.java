package ai.lawyers.system.service.lawyers.quality;

import java.util.List;
import ai.lawyers.system.domain.lawyers.quality.AiQualityInspection;

/**
 * 智能质检 Service（T4-1）。
 *
 * <p>通话录音就绪后投递 quality-transcribe 队列，消费者执行：ASR 转写（复用
 * VoiceEngineManager/Whisper 兼容端点）→ AI 多维度评分（chatJson）→ 命中违禁/激烈
 * 情绪联动生成风险预警（T4-2）→ 质检员人工复核闭环。队列不可用时同步降级。</p>
 *
 * @author ai-lawyers
 */
public interface IAiQualityInspectionService
{
    /**
     * 通话录音就绪后提交质检（按采样率入检，幂等：同一 recordId 仅一条质检记录）。
     *
     * @param recordId 通话记录ID
     */
    void submitForRecord(Long recordId);

    /**
     * 执行一次完整质检流程（ASR → AI 评分 → 风险联动）。供队列消费者调用，需幂等。
     *
     * @param recordId 通话记录ID
     */
    void inspect(Long recordId);

    /**
     * 查询质检记录列表
     */
    List<AiQualityInspection> selectInspectionList(AiQualityInspection query);

    /**
     * 查询质检记录详情
     */
    AiQualityInspection selectInspectionById(Long inspectionId);

    /**
     * 人工复核（通过/驳回整改 + 评语）
     *
     * @param inspection 质检记录（inspectionId/reviewStatus/reviewRemark/reviewerId/reviewerName）
     * @return 结果
     */
    int review(AiQualityInspection inspection);
}
