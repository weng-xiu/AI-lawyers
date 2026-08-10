package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiPaidLegalService;

/**
 * 有偿法律服务 服务接口
 * 
 * @author AI Lawyers
 */
public interface IAiPaidLegalService 
{
    /**
     * 查询有偿法律服务
     * 
     * @param paidId 有偿法律服务主键
     * @return 有偿法律服务
     */
    public AiPaidLegalService selectAiPaidLegalServiceByPaidId(Long paidId);

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
     * 批量删除有偿法律服务
     * 
     * @param paidIds 需要删除的有偿法律服务主键集合
     * @return 结果
     */
    public int deleteAiPaidLegalServiceByPaidIds(Long[] paidIds);

    /**
     * 删除有偿法律服务信息
     * 
     * @param paidId 有偿法律服务主键
     * @return 结果
     */
    public int deleteAiPaidLegalServiceByPaidId(Long paidId);
}
