package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiExternalOrg;
import ai.lawyers.system.domain.lawyers.AiRiskWarning;
import ai.lawyers.system.domain.lawyers.AiTicketTransfer;

public interface IAiRiskWarningService
{
    public AiRiskWarning selectAiRiskWarningByWarningId(Long warningId);

    public List<AiRiskWarning> selectAiRiskWarningList(AiRiskWarning aiRiskWarning);

    public int insertAiRiskWarning(AiRiskWarning aiRiskWarning);

    public int updateAiRiskWarning(AiRiskWarning aiRiskWarning);

    public int deleteAiRiskWarningByWarningIds(Long[] warningIds);

    /**
     * F3 风险联动：按建议条线查询启用状态的可转办机构（精简公开字段）。
     */
    public List<AiExternalOrg> selectTransferOrgs(String externalType);

    /**
     * F3 风险联动：一键确认转办。
     * <p>对存在转办建议的高风险预警，校验条线与机构后自动建工单（无工单时），
     * 复用 {@code IAiTicketTransferService.transferOut} 发起转出，并回写预警转办流水；
     * 已转办的预警幂等返回既有流水，行锁防并发重复发起。</p>
     *
     * @param warningId 预警ID
     * @param orgId     目标机构ID，可空（取规则默认建议机构）
     * @param remark    转办备注，可空
     * @param operator  操作人
     * @return 转办流水
     */
    public AiTicketTransfer transferByWarning(Long warningId, Long orgId, String remark, String operator);
}
