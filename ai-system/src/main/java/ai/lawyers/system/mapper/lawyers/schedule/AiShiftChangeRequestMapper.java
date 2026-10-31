package ai.lawyers.system.mapper.lawyers.schedule;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.schedule.AiShiftChangeRequest;
import ai.lawyers.system.domain.lawyers.schedule.AiShiftChangeRequestVO;

/**
 * 换班申请与审批 Mapper（F9）
 */
public interface AiShiftChangeRequestMapper
{
    /** 按ID查申请（含申请人/接收坐席与班次展示字段） */
    public AiShiftChangeRequestVO selectShiftChangeRequestById(Long requestId);

    /** 列表查询：按申请人/接收坐席/状态/类型/创建时间过滤 */
    public List<AiShiftChangeRequestVO> selectShiftChangeRequestList(AiShiftChangeRequest query);

    /** 同一排班是否已有待审批申请（防重复提交） */
    public int countPendingByApplicantSchedule(@Param("applicantScheduleId") Long applicantScheduleId);

    /** 新增申请（状态由服务层统一置为待审批） */
    public int insertShiftChangeRequest(AiShiftChangeRequest request);

    /**
     * 状态流转（CAS）：仅当仍为待审批时更新，返回 0 表示已被他人处理，
     * 供服务层在事务内回滚已做的排班改写。
     */
    public int updateApproval(@Param("requestId") Long requestId,
                              @Param("status") String status,
                              @Param("approver") String approver,
                              @Param("approveRemark") String approveRemark,
                              @Param("updateBy") String updateBy);
}
