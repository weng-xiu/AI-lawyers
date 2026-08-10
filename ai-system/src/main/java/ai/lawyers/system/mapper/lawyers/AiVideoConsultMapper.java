package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiVideoConsult;

public interface AiVideoConsultMapper
{
    public AiVideoConsult selectAiVideoConsultByConsultId(Long consultId);
    public List<AiVideoConsult> selectAiVideoConsultList(AiVideoConsult aiVideoConsult);
    public int insertAiVideoConsult(AiVideoConsult aiVideoConsult);
    public int updateAiVideoConsult(AiVideoConsult aiVideoConsult);
    public int deleteAiVideoConsultByConsultId(Long consultId);
    public int deleteAiVideoConsultByConsultIds(Long[] consultIds);
}
