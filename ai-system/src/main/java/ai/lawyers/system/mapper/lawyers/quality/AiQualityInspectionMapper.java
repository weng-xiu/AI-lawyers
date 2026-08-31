package ai.lawyers.system.mapper.lawyers.quality;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.quality.AiQualityInspection;

/**
 * 智能质检 数据层（T4-1）
 *
 * @author ai-lawyers
 */
public interface AiQualityInspectionMapper
{
    /**
     * 查询质检记录
     */
    public AiQualityInspection selectAiQualityInspectionByInspectionId(Long inspectionId);

    /**
     * 按通话记录ID查询质检记录（幂等判断用）
     */
    public AiQualityInspection selectByRecordId(@Param("recordId") Long recordId);

    /**
     * 查询质检记录列表
     */
    public List<AiQualityInspection> selectAiQualityInspectionList(AiQualityInspection query);

    /**
     * 新增质检记录
     */
    public int insertAiQualityInspection(AiQualityInspection inspection);

    /**
     * 更新质检结果（AI 评分）
     */
    public int updateAiQualityInspection(AiQualityInspection inspection);

    /**
     * 条件推进 AI 质检状态（幂等抢占）：仅当当前状态匹配 fromStatus 时更新。
     *
     * @param inspectionId 质检ID
     * @param fromStatus   期望的当前状态（可空表示不校验）
     * @param toStatus     目标状态
     * @return 影响行数（0 表示已被其他线程处理）
     */
    public int casAiStatus(@Param("inspectionId") Long inspectionId,
                           @Param("fromStatus") String fromStatus,
                           @Param("toStatus") String toStatus);

    /**
     * 人工复核
     */
    public int reviewAiQualityInspection(AiQualityInspection inspection);

    /**
     * 回填联动的风险预警ID
     */
    public int updateRiskWarningId(@Param("inspectionId") Long inspectionId,
                                   @Param("riskWarningId") Long riskWarningId);
}
