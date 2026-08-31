package ai.lawyers.system.domain.lawyers.quality;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 坐席状态变更流水对象 ai_agent_status_log（T4-3）
 *
 * <p>坐席在线状态（0离线/1在线/2忙碌/3休息）与通话状态（0空闲/1通话/2保持/3咨询/
 * 4三方/5话后）每次切换经 status-log 队列异步落库一条流水；下一条流水落库时回填
 * 上一条 duration，供坐席效能与 SLA 统计。</p>
 *
 * @author ai-lawyers
 */
public class AiAgentStatusLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 流水ID */
    private Long logId;

    /** 坐席ID */
    private Long agentId;

    /** 绑定用户ID */
    private Long userId;

    /** 事件类型 LOGIN/LOGOUT/STATUS/MAKE_CALL/HANGUP/AFTER_WORK/HOLD/RESUME/TRANSFER/CONSULT/THREE_WAY/ROBOT_TAKEOVER/IVR_TRANSFER/ACD_ASSIGN */
    private String eventType;

    /** 原在线状态 0离线 1在线 2忙碌 3休息 */
    private String fromStatus;

    /** 新在线状态 */
    private String toStatus;

    /** 原通话状态 0空闲 1通话 2保持 3咨询 4三方 5话后 */
    private String fromCallStatus;

    /** 新通话状态 */
    private String toCallStatus;

    /** 上一状态持续秒数（下一切换时回填） */
    private Integer duration;

    /** 关联通话记录ID（通话类事件） */
    private Long recordId;

    /** 状态切换时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date logTime;

    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }

    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }

    public String getFromCallStatus() { return fromCallStatus; }
    public void setFromCallStatus(String fromCallStatus) { this.fromCallStatus = fromCallStatus; }

    public String getToCallStatus() { return toCallStatus; }
    public void setToCallStatus(String toCallStatus) { this.toCallStatus = toCallStatus; }

    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }

    public Date getLogTime() { return logTime; }
    public void setLogTime(Date logTime) { this.logTime = logTime; }
}
