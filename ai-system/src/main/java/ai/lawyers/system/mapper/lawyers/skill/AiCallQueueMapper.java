package ai.lawyers.system.mapper.lawyers.skill;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.skill.AiCallQueue;

/**
 * 排队/分配流水Mapper接口
 *
 * @author ai-lawyers
 */
public interface AiCallQueueMapper
{
    public AiCallQueue selectAiCallQueueByQueueId(Long queueId);

    public List<AiCallQueue> selectAiCallQueueList(AiCallQueue aiCallQueue);

    /**
     * 查询某技能组当前排队中（queue_status=0）的记录，按优先级、入队时间排序
     */
    public List<AiCallQueue> selectQueuingByGroupId(Long groupId);

    /**
     * 统计某技能组当前排队人数
     */
    public int countQueuingByGroupId(Long groupId);

    /**
     * 查询某会话当前排队记录（用于重入/查询排队位置）
     */
    public AiCallQueue selectQueuingBySessionId(String sessionId);

    /**
     * 查询某条记录前面还有多少排队者（排队位置-1）
     */
    public int countBefore(@Param("groupId") Long groupId, @Param("enqueueTime") Date enqueueTime,
                           @Param("priority") Integer priority);

    public int insertAiCallQueue(AiCallQueue aiCallQueue);

    public int updateAiCallQueue(AiCallQueue aiCallQueue);

    /**
     * 将超时仍在排队的记录置为指定状态（2超时溢出 / 4无可用），返回受影响行数
     */
    public int expireQueued(@Param("groupId") Long groupId, @Param("beforeTime") Date beforeTime,
                            @Param("status") String status);

    public int deleteAiCallQueueByQueueIds(Long[] queueIds);
}
