package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiCallerProfile;

public interface AiCallerProfileMapper
{
    public AiCallerProfile selectAiCallerProfileByProfileId(Long profileId);

    public AiCallerProfile selectAiCallerProfileByCallerNumber(String callerNumber);

    public List<AiCallerProfile> selectAiCallerProfileList(AiCallerProfile aiCallerProfile);

    public int insertAiCallerProfile(AiCallerProfile aiCallerProfile);

    public int updateAiCallerProfile(AiCallerProfile aiCallerProfile);

    public int deleteAiCallerProfileByProfileId(Long profileId);

    public int deleteAiCallerProfileByProfileIds(Long[] profileIds);
}
