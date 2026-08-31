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

    /**
     * T1-7 发送结果回写（幂等）：仅当日志仍为待发(0)时更新为成功/失败并写回供应商 msgId，
     * 影响行数=0 表示已被回执/对账处理过，调用方据此跳过，防止重复回调重复处理。
     */
    public int updateSendResult(@org.apache.ibatis.annotations.Param("logId") Long logId,
                                @org.apache.ibatis.annotations.Param("sendStatus") String sendStatus,
                                @org.apache.ibatis.annotations.Param("providerMsgId") String providerMsgId,
                                @org.apache.ibatis.annotations.Param("failReason") String failReason);
}
