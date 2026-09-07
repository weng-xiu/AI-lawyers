package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiRiskWarning;
import ai.lawyers.system.mapper.lawyers.AiRiskWarningMapper;
import ai.lawyers.system.service.lawyers.IAiRiskWarningService;
import ai.lawyers.system.service.lawyers.queue.MessageNotifyDispatcher;

@Service
public class AiRiskWarningServiceImpl implements IAiRiskWarningService
{
    private static final Logger log = LoggerFactory.getLogger(AiRiskWarningServiceImpl.class);

    @Autowired
    private AiRiskWarningMapper aiRiskWarningMapper;

    @Autowired(required = false)
    private MessageNotifyDispatcher messageNotifyDispatcher;

    @Override
    public AiRiskWarning selectAiRiskWarningByWarningId(Long warningId)
    {
        return aiRiskWarningMapper.selectAiRiskWarningByWarningId(warningId);
    }

    @Override
    public List<AiRiskWarning> selectAiRiskWarningList(AiRiskWarning aiRiskWarning)
    {
        return aiRiskWarningMapper.selectAiRiskWarningList(aiRiskWarning);
    }

    @Override
    public int insertAiRiskWarning(AiRiskWarning aiRiskWarning)
    {
        if (aiRiskWarning.getStatus() == null || aiRiskWarning.getStatus().isEmpty()) {
            aiRiskWarning.setStatus("0");
        }
        if (aiRiskWarning.getWarningLevel() == null || aiRiskWarning.getWarningLevel().isEmpty()) {
            aiRiskWarning.setWarningLevel("3");
        }
        int rows = aiRiskWarningMapper.insertAiRiskWarning(aiRiskWarning);
        // 新预警生成后广播站内信（T5-3 消息中心），无明确接收人，通知全部在职用户
        if (rows > 0 && aiRiskWarning.getWarningId() != null && messageNotifyDispatcher != null)
        {
            try
            {
                String type = StringUtils.isNotEmpty(aiRiskWarning.getWarningType())
                        ? aiRiskWarning.getWarningType() : "未知类型";
                String customer = StringUtils.isNotEmpty(aiRiskWarning.getCustomerName())
                        ? "，客户：" + aiRiskWarning.getCustomerName() : "";
                String detail = StringUtils.isNotEmpty(aiRiskWarning.getContent())
                        ? "，触发内容：" + aiRiskWarning.getContent() : "";
                messageNotifyDispatcher.broadcast("3",
                        "风险预警（" + warningLevelText(aiRiskWarning.getWarningLevel()) + "）：" + type,
                        "产生一条" + warningLevelText(aiRiskWarning.getWarningLevel()) + "级风险预警，类型：" + type
                                + customer + detail + "，请及时处理。",
                        "warning", aiRiskWarning.getWarningId(), "system");
            }
            catch (Exception e)
            {
                log.warn("风险预警站内信广播失败, warningId={}", aiRiskWarning.getWarningId(), e);
            }
        }
        return rows;
    }

    private String warningLevelText(String level)
    {
        if ("1".equals(level))
        {
            return "高";
        }
        if ("2".equals(level))
        {
            return "中";
        }
        return "低";
    }

    @Override
    public int updateAiRiskWarning(AiRiskWarning aiRiskWarning)
    {
        return aiRiskWarningMapper.updateAiRiskWarning(aiRiskWarning);
    }

    @Override
    public int deleteAiRiskWarningByWarningIds(Long[] warningIds)
    {
        return aiRiskWarningMapper.deleteAiRiskWarningByWarningIds(warningIds);
    }
}
