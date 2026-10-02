package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.risk.AiCallRiskRecord;

/**
 * 呼叫行为风控 Service 接口（P2-15）
 *
 * @author ai-lawyers
 */
public interface IAiCallBehaviorRiskService
{
    /**
     * 扫描窗口内呼叫行为并评估留痕。
     *
     * <p>按号码盲索引聚合来电次数/超短通话/夜间来电/未接率，评分超阈值生成
     * 风控记录；auto-suppress=PRIORITY 时同步自动生成高频置底规则。
     * 已有待复核记录或已存在启用置底规则的号码不重复评估。</p>
     *
     * @return 本次新增风控记录数
     */
    int scanRisk();

    /** 条件查询风控记录 */
    List<AiCallRiskRecord> selectRiskList(AiCallRiskRecord query);

    /**
     * 班组长复核：reviewStatus=1 确认（未生成置底规则时按当前配置生成），
     * 2 忽略。
     *
     * @param review riskId/reviewStatus/reviewRemark（reviewBy 由调用方设置）
     * @return 影响行数
     */
    int reviewRisk(AiCallRiskRecord review);
}
