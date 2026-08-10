package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.system.domain.lawyers.AiPaidLegalService;
import ai.lawyers.system.mapper.lawyers.AiPaidLegalServiceMapper;
import ai.lawyers.system.service.lawyers.IAiPaidLegalService;

/**
 * 有偿法律服务 服务层实现
 * 
 * @author AI Lawyers
 */
@Service
public class AiPaidLegalServiceImpl implements IAiPaidLegalService 
{
    @Autowired
    private AiPaidLegalServiceMapper aiPaidLegalServiceMapper;

    /**
     * 查询有偿法律服务
     * 
     * @param paidId 有偿法律服务主键
     * @return 有偿法律服务
     */
    @Override
    public AiPaidLegalService selectAiPaidLegalServiceByPaidId(Long paidId)
    {
        return aiPaidLegalServiceMapper.selectAiPaidLegalServiceById(paidId);
    }

    /**
     * 查询有偿法律服务列表
     * 
     * @param aiPaidLegalService 有偿法律服务
     * @return 有偿法律服务
     */
    @Override
    public List<AiPaidLegalService> selectAiPaidLegalServiceList(AiPaidLegalService aiPaidLegalService)
    {
        return aiPaidLegalServiceMapper.selectAiPaidLegalServiceList(aiPaidLegalService);
    }

    /**
     * 新增有偿法律服务
     * 
     * @param aiPaidLegalService 有偿法律服务
     * @return 结果
     */
    @Override
    public int insertAiPaidLegalService(AiPaidLegalService aiPaidLegalService)
    {
        aiPaidLegalService.setCreateTime(DateUtils.getNowDate());
        return aiPaidLegalServiceMapper.insertAiPaidLegalService(aiPaidLegalService);
    }

    /**
     * 修改有偿法律服务
     * 
     * @param aiPaidLegalService 有偿法律服务
     * @return 结果
     */
    @Override
    public int updateAiPaidLegalService(AiPaidLegalService aiPaidLegalService)
    {
        aiPaidLegalService.setUpdateTime(DateUtils.getNowDate());
        return aiPaidLegalServiceMapper.updateAiPaidLegalService(aiPaidLegalService);
    }

    /**
     * 批量删除有偿法律服务
     * 
     * @param paidIds 需要删除的有偿法律服务主键
     * @return 结果
     */
    @Override
    public int deleteAiPaidLegalServiceByPaidIds(Long[] paidIds)
    {
        return aiPaidLegalServiceMapper.deleteAiPaidLegalServiceByIds(paidIds);
    }

    /**
     * 删除有偿法律服务信息
     * 
     * @param paidId 有偿法律服务主键
     * @return 结果
     */
    @Override
    public int deleteAiPaidLegalServiceByPaidId(Long paidId)
    {
        return aiPaidLegalServiceMapper.deleteAiPaidLegalServiceById(paidId);
    }
}
