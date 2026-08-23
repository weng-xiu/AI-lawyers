package ai.lawyers.system.service.lawyers.schedule;

import java.util.List;
import ai.lawyers.system.domain.lawyers.schedule.AiWorkShift;

/**
 * 班次定义 Service
 */
public interface IAiWorkShiftService
{
    public AiWorkShift selectAiWorkShiftByShiftId(Long shiftId);

    public List<AiWorkShift> selectAiWorkShiftList(AiWorkShift aiWorkShift);

    /** 查询全部启用班次 */
    public List<AiWorkShift> selectAllEnabled();

    public int insertAiWorkShift(AiWorkShift aiWorkShift);

    public int updateAiWorkShift(AiWorkShift aiWorkShift);

    public int deleteAiWorkShiftByShiftIds(Long[] shiftIds);

    public int deleteAiWorkShiftByShiftId(Long shiftId);
}
