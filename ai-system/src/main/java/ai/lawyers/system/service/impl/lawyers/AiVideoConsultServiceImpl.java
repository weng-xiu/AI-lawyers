package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.AiVideoConsult;
import ai.lawyers.system.mapper.lawyers.AiVideoConsultMapper;
import ai.lawyers.system.service.lawyers.IAiVideoConsultService;

@Service
public class AiVideoConsultServiceImpl implements IAiVideoConsultService
{
    @Autowired
    private AiVideoConsultMapper aiVideoConsultMapper;

    @Override
    public AiVideoConsult selectAiVideoConsultByConsultId(Long consultId) {
        return aiVideoConsultMapper.selectAiVideoConsultByConsultId(consultId);
    }

    @Override
    public List<AiVideoConsult> selectAiVideoConsultList(AiVideoConsult aiVideoConsult) {
        return aiVideoConsultMapper.selectAiVideoConsultList(aiVideoConsult);
    }

    @Override
    public int insertAiVideoConsult(AiVideoConsult aiVideoConsult) {
        return aiVideoConsultMapper.insertAiVideoConsult(aiVideoConsult);
    }

    @Override
    public int updateAiVideoConsult(AiVideoConsult aiVideoConsult) {
        return aiVideoConsultMapper.updateAiVideoConsult(aiVideoConsult);
    }

    @Override
    public int deleteAiVideoConsultByConsultId(Long consultId) {
        return aiVideoConsultMapper.deleteAiVideoConsultByConsultId(consultId);
    }

    @Override
    public int deleteAiVideoConsultByConsultIds(Long[] consultIds) {
        return aiVideoConsultMapper.deleteAiVideoConsultByConsultIds(consultIds);
    }
}
