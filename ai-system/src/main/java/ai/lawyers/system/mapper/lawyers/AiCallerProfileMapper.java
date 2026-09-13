package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.AiCallerProfile;

public interface AiCallerProfileMapper
{
    public AiCallerProfile selectAiCallerProfileByProfileId(Long profileId);

    public AiCallerProfile selectAiCallerProfileByCallerNumber(String callerNumber);

    public List<AiCallerProfile> selectAiCallerProfileList(AiCallerProfile aiCallerProfile);

    public int insertAiCallerProfile(AiCallerProfile aiCallerProfile);

    public int updateAiCallerProfile(AiCallerProfile aiCallerProfile);

    /** F1/F2：更新语种偏好与适老关怀模式 */
    public int updatePreference(@Param("profileId") Long profileId,
                                @Param("languagePreference") String languagePreference,
                                @Param("careMode") Integer careMode);

    public int deleteAiCallerProfileByProfileId(Long profileId);

    public int deleteAiCallerProfileByProfileIds(Long[] profileIds);
}
