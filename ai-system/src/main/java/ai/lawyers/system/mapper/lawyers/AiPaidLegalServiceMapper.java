package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiPaidLegalService;

/**
 * 有偿法律服务 数据层
 * 
 * @author AI Lawyers
 */
public interface AiPaidLegalServiceMapper
{
    /**
     * 查询有偿法律服务
     * 
     * @param paidId 有偿法律服务ID
     * @return 有偿法律服务
     */
    public AiPaidLegalService selectAiPaidLegalServiceById(Long paidId);

    /**
     * 查询有偿法律服务列表
     * 
     * @param aiPaidLegalService 有偿法律服务
     * @return 有偿法律服务集合
     */
    public List<AiPaidLegalService> selectAiPaidLegalServiceList(AiPaidLegalService aiPaidLegalService);

    /**
     * 新增有偿法律服务
     * 
     * @param aiPaidLegalService 有偿法律服务
     * @return 结果
     */
    public int insertAiPaidLegalService(AiPaidLegalService aiPaidLegalService);

    /**
     * 修改有偿法律服务
     * 
     * @param aiPaidLegalService 有偿法律服务
     * @return 结果
     */
    public int updateAiPaidLegalService(AiPaidLegalService aiPaidLegalService);

    /**
     * 删除有偿法律服务
     * 
     * @param paidId 有偿法律服务ID
     * @return 结果
     */
    public int deleteAiPaidLegalServiceById(Long paidId);

    /**
     * 批量删除有偿法律服务
     * 
     * @param paidIds 需要删除的数据ID
     * @return 结果
     */
    public int deleteAiPaidLegalServiceByIds(Long[] paidIds);
}
