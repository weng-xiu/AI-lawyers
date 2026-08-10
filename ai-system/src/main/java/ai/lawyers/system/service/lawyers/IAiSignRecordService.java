package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiSignRecord;

/**
 * 签署记录Service接口
 * 
 * @author ai-lawyers
 * @date 2026-08-10
 */
public interface IAiSignRecordService 
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
     * 批量删除签署记录
     * 
     * @param signIds 需要删除的签署记录主键集合
     * @return 结果
     */
    public int deleteAiSignRecordBySignIds(Long[] signIds);

    /**
     * 删除签署记录信息
     * 
     * @param signId 签署记录主键
     * @return 结果
     */
    public int deleteAiSignRecordBySignId(Long signId);
}
