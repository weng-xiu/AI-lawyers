package ai.lawyers.system.service.lawyers.trunk;

import java.util.List;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.domain.lawyers.trunk.AiNumberSegment;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.service.lawyers.trunk.gateway.GatewayHealth;

/**
 * 线路与号段配置管理服务
 */
public interface IAiCallTrunkService
{
    public AiCallTrunk selectAiCallTrunkByTrunkId(Long trunkId);

    public List<AiCallTrunk> selectAiCallTrunkList(AiCallTrunk aiCallTrunk);

    public int insertAiCallTrunk(AiCallTrunk aiCallTrunk);

    public int updateAiCallTrunk(AiCallTrunk aiCallTrunk);

    public int deleteAiCallTrunkByTrunkIds(Long[] trunkIds);

    /** 启用/停用线路 */
    public int changeEnableFlag(Long trunkId, String enableFlag);

    /** 手动测试线路连通性 */
    public GatewayHealth testTrunk(Long trunkId);

    public DialResult testCall(Long trunkId, String calleeNumber);

    // ---------------- 号段管理 ----------------

    public List<AiNumberSegment> selectAiNumberSegmentList(AiNumberSegment aiNumberSegment);

    public int insertAiNumberSegment(AiNumberSegment aiNumberSegment);

    public int updateAiNumberSegment(AiNumberSegment aiNumberSegment);

    public int deleteAiNumberSegmentBySegmentIds(Long[] segmentIds);
}
