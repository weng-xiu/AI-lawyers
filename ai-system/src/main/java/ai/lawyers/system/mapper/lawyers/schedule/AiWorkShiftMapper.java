package ai.lawyers.system.mapper.lawyers.schedule;

import java.util.List;
import ai.lawyers.system.domain.lawyers.schedule.AiWorkShift;

/**
 * 班次定义 Mapper
 */
public interface AiWorkShiftMapper
{
    public AiWorkShift selectAiWorkShiftByShiftId(Long shiftId);

    public List<AiWorkShift> selectAiWorkShiftList(AiWorkShift aiWorkShift);

    /** 查询全部启用班次 */
    public List<AiWorkShift> selectAllEnabled();

    public int insertAiWorkShift(AiWorkShift aiWorkShift);

    public int updateAiWorkShift(AiWorkShift aiWorkShift);

    public int deleteAiWorkShiftByShiftId(Long shiftId);

    public int deleteAiWorkShiftByShiftIds(Long[] shiftIds);
}
