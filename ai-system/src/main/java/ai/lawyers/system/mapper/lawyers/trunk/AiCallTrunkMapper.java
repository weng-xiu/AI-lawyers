package ai.lawyers.system.mapper.lawyers.trunk;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;

/**
 * 运营商中继线路 Mapper
 */
public interface AiCallTrunkMapper
{
    public AiCallTrunk selectAiCallTrunkByTrunkId(Long trunkId);

    public AiCallTrunk selectAiCallTrunkByCode(String trunkCode);

    public List<AiCallTrunk> selectAiCallTrunkList(AiCallTrunk aiCallTrunk);

    /** 查询指定运营商下所有可用线路（启用 + 健康状态可选路），按优先级、权重排序 */
    public List<AiCallTrunk> selectAvailableTrunks(@Param("carrier") String carrier);

    /** 查询全部启用线路（含兜底），用于故障切换与监控 */
    public List<AiCallTrunk> selectAllEnabledTrunks();

    public int insertAiCallTrunk(AiCallTrunk aiCallTrunk);

    public int updateAiCallTrunk(AiCallTrunk aiCallTrunk);

    /** 原子占用一个并发槽位：仅当 current_concurrent < max_concurrent 时 +1 */
    public int tryAcquireConcurrent(@Param("trunkId") Long trunkId);

    /** 释放一个并发槽位 */
    public int releaseConcurrent(@Param("trunkId") Long trunkId);

    /** 重置所有线路的当前并发（应用启动时调用，避免脏数据） */
    public int resetAllConcurrent();

    /** 更新健康状态 */
    public int updateHealthStatus(@Param("trunkId") Long trunkId,
                                  @Param("healthStatus") String healthStatus,
                                  @Param("failCount") Integer failCount);

    /** 记录一次呼叫成功 */
    public int markCallSuccess(@Param("trunkId") Long trunkId, @Param("duration") Integer duration);

    /** 记录一次呼叫失败 */
    public int markCallFail(@Param("trunkId") Long trunkId);

    /** 刷新滚动质量指标 */
    public int updateQualityMetric(AiCallTrunk aiCallTrunk);

    public int deleteAiCallTrunkByTrunkId(Long trunkId);

    public int deleteAiCallTrunkByTrunkIds(Long[] trunkIds);

    /** 按运营商聚合的线路概览 */
    public List<Map<String, Object>> selectCarrierOverview();
}
