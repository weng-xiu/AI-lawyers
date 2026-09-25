package ai.lawyers.system.service.lawyers.stat;

import java.util.List;
import java.util.Map;
import ai.lawyers.system.domain.lawyers.stat.AiModelCallLog;

/**
 * 大模型调用成本与质量看板 Service（P3-E5）
 *
 * <p>数据源 ai_model_call_log（chat/embed/rerank 全量异步埋点），
 * 时间口径与统计报表一致：[beginTime 00:00, endTime+1天 00:00) 左闭右开。</p>
 *
 * @author ai-lawyers
 * @date 2026-09-25
 */
public interface IModelCostService
{
    /** 概览：调用量/成功率/Token/估算费用/耗时 + 单位通话成本（分母为区间话单量） */
    Map<String, Object> overview(String beginTime, String endTime);

    /** 按日趋势 */
    List<Map<String, Object>> trend(String beginTime, String endTime);

    /** 按模型分布 */
    List<Map<String, Object>> byModel(String beginTime, String endTime);

    /** 按调用类型 + 业务场景分布 */
    List<Map<String, Object>> byScene(String beginTime, String endTime);

    /** 明细分页查询（分页由 PageHelper 拦截） */
    List<AiModelCallLog> logList(AiModelCallLog query);
}
