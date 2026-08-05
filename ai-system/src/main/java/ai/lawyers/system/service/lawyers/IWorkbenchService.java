package ai.lawyers.system.service.lawyers;

import java.util.List;
import java.util.Map;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiNotice;
import ai.lawyers.system.domain.lawyers.AiTodo;

/**
 * 统一工作台聚合服务
 */
public interface IWorkbenchService
{
    /** 工作台统计卡：今日通话/服务时长/满意度/在线时长 */
    public Map<String, Object> getWorkbenchStats(Long userId);

    /** 我的待办（按当前用户） */
    public List<AiTodo> getWorkbenchTodos(Long userId);

    /** 最近通话记录 */
    public List<AiCallRecord> getWorkbenchRecentCalls(Integer limit);

    /** 已发布公告 */
    public List<AiNotice> getWorkbenchNotices(Integer limit);
}
