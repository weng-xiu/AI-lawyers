package ai.lawyers.system.service.lawyers.skill;

import java.util.List;
import ai.lawyers.system.domain.lawyers.skill.AiCallQueue;
import ai.lawyers.system.domain.lawyers.skill.DispatchContext;
import ai.lawyers.system.domain.lawyers.skill.DispatchResult;

/**
 * 坐席智能分配（ACD）Service
 *
 * @author ai-lawyers
 */
public interface IAgentDispatchService
{
    /**
     * 按技能组分配坐席
     *
     * @param groupId 目标技能组ID
     * @param ctx     分配上下文
     * @return 分配结果（成功含坐席，失败含排队信息）
     */
    DispatchResult dispatch(Long groupId, DispatchContext ctx);

    /**
     * 按咨询分类分配（内部查 category_id 对应的技能组，承接 B1 agentCategoryId）
     */
    DispatchResult dispatchByCategory(Long categoryId, DispatchContext ctx);

    /**
     * 手动分配排队中的通话给指定坐席
     */
    DispatchResult assignManually(Long queueId, Long agentId);

    /**
     * 查询当前排队列表
     */
    List<AiCallQueue> listQueuing(Long groupId);

    /**
     * 踢除某条排队记录（用户放弃/管理员踢除）
     */
    int kickQueue(Long queueId, String reason);

    /**
     * 坐席通话结束后回调：尝试把该坐席分配给队列中最早等待者（驱动排队流转）
     *
     * @return 被唤醒分配的排队记录，无则 null
     */
    DispatchResult tryDispatchNextForAgent(Long agentId);
}
