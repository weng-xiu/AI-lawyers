package ai.lawyers.system.service.impl.lawyers.schedule;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.schedule.AiAgentSchedule;
import ai.lawyers.system.domain.lawyers.schedule.AiShiftChangeRequest;
import ai.lawyers.system.domain.lawyers.schedule.AiShiftChangeRequestVO;
import ai.lawyers.system.mapper.lawyers.schedule.AiAgentScheduleMapper;
import ai.lawyers.system.mapper.lawyers.schedule.AiShiftChangeRequestMapper;
import ai.lawyers.system.service.lawyers.schedule.IAiShiftChangeService;

/**
 * 坐席换班申请与审批 Service 实现（F9）
 *
 * <p>设计要点：</p>
 * <ol>
 *   <li>申请阶段只做校验与落单，<b>不改排班</b>，避免审批前的误操作既成事实；</li>
 *   <li>审批通过时在同一事务内先改写 {@code ai_agent_schedule}、再做状态 CAS 流转
 *       （{@code where status='0'}），CAS 失败则抛出异常整体回滚，杜绝并发双批；</li>
 *   <li>互换要求双方排班同一天（同一坐席当日唯一排班的约束下只能换 shift_id），
 *       转班要求接收坐席当日无有效排班（否则撞 {@code uk_agent_date}）。</li>
 * </ol>
 */
@Service
public class AiShiftChangeServiceImpl implements IAiShiftChangeService
{
    private static final Logger log = LoggerFactory.getLogger(AiShiftChangeServiceImpl.class);

    /** 申请原因最大长度（与表字段一致） */
    private static final int MAX_REASON_LENGTH = 500;

    /** 审批意见最大长度 */
    private static final int MAX_REMARK_LENGTH = 500;

    @Autowired
    private AiShiftChangeRequestMapper shiftChangeRequestMapper;

    @Autowired
    private AiAgentScheduleMapper agentScheduleMapper;

    @Override
    public List<AiShiftChangeRequestVO> selectShiftChangeList(AiShiftChangeRequest query)
    {
        return shiftChangeRequestMapper.selectShiftChangeRequestList(query == null ? new AiShiftChangeRequest() : query);
    }

    @Override
    public List<AiShiftChangeRequestVO> selectMyShiftChangeList(Long applicantAgentId, AiShiftChangeRequest query)
    {
        AiShiftChangeRequest filter = query == null ? new AiShiftChangeRequest() : query;
        if (applicantAgentId == null)
        {
            // 未绑定坐席的账号看不到任何人的申请，避免退化成全量列表（越权）
            return new java.util.ArrayList<>();
        }
        filter.setApplicantAgentId(applicantAgentId);
        return shiftChangeRequestMapper.selectShiftChangeRequestList(filter);
    }

    @Override
    public AiShiftChangeRequestVO selectShiftChangeById(Long requestId)
    {
        if (requestId == null)
        {
            return null;
        }
        return shiftChangeRequestMapper.selectShiftChangeRequestById(requestId);
    }

