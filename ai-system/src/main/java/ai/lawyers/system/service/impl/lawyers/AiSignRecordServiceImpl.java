package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.mapper.lawyers.AiSignRecordMapper;
import ai.lawyers.system.domain.lawyers.AiSignRecord;
import ai.lawyers.system.service.lawyers.IAiSignRecordService;

/**
 * 签署记录Service业务层处理
 * 
 * @author ai-lawyers
 * @date 2026-08-10
 */
@Service
public class AiSignRecordServiceImpl implements IAiSignRecordService 
{
    @Autowired
    private AiSignRecordMapper aiSignRecordMapper;

    /**
     * 查询签署记录
     * 
     * @param signId 签署记录主键
     * @return 签署记录
     */
    @Override
    public AiSignRecord selectAiSignRecordBySignId(Long signId)
    {
        return aiSignRecordMapper.selectAiSignRecordBySignId(signId);
    }

    /**
     * 查询签署记录列表
     * 
     * @param aiSignRecord 签署记录
     * @return 签署记录
     */
    @Override
    public List<AiSignRecord> selectAiSignRecordList(AiSignRecord aiSignRecord)
    {
        return aiSignRecordMapper.selectAiSignRecordList(aiSignRecord);
    }

    /**
     * 新增签署记录
     * 
     * @param aiSignRecord 签署记录
     * @return 结果
     */
    @Override
    public int insertAiSignRecord(AiSignRecord aiSignRecord)
    {
        return aiSignRecordMapper.insertAiSignRecord(aiSignRecord);
    }

    /**
     * 修改签署记录
     * 
     * @param aiSignRecord 签署记录
     * @return 结果
     */
    @Override
    public int updateAiSignRecord(AiSignRecord aiSignRecord)
    {
        return aiSignRecordMapper.updateAiSignRecord(aiSignRecord);
    }

    /**
     * 批量删除签署记录
     * 
     * @param signIds 需要删除的签署记录主键
     * @return 结果
     */
    @Override
    public int deleteAiSignRecordBySignIds(Long[] signIds)
    {
        return aiSignRecordMapper.deleteAiSignRecordBySignIds(signIds);
    }

    /**
     * 删除签署记录信息
     * 
     * @param signId 签署记录主键
     * @return 结果
     */
    @Override
    public int deleteAiSignRecordBySignId(Long signId)
    {
        return aiSignRecordMapper.deleteAiSignRecordBySignId(signId);
    }
}
