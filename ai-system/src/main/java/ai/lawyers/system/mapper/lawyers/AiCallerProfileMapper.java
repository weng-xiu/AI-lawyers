package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.AiCallerProfile;

public interface AiCallerProfileMapper
{
    public AiCallerProfile selectAiCallerProfileByProfileId(Long profileId);

    /** G1-b：按来电号码盲索引等值查询（caller_number 已存 SM4-GCM 密文，明文不可直接查） */
    public AiCallerProfile selectAiCallerProfileByCallerNumberIndex(@Param("callerNumberIndex") String callerNumberIndex);

    public List<AiCallerProfile> selectAiCallerProfileList(AiCallerProfile aiCallerProfile);

    public int insertAiCallerProfile(AiCallerProfile aiCallerProfile);

    public int updateAiCallerProfile(AiCallerProfile aiCallerProfile);

    /** F1/F2：更新语种偏好与适老关怀模式 */
    public int updatePreference(@Param("profileId") Long profileId,
                                @Param("languagePreference") String languagePreference,
                                @Param("careMode") Integer careMode);

    public int deleteAiCallerProfileByProfileId(Long profileId);

    public int deleteAiCallerProfileByProfileIds(Long[] profileIds);

    /**
     * G1-b2 迁移专用：读取原始（不经解密拦截器）号码/身份证密文与盲索引。
     * resultType=map，拦截器仅处理域对象，故返回值保留库中原始形态。
     */
    public List<java.util.Map<String, Object>> selectRawForMigrate();
}
