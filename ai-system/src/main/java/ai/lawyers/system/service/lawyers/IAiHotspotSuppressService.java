package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiHotspotSuppress;
import ai.lawyers.system.domain.lawyers.AiHotspotSuppressLog;

/**
 * 高频置底 Service
 *
 * @author ai-lawyers
 */
public interface IAiHotspotSuppressService
{
    /** 规则列表 */
    List<AiHotspotSuppress> selectSuppressList(AiHotspotSuppress query);

    /** 规则详情 */
    AiHotspotSuppress selectSuppressById(Long suppressId);

    /** 新增规则 */
    int insertSuppress(AiHotspotSuppress suppress);

    /** 修改规则 */
    int updateSuppress(AiHotspotSuppress suppress);

    /** 删除规则 */
    int deleteSuppressByIds(Long[] suppressIds);

    /** 命中日志列表 */
    List<AiHotspotSuppressLog> selectLogList(AiHotspotSuppressLog query);

    /**
     * 入站来电匹配判定：按主叫号码与（可选）关键词命中生效规则。
     *
     * @param callerNumber 主叫号码
     * @param content      来电文本/关键词上下文（IVR 识别内容，无则传 null）
     * @param channelUuid  通道UUID/话单关联键
     * @param calleeNumber 被叫号码
     * @return 命中的规则；未命中返回 null
     */
    AiHotspotSuppress matchInbound(String callerNumber, String content, String channelUuid, String calleeNumber);

    /**
     * 外呼号码匹配判定：拨号前按被叫号码命中生效规则（PHONE 规则）。
     * 命中 REJECT 由调用方跳过拨号；命中 PRIORITY 由调用方抬高外呼队列优先级数值沉底
     * （外呼内存队列为 priority 升序，越小越优先，与入站 ACD desc 方向相反，不可复用负数）。
     *
     * @param calleeNumber 外呼被叫号码
     * @param taskId       外呼任务ID（用于日志关联）
     * @return 命中的规则；未命中返回 null
     */
    AiHotspotSuppress matchOutbound(String calleeNumber, Long taskId);

    /**
     * IVR 关键词匹配判定：ASR/意图识别文本产出后按 KEYWORD 规则命中判定。
     * 命中 REJECT 由引擎终止流程（挂断语义）；命中 PRIORITY 由引擎写入 dispatchPriority 变量，
     * 后续转人工节点自动降权沉底。
     *
     * @param content      识别文本
     * @param callerNumber 主叫号码（日志留痕用，可空）
     * @param sessionId    会话ID（日志关联键）
     * @param direction    话路方向（INBOUND/OUTBOUND），用于命中日志留痕
     * @return 命中的规则；未命中返回 null
     */
    AiHotspotSuppress matchKeyword(String content, String callerNumber, String sessionId, String direction);
}
