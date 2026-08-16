package ai.lawyers.system.mapper.lawyers.sms;

import java.util.List;
import ai.lawyers.system.domain.lawyers.sms.AiSmsConfig;

/**
 * 短信通道配置Mapper接口
 *
 * @author ai-lawyers
 */
public interface AiSmsConfigMapper
{
    public AiSmsConfig selectAiSmsConfigByConfigId(Long configId);

    public List<AiSmsConfig> selectAiSmsConfigList(AiSmsConfig aiSmsConfig);

    public int insertAiSmsConfig(AiSmsConfig aiSmsConfig);

    public int updateAiSmsConfig(AiSmsConfig aiSmsConfig);

    public int deleteAiSmsConfigByConfigIds(Long[] configIds);
}
