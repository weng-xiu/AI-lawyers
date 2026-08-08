package ai.lawyers.system.mapper.lawyers.trunk;

import java.util.List;
import ai.lawyers.system.domain.lawyers.trunk.AiNumberSegment;

/**
 * 号段-运营商路由 Mapper
 */
public interface AiNumberSegmentMapper
{
    public AiNumberSegment selectAiNumberSegmentBySegmentId(Long segmentId);

    public List<AiNumberSegment> selectAiNumberSegmentList(AiNumberSegment aiNumberSegment);

    /** 加载全部启用号段（选路引擎启动时构建内存字典树/Map） */
    public List<AiNumberSegment> selectAllEnabled();

    public int insertAiNumberSegment(AiNumberSegment aiNumberSegment);

    public int updateAiNumberSegment(AiNumberSegment aiNumberSegment);

    public int deleteAiNumberSegmentBySegmentId(Long segmentId);

    public int deleteAiNumberSegmentBySegmentIds(Long[] segmentIds);
}
