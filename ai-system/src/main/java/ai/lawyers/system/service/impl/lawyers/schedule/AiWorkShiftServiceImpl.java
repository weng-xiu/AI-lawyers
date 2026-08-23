package ai.lawyers.system.service.impl.lawyers.schedule;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.system.domain.lawyers.schedule.AiWorkShift;
import ai.lawyers.system.mapper.lawyers.schedule.AiWorkShiftMapper;
import ai.lawyers.system.service.lawyers.schedule.IAiWorkShiftService;

/**
 * 班次定义 Service 实现
 */
@Service
public class AiWorkShiftServiceImpl implements IAiWorkShiftService
{
    @Autowired
    private AiWorkShiftMapper aiWorkShiftMapper;

    @Override
    public AiWorkShift selectAiWorkShiftByShiftId(Long shiftId)
    {
        return aiWorkShiftMapper.selectAiWorkShiftByShiftId(shiftId);
    }

    @Override
    public List<AiWorkShift> selectAiWorkShiftList(AiWorkShift aiWorkShift)
    {
        return aiWorkShiftMapper.selectAiWorkShiftList(aiWorkShift);
    }

    @Override
    public List<AiWorkShift> selectAllEnabled()
    {
        return aiWorkShiftMapper.selectAllEnabled();
    }

    @Override
    public int insertAiWorkShift(AiWorkShift aiWorkShift)
    {
        if (aiWorkShift.getStatus() == null)
        {
            aiWorkShift.setStatus(1);
        }
        if (aiWorkShift.getIsOvernight() == null)
        {
            aiWorkShift.setIsOvernight(0);
        }
        aiWorkShift.setCreateTime(DateUtils.getNowDate());
        return aiWorkShiftMapper.insertAiWorkShift(aiWorkShift);
    }

    @Override
    public int updateAiWorkShift(AiWorkShift aiWorkShift)
    {
        aiWorkShift.setUpdateTime(DateUtils.getNowDate());
        return aiWorkShiftMapper.updateAiWorkShift(aiWorkShift);
    }

    @Override
    public int deleteAiWorkShiftByShiftIds(Long[] shiftIds)
    {
        return aiWorkShiftMapper.deleteAiWorkShiftByShiftIds(shiftIds);
    }

    @Override
    public int deleteAiWorkShiftByShiftId(Long shiftId)
    {
        return aiWorkShiftMapper.deleteAiWorkShiftByShiftId(shiftId);
    }
}
