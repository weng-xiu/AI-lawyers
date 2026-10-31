package ai.lawyers.system.service.lawyers.schedule;

import java.util.List;
import ai.lawyers.system.domain.lawyers.schedule.AiShiftChangeRequest;
import ai.lawyers.system.domain.lawyers.schedule.AiShiftChangeRequestVO;

/**
 * 坐席换班申请与审批 Service（F9）
 *
 * <p>状态口径：0 待审批 → 1 已通过 / 2 已驳回 / 3 已撤销。
 * 审批通过时才改写 {@code ai_agent_schedule}，且与状态流转同事务。</p>
 */
public interface IAiShiftChangeService
{
    /** 申请列表（班组长视角，可按申请人/接收坐席/状态/类型/时间过滤） */
    public List<AiShiftChangeRequestVO> selectShiftChangeList(AiShiftChangeRequest query);

    /** 我的申请（坐席视角） */
    public List<AiShiftChangeRequestVO> selectMyShiftChangeList(Long applicantAgentId, AiShiftChangeRequest query);

    /** 按ID查询（含班次/坐席展示字段） */
    public AiShiftChangeRequestVO selectShiftChangeById(Long requestId);

    /**
     * 提交换班/转班申请。
     *
     * @param request 申请（applicantAgentId 由调用方按当前登录账号解析后传入）
     * @return 新增行数
     */
    public int applyShiftChange(AiShiftChangeRequest request);

    /**
     * 审批通过并自动改写排班（互换换班次 / 转班换坐席），与状态流转同事务。
     *
     * @return 影响行数（0 表示已被他人处理，异常场景已抛 ServiceException）
     */
    public int approveShiftChange(Long requestId, String approver, String approveRemark);

    /** 审批驳回 */
    public int rejectShiftChange(Long requestId, String approver, String approveRemark);

    /** 撤销（仅申请人本人、仅待审批） */
    public int cancelShiftChange(Long requestId, Long applicantAgentId, String operator);
}
