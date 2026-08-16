package ai.lawyers.system.mapper.lawyers.sms;

import java.util.List;
import ai.lawyers.system.domain.lawyers.sms.AiSmsLog;

/**
 * 短信发送记录Mapper接口
 *
 * @author ai-lawyers
 */
public interface AiSmsLogMapper
{
    public AiSmsLog selectAiSmsLogByLogId(Long logId);

    public List<AiSmsLog> selectAiSmsLogList(AiSmsLog aiSmsLog);

    public int insertAiSmsLog(AiSmsLog aiSmsLog);

    public int deleteAiSmsLogByLogIds(Long[] logIds);

    /** 统计某号码当日成功发送次数（用于日限流） */
    public int selectTodayCountByPhone(String phone);
}
