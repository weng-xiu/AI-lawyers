package ai.lawyers.system.mapper.lawyers.risk;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.risk.AiCallRiskRecord;

/**
 * 呼叫行为风控评估记录 Mapper（P2-15）
 *
 * @author ai-lawyers
 */
public interface AiCallRiskRecordMapper
{
    /** 新增评估记录 */
    int insertRisk(AiCallRiskRecord record);

    /** 更新复核信息（复核状态/复核人/时间/说明/置底规则ID） */
    int updateReview(AiCallRiskRecord record);

    /** 按ID查询 */
    AiCallRiskRecord selectRiskById(Long riskId);

    /** 查询号码在指定复核状态的最近一条记录（幂等判断用） */
    AiCallRiskRecord selectLatestByIndex(@Param("callerNumberIndex") String callerNumberIndex,
                                         @Param("reviewStatus") String reviewStatus);

    /** 条件查询（复核状态/风险等级/脱敏号码/时间区间） */
    List<AiCallRiskRecord> selectRiskList(AiCallRiskRecord query);
}
