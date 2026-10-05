package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.AiCallRecord;

/**
 * 话单归档表（ai_call_record_archive，P3-F1 冷热分离）查询 Mapper。
 *
 * <p>归档表结构由 CREATE TABLE ... LIKE ai_call_record 生成并按月 RANGE 分区，
 * 常规业务仅查询；行迁移由 {@code DataArchiveTask} 批量执行，删除仅限 DBA
 * DROP PARTITION 操作。{@link #updateArchiveEncryption} 为 G1-b2 存量 PII
 * 加密迁移专用方法，不用于日常业务。</p>
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

    /**
     * G1-b2：归档话单存量 PII 加密迁移专用更新（动态 SET，仅更新非空参数列）。
     */
    public int updateArchiveEncryption(@Param("recordId") Long recordId,
                                       @Param("callerNumber") String callerNumber,
                                       @Param("callerNumberIndex") String callerNumberIndex);

    /**
     * G1-b2 迁移专用：读取原始（不经解密拦截器）号码密文与盲索引。
     * resultType=map，拦截器仅处理域对象，故返回值保留库中原始形态。
     */
    public List<java.util.Map<String, Object>> selectRawForMigrate();
}
