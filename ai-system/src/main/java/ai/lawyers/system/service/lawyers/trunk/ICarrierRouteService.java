package ai.lawyers.system.service.lawyers.trunk;

import java.util.List;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.domain.lawyers.trunk.AiNumberSegment;

/**
 * 运营商识别与线路选路服务
 */
public interface ICarrierRouteService
{
    /**
     * 根据被叫号码前缀识别所属运营商。
     *
     * @param calleeNumber 被叫号码（支持带 +86、空格、横线等格式）
     * @return 运营商编码，无法识别返回 "00"
     */
    String recognizeCarrier(String calleeNumber);

    /**
     * 识别号码归属号段（含省市信息）。
     *
     * @param calleeNumber 被叫号码
     * @return 号段对象，无法识别返回 null
     */
    AiNumberSegment recognizeSegment(String calleeNumber);

    /**
     * 为一次外呼选择候选线路列表（已按优先级排序）。
     *
     * 排序规则：同运营商优先 → 主用优先 → priority 升序 → 健康度优先
     *          → 权重降序 → 当前并发升序（负载均衡）。
     *
     * @param carrier        目标运营商
     * @param excludeTrunkIds 需要排除的线路ID（故障切换时排除已失败的线路）
     * @return 候选线路列表，可能为空
     */
    List<AiCallTrunk> selectCandidateTrunks(String carrier, List<Long> excludeTrunkIds);

    /**
     * 按线路编码强制指定线路。
     */
    AiCallTrunk getTrunkByCode(String trunkCode);

    /**
     * 刷新号段缓存（号段数据变更后调用）。
     */
    void refreshSegmentCache();
}
