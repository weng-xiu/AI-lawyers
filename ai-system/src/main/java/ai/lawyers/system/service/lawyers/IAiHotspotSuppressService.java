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
}
