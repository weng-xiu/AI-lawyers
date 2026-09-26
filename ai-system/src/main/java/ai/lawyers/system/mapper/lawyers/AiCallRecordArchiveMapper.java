package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.AiCallRecord;

/**
 * 话单归档表（ai_call_record_archive，P3-F1 冷热分离）只读 Mapper。
 *
 * <p>归档表结构由 CREATE TABLE ... LIKE ai_call_record 生成并按月 RANGE 分区，
 * 仅提供查询能力；写入由 {@code DataArchiveTask} 批量迁移，删除仅限 DBA
 * DROP PARTITION 操作，应用侧不提供任何写接口。</p>
 *
 * @author ai-lawyers
 */
public interface AiCallRecordArchiveMapper
{
    /** 分页查询归档话单（号码模糊/状态/坐席/来电时间区间） */
    public List<AiCallRecord> selectArchiveList(AiCallRecord query);

    /** 按话单ID查归档详情 */
    public AiCallRecord selectArchiveByRecordId(@Param("recordId") Long recordId);

    /** 归档总量（冷数据规模观测） */
    public long countArchive();
}
