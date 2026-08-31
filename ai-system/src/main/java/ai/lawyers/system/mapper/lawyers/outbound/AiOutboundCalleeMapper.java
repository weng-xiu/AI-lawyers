package ai.lawyers.system.mapper.lawyers.outbound;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundCallee;

public interface AiOutboundCalleeMapper
{
    public AiOutboundCallee selectAiOutboundCalleeByCalleeId(Long calleeId);

    public List<AiOutboundCallee> selectAiOutboundCalleeList(AiOutboundCallee aiOutboundCallee);

    public List<AiOutboundCallee> selectAiOutboundCalleeByTaskId(Long taskId);

    public List<AiOutboundCallee> selectPendingCallees(@Param("taskId") Long taskId, @Param("limit") int limit);

    public int insertAiOutboundCallee(AiOutboundCallee aiOutboundCallee);

    public int batchInsertCallees(List<AiOutboundCallee> callees);

    public int updateAiOutboundCallee(AiOutboundCallee aiOutboundCallee);

    public int updateCalleeStatus(AiOutboundCallee aiOutboundCallee);

    /**
     * 号码原子领取：仅当号码处于待呼叫(0)且到达下次重试时间时抢占置为呼叫中(1)。
     * 多实例/多线程并发扫描时，影响行数=1 表示领取成功，=0 表示已被其他实例领取或未到重试时间。
     */
    public int claimCallee(@Param("calleeId") Long calleeId, @Param("updateBy") String updateBy);

    /**
     * T2-2 待呼叫号码（含重试退避）：status=0 且（无重试时间 或 next_retry_time 已到期）。
     */
    public List<AiOutboundCallee> selectDialableCallees(@Param("taskId") Long taskId, @Param("limit") int limit);

    /**
     * T1-6 是否存在未终态号码（0待呼叫/1呼叫中/2已接通待挂机）；=0 即任务可判完成。单 SQL 快照判定。
     */
    public int countUnfinishedByTaskId(@Param("taskId") Long taskId);

    /**
     * T1-6 对账：长时间卡在"呼叫中(1)"（超过 staleMinutes）的号码列表，防止网关事件丢失导致永久卡住。
     */
    public List<AiOutboundCallee> selectStaleCallingCallees(@Param("staleMinutes") int staleMinutes);

    /**
     * T2-2 回拨时间窗口：设置下次可重试时间（退避），配合 selectDialableCallees 到期才捞取。
     */
    public int updateNextRetryTime(@Param("calleeId") Long calleeId,
                                   @Param("nextRetryTime") java.util.Date nextRetryTime,
                                   @Param("updateBy") String updateBy);

    public int deleteAiOutboundCalleeByCalleeId(Long calleeId);

    public int deleteAiOutboundCalleeByTaskId(Long taskId);

    public int deleteAiOutboundCalleeByCalleeIds(Long[] calleeIds);

    public int countByTaskIdAndStatus(@Param("taskId") Long taskId, @Param("callStatus") String callStatus);
}
