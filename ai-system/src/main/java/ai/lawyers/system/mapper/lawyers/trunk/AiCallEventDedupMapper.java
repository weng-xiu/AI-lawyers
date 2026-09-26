package ai.lawyers.system.mapper.lawyers.trunk;

import org.apache.ibatis.annotations.Param;

/**
 * P3-B4：PBX 事件幂等去重表 Mapper
 *
 * <p>仅提供 INSERT IGNORE 判重写入：依赖 dedup_key 主键，重复写入返回 0。</p>
 */
public interface AiCallEventDedupMapper
{
    /**
     * 首次写入返回 1；dedup_key 已存在返回 0（重复事件）。
     *
     * @param dedupKey  source|eventKey|eventName
     * @param source    事件来源（如 ESL:host:port）
     * @param eventKey  事件对象键（通道 UUID）
     * @param eventName 规范化事件名
     */
    public int insertIgnore(@Param("dedupKey") String dedupKey,
                            @Param("source") String source,
                            @Param("eventKey") String eventKey,
                            @Param("eventName") String eventName);
}