    @Override
    public int applyShiftChange(AiShiftChangeRequest request)
    {
        if (request == null)
        {
            throw new ServiceException("换班申请不能为空");
        }
        Long applicantAgentId = request.getApplicantAgentId();
        if (applicantAgentId == null)
        {
            throw new ServiceException("当前账号未绑定坐席，无法申请换班");
        }
        Long applicantScheduleId = request.getApplicantScheduleId();
        if (applicantScheduleId == null)
        {
            throw new ServiceException("请选择本人待变更的排班");
        }
        AiAgentSchedule mine = agentScheduleMapper.selectAiAgentScheduleByScheduleId(applicantScheduleId);
        if (mine == null)
        {
            throw new ServiceException("排班不存在或已被删除");
        }
        if (mine.getAgentId() == null || !mine.getAgentId().equals(applicantAgentId))
        {
            throw new ServiceException("只能申请本人的排班换班");
        }
        if (!Integer.valueOf(1).equals(mine.getStatus()))
        {
            throw new ServiceException("该排班已取消，无法申请换班");
        }
        if (!isTodayOrFuture(mine.getScheduleDate()))
        {
            throw new ServiceException("仅可申请今天及以后的排班换班");
        }
        if (shiftChangeRequestMapper.countPendingByApplicantSchedule(applicantScheduleId) > 0)
        {
            throw new ServiceException("该排班已有待审批的换班申请，请等待处理");
        }
        if (StringUtils.isNotEmpty(request.getReason()) && request.getReason().length() > MAX_REASON_LENGTH)
        {
            throw new ServiceException("申请原因不能超过 " + MAX_REASON_LENGTH + " 字");
        }

        String changeType = normalizeChangeType(request.getChangeType());
        AiAgentSchedule target = null;
        if (AiShiftChangeRequest.TYPE_SWAP.equals(changeType))
        {
            target = validateSwap(request, mine, applicantAgentId);
            request.setTargetScheduleId(target.getScheduleId());
            request.setTargetAgentId(target.getAgentId());
        }
        else
        {
            validateTransfer(request, mine, applicantAgentId);
            // 转班不涉及对方排班行
            request.setTargetScheduleId(null);
        }

        request.setChangeType(changeType);
        request.setApplicantAgentId(applicantAgentId);
        request.setStatus(AiShiftChangeRequest.STATUS_PENDING);
        if (StringUtils.isEmpty(request.getCreateBy()))
        {
            request.setCreateBy(currentUsername());
        }
        int rows = shiftChangeRequestMapper.insertShiftChangeRequest(request);
        if (rows > 0)
        {
            log.info("换班申请提交 requestId={} type={} applicantAgentId={} scheduleId={} targetAgentId={}",
                    request.getRequestId(), changeType, applicantAgentId, applicantScheduleId, request.getTargetAgentId());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int approveShiftChange(Long requestId, String approver, String approveRemark)
    {
        AiShiftChangeRequestVO request = requirePending(requestId);
        AiAgentSchedule mine = agentScheduleMapper.selectAiAgentScheduleByScheduleId(request.getApplicantScheduleId());
        if (mine == null || !Integer.valueOf(1).equals(mine.getStatus()))
        {
            throw new ServiceException("申请人排班已失效，无法通过审批");
        }
        String operator = StringUtils.isNotEmpty(approver) ? approver : currentUsername();
        if (AiShiftChangeRequest.TYPE_SWAP.equals(request.getChangeType()))
        {
            AiAgentSchedule target = request.getTargetScheduleId() == null ? null
                    : agentScheduleMapper.selectAiAgentScheduleByScheduleId(request.getTargetScheduleId());
            if (target == null || !Integer.valueOf(1).equals(target.getStatus()))
            {
                throw new ServiceException("对方排班已失效，无法通过审批");
            }
            if (!sameDate(mine, target))
            {
                throw new ServiceException("双方排班日期不一致，无法互换换班");
            }
            // 互换：各自换成对方的班次，排班行与坐席不变
            agentScheduleMapper.updateShiftAssignment(mine.getScheduleId(), target.getShiftId(),
                    mine.getScheduleType(), operator);
            agentScheduleMapper.updateShiftAssignment(target.getScheduleId(), mine.getShiftId(),
                    target.getScheduleType(), operator);
        }
        else
        {
            Long targetAgentId = request.getTargetAgentId();
            if (targetAgentId == null)
            {
                throw new ServiceException("换班申请缺少接收坐席，无法通过审批");
            }
            AiAgentSchedule exist = agentScheduleMapper.selectByAgentAndDate(targetAgentId, mine.getScheduleDate());
            if (exist != null)
            {
                throw new ServiceException("接收坐席当日已有排班，无法转班");
            }
            agentScheduleMapper.transferScheduleToAgent(mine.getScheduleId(), targetAgentId, operator);
        }
        int rows = shiftChangeRequestMapper.updateApproval(requestId, AiShiftChangeRequest.STATUS_APPROVED,
                operator, truncateRemark(approveRemark), operator);
        if (rows <= 0)
        {
            // CAS 失败：已被他人处理，抛异常回滚上面的排班改写
            throw new ServiceException("该申请已被处理，请刷新后重试");
        }
        log.info("换班审批通过 requestId={} type={} approver={}", requestId, request.getChangeType(), operator);
        return rows;
    }

    @Override
    public int rejectShiftChange(Long requestId, String approver, String approveRemark)
    {
        requirePending(requestId);
        String operator = StringUtils.isNotEmpty(approver) ? approver : currentUsername();
        int rows = shiftChangeRequestMapper.updateApproval(requestId, AiShiftChangeRequest.STATUS_REJECTED,
                operator, truncateRemark(approveRemark), operator);
        if (rows <= 0)
        {
            throw new ServiceException("该申请已被处理，请刷新后重试");
        }
        log.info("换班审批驳回 requestId={} approver={}", requestId, operator);
        return rows;
    }

    @Override
    public int cancelShiftChange(Long requestId, Long applicantAgentId, String operator)
    {
        AiShiftChangeRequestVO request = requirePending(requestId);
        if (applicantAgentId == null || request.getApplicantAgentId() == null
                || !applicantAgentId.equals(request.getApplicantAgentId()))
        {
            throw new ServiceException("只能撤销本人的换班申请");
        }
        String who = StringUtils.isNotEmpty(operator) ? operator : currentUsername();
        int rows = shiftChangeRequestMapper.updateApproval(requestId, AiShiftChangeRequest.STATUS_CANCELLED,
                who, "申请人撤销", who);
        if (rows <= 0)
        {
            throw new ServiceException("该申请已被处理，请刷新后重试");
        }
        return rows;
    }

    /** 互换校验：对方排班须存在、有效、非本人、同一天，返回对方排班 */
    private AiAgentSchedule validateSwap(AiShiftChangeRequest request, AiAgentSchedule mine, Long applicantAgentId)
    {
        Long targetScheduleId = request.getTargetScheduleId();
        if (targetScheduleId == null)
        {
            throw new ServiceException("互换换班需指定对方排班");
        }
        AiAgentSchedule target = agentScheduleMapper.selectAiAgentScheduleByScheduleId(targetScheduleId);
        if (target == null)
        {
            throw new ServiceException("对方排班不存在或已被删除");
        }
        if (target.getAgentId() == null || target.getAgentId().equals(applicantAgentId))
        {
            throw new ServiceException("不能与自己换班");
        }
        if (!Integer.valueOf(1).equals(target.getStatus()))
        {
            throw new ServiceException("对方排班已取消，无法互换");
        }
        if (!sameDate(mine, target))
        {
            throw new ServiceException("互换换班需选择同一天的对方排班");
        }
        return target;
    }

    /** 转班校验：接收坐席非本人且当日无排班 */
    private void validateTransfer(AiShiftChangeRequest request, AiAgentSchedule mine, Long applicantAgentId)
    {
        Long targetAgentId = request.getTargetAgentId();
        if (targetAgentId == null)
        {
            throw new ServiceException("转班需指定接收坐席");
        }
        if (targetAgentId.equals(applicantAgentId))
        {
            throw new ServiceException("不能把班次转给自己");
        }
        AiAgentSchedule exist = agentScheduleMapper.selectByAgentAndDate(targetAgentId, mine.getScheduleDate());
        if (exist != null)
        {
            throw new ServiceException("接收坐席当日已有排班，无法转班");
        }
    }

    /** 取待审批申请；不存在或已处理时抛出业务异常 */
    private AiShiftChangeRequestVO requirePending(Long requestId)
    {
        if (requestId == null)
        {
            throw new ServiceException("换班申请ID不能为空");
        }
        AiShiftChangeRequestVO request = shiftChangeRequestMapper.selectShiftChangeRequestById(requestId);
        if (request == null)
        {
            throw new ServiceException("换班申请不存在");
        }
        if (!AiShiftChangeRequest.STATUS_PENDING.equals(request.getStatus()))
        {
            throw new ServiceException("该申请已处理，请勿重复操作");
        }
        return request;
    }

    /** 变更类型归一：仅支持 SWAP/TRANSFER，大小写与空白容错 */
    private String normalizeChangeType(String changeType)
    {
        String type = StringUtils.isEmpty(changeType) ? AiShiftChangeRequest.TYPE_SWAP : changeType.trim().toUpperCase();
        if (!AiShiftChangeRequest.TYPE_SWAP.equals(type) && !AiShiftChangeRequest.TYPE_TRANSFER.equals(type))
        {
            throw new ServiceException("换班类型非法（SWAP 互换 / TRANSFER 转班）");
        }
        return type;
    }

    /** 排班日期须为今天或未来（yyyy-MM-dd 字典序即可比较） */
    private boolean isTodayOrFuture(String scheduleDate)
    {
        if (StringUtils.isEmpty(scheduleDate))
        {
            return false;
        }
        return scheduleDate.compareTo(DateUtils.getDate()) >= 0;
    }

    private boolean sameDate(AiAgentSchedule left, AiAgentSchedule right)
    {
        return left.getScheduleDate() != null && left.getScheduleDate().equals(right.getScheduleDate());
    }

    private String truncateRemark(String remark)
    {
        if (remark == null || remark.length() <= MAX_REMARK_LENGTH)
        {
            return remark;
        }
        return remark.substring(0, MAX_REMARK_LENGTH);
    }

    /** 当前登录用户名；定时任务等无登录上下文场景返回 null（不阻断主流程） */
    private String currentUsername()
    {
        try
        {
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            log.debug("当前上下文无登录用户，换班申请操作人不记录：{}", e.getMessage());
            return null;
        }
    }
}
