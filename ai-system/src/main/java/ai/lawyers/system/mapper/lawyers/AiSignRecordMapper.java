package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiSignRecord;

/**
 * 签署记录Mapper接口
 * 
 * @author ai-lawyers
 * @date 2026-08-10
 */
public interface AiSignRecordMapper 
{
    /**
     * 查询签署记录
     * 
     * @param signId 签署记录主键
     * @return 签署记录
     */
    public AiSignRecord selectAiSignRecordBySignId(Long signId);

    /**
     * 查询签署记录列表
     * 
     * @param aiSignRecord 签署记录
     * @return 签署记录集合
     */
    public List<AiSignRecord> selectAiSignRecordList(AiSignRecord aiSignRecord);

    /**
     * 新增签署记录
     * 
     * @param aiSignRecord 签署记录
     * @return 结果
     */
    public int insertAiSignRecord(AiSignRecord aiSignRecord);

    /**
     * 修改签署记录
     * 
     * @param aiSignRecord 签署记录
     * @return 结果
     */
    public int updateAiSignRecord(AiSignRecord aiSignRecord);

    /**
     * 删除签署记录
     * 
     * @param signId 签署记录主键
     * @return 结果
     */
    public int deleteAiSignRecordBySignId(Long signId);

    /**
     * 批量删除签署记录
     * 
     * @param signIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteAiSignRecordBySignIds(Long[] signIds);
}
