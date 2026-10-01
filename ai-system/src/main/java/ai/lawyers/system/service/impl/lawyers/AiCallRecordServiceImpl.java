package ai.lawyers.system.service.impl.lawyers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.common.utils.sign.PiiCryptoUtils;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.AiCallAgentStatusMapper;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;
import ai.lawyers.system.service.lawyers.IPiiSearchTokenService;

@Service
public class AiCallRecordServiceImpl implements IAiCallRecordService 
{
    private static final Logger log = LoggerFactory.getLogger(AiCallRecordServiceImpl.class);

    @Autowired
    private AiCallRecordMapper aiCallRecordMapper;

    @Autowired
    private AiCallAgentStatusMapper aiCallAgentStatusMapper;

    @Autowired(required = false)
    private IPiiSearchTokenService piiSearchTokenService;

    @Override
    public AiCallRecord selectAiCallRecordByRecordId(Long recordId)
    {
        return aiCallRecordMapper.selectAiCallRecordByRecordId(recordId);
    }

    @Override
    public List<AiCallRecord> selectAiCallRecordList(AiCallRecord aiCallRecord)
    {
        applyTokenGroups(aiCallRecord);
        return aiCallRecordMapper.selectAiCallRecordList(aiCallRecord);
    }

    @Override
    public int insertAiCallRecord(AiCallRecord aiCallRecord)
    {
        String plainNumber = normalizeNumber(aiCallRecord);
        int rows = aiCallRecordMapper.insertAiCallRecord(aiCallRecord);
        rebuildTokens(aiCallRecord.getRecordId(), plainNumber);
        return rows;
    }

    @Override
    public int updateAiCallRecord(AiCallRecord aiCallRecord)
    {
        String plainNumber = normalizeNumber(aiCallRecord);
        int rows = aiCallRecordMapper.updateAiCallRecord(aiCallRecord);
        rebuildTokens(aiCallRecord.getRecordId(), plainNumber);
        return rows;
    }

    /** G1-b2：列表号码条件转位置分片 token 分组（调用方未显式设置时统一在此派生） */
    private void applyTokenGroups(AiCallRecord query)
    {
        if (query != null && StringUtils.isNotEmpty(query.getCallerNumber())
                && query.getTokenGroups() == null)
        {
            query.setTokenGroups(PiiCryptoUtils.tokenGroups(query.getCallerNumber()));
        }
    }

    /**
     * G1-b2：写前号码规整——明文/密文统一归一为密文 + 盲索引。
     * @return 明文号码（供 token 回填）；号码为空时返回 null
     */
    private String normalizeNumber(AiCallRecord record)
    {
        if (record == null || StringUtils.isEmpty(record.getCallerNumber()))
        {
            return null;
        }
        String plain = PiiCryptoUtils.decrypt(record.getCallerNumber());
        record.setCallerNumber(PiiCryptoUtils.encrypt(plain));
        record.setCallerNumberIndex(PiiCryptoUtils.blindIndex(plain));
        return plain;
    }

    /** G1-b2：按属主重建号码模糊检索 token（失败仅告警，可由迁移接口补建） */
    private void rebuildTokens(Long recordId, String plainNumber)
    {
        if (piiSearchTokenService != null && recordId != null
                && StringUtils.isNotEmpty(plainNumber))
        {
            try
            {
                piiSearchTokenService.rebuild(IPiiSearchTokenService.OWNER_CALL_RECORD,
                        recordId, plainNumber);
            }
            catch (Exception e)
            {
                log.warn("话单模糊检索 token 重建失败 recordId={}: {}", recordId, e.getMessage());
            }
        }
    }

    @Override
    public int deleteAiCallRecordByRecordId(Long recordId)
    {
        return aiCallRecordMapper.deleteAiCallRecordByRecordId(recordId);
    }

    @Override
    public int deleteAiCallRecordByRecordIds(Long[] recordIds)
    {
        return aiCallRecordMapper.deleteAiCallRecordByRecordIds(recordIds);
    }

    @Override
    public List<AiCallRecord> selectAiCallRecordByAgentId(Long agentId)
    {
        return aiCallRecordMapper.selectAiCallRecordByAgentId(agentId);
    }

    @Override
    public java.util.Map<String, Object> getCallStatistics()
    {
        return aiCallRecordMapper.getCallStatistics();
    }

    @Override
    public List<java.util.Map<String, Object>> getCallStatisticsByAgent()
    {
        return aiCallRecordMapper.getCallStatisticsByAgent();
    }

    @Override
    public List<java.util.Map<String, Object>> getCallStatisticsByCategory()
    {
        return aiCallRecordMapper.getCallStatisticsByCategory();
    }

    @Override
    public List<java.util.Map<String, Object>> getCallStatisticsByDate(Integer days)
    {
        return aiCallRecordMapper.getCallStatisticsByDate(days);
    }

    @Override
    public Map<String, Object> getWorkbenchSummary()
    {
        Map<String, Object> result = new HashMap<>();

        // 今日统计
        Map<String, Object> todayStats = aiCallRecordMapper.selectTodayCallStats();
        result.put("todayCallStats", todayStats);

        // 全部统计
        Map<String, Object> totalStats = aiCallRecordMapper.getCallStatistics();
        result.put("totalStats", totalStats);

        // 团队统计
        List<Map<String, Object>> teamStats = aiCallRecordMapper.getCallStatisticsByAgent();
        result.put("teamStats", teamStats);

        // 在线坐席
        result.put("onlineAgentCount", aiCallAgentStatusMapper.selectOnlineAgents().size());

        // 最近通话 Top 10
        result.put("recentCalls", aiCallRecordMapper.selectRecentCalls(10));

        return result;
    }

    @Override
    public AiCallRecord selectAiCallRecordByCallUuid(String callUuid)
    {
        return aiCallRecordMapper.selectAiCallRecordByCallUuid(callUuid);
    }

    @Override
    public int updateRecordingInfo(AiCallRecord aiCallRecord)
    {
        return aiCallRecordMapper.updateRecordingInfo(aiCallRecord);
    }
}
