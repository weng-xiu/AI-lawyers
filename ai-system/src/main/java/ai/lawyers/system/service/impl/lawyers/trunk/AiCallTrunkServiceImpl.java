package ai.lawyers.system.service.impl.lawyers.trunk;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.domain.lawyers.trunk.AiNumberSegment;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallTrunkMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiNumberSegmentMapper;
import ai.lawyers.system.service.lawyers.trunk.IAiCallTrunkService;
import ai.lawyers.system.service.lawyers.trunk.ICarrierRouteService;
import ai.lawyers.system.service.lawyers.trunk.gateway.CallGatewayFactory;
import ai.lawyers.system.service.lawyers.trunk.gateway.GatewayHealth;
import ai.lawyers.system.service.lawyers.trunk.gateway.ICallGatewayAdapter;

/**
 * 线路与号段配置管理服务实现
 */
@Service
public class AiCallTrunkServiceImpl implements IAiCallTrunkService
{
    @Autowired
    private AiCallTrunkMapper trunkMapper;

    @Autowired
    private AiNumberSegmentMapper segmentMapper;

    @Autowired
    private CallGatewayFactory gatewayFactory;

    @Autowired
    private ICarrierRouteService carrierRouteService;

    @Override
    public AiCallTrunk selectAiCallTrunkByTrunkId(Long trunkId)
    {
        return trunkMapper.selectAiCallTrunkByTrunkId(trunkId);
    }

    @Override
    public List<AiCallTrunk> selectAiCallTrunkList(AiCallTrunk aiCallTrunk)
    {
        return trunkMapper.selectAiCallTrunkList(aiCallTrunk);
    }

    @Override
    public int insertAiCallTrunk(AiCallTrunk aiCallTrunk)
    {
        aiCallTrunk.setCreateTime(DateUtils.getNowDate());
        return trunkMapper.insertAiCallTrunk(aiCallTrunk);
    }

    @Override
    public int updateAiCallTrunk(AiCallTrunk aiCallTrunk)
    {
        aiCallTrunk.setUpdateTime(DateUtils.getNowDate());
        return trunkMapper.updateAiCallTrunk(aiCallTrunk);
    }

    @Override
    public int deleteAiCallTrunkByTrunkIds(Long[] trunkIds)
    {
        return trunkMapper.deleteAiCallTrunkByTrunkIds(trunkIds);
    }

    @Override
    public int changeEnableFlag(Long trunkId, String enableFlag)
    {
        AiCallTrunk trunk = new AiCallTrunk();
        trunk.setTrunkId(trunkId);
        trunk.setEnableFlag(enableFlag);
        trunk.setUpdateTime(DateUtils.getNowDate());
        return trunkMapper.updateAiCallTrunk(trunk);
    }

    @Override
    public GatewayHealth testTrunk(Long trunkId)
    {
        AiCallTrunk trunk = trunkMapper.selectAiCallTrunkByTrunkId(trunkId);
        if (trunk == null)
        {
            return GatewayHealth.down("线路不存在");
        }
        ICallGatewayAdapter adapter = gatewayFactory.get(trunk);
        if (adapter == null)
        {
            return GatewayHealth.down("未找到网关适配器 vendor=" + trunk.getVendor());
        }
        return adapter.checkHealth(trunk);
    }

    // ---------------- 号段管理 ----------------

    @Override
    public List<AiNumberSegment> selectAiNumberSegmentList(AiNumberSegment aiNumberSegment)
    {
        return segmentMapper.selectAiNumberSegmentList(aiNumberSegment);
    }

    @Override
    public int insertAiNumberSegment(AiNumberSegment aiNumberSegment)
    {
        if (aiNumberSegment.getMatchLength() == null && aiNumberSegment.getSegmentPrefix() != null)
        {
            aiNumberSegment.setMatchLength(aiNumberSegment.getSegmentPrefix().length());
        }
        aiNumberSegment.setCreateTime(DateUtils.getNowDate());
        int rows = segmentMapper.insertAiNumberSegment(aiNumberSegment);
        carrierRouteService.refreshSegmentCache();
        return rows;
    }

    @Override
    public int updateAiNumberSegment(AiNumberSegment aiNumberSegment)
    {
        if (aiNumberSegment.getSegmentPrefix() != null)
        {
            aiNumberSegment.setMatchLength(aiNumberSegment.getSegmentPrefix().length());
        }
        aiNumberSegment.setUpdateTime(DateUtils.getNowDate());
        int rows = segmentMapper.updateAiNumberSegment(aiNumberSegment);
        carrierRouteService.refreshSegmentCache();
        return rows;
    }

    @Override
    public int deleteAiNumberSegmentBySegmentIds(Long[] segmentIds)
    {
        int rows = segmentMapper.deleteAiNumberSegmentBySegmentIds(segmentIds);
        carrierRouteService.refreshSegmentCache();
        return rows;
    }
}
