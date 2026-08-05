package ai.lawyers.system.service.impl.lawyers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiCallerProfile;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.mapper.lawyers.AiCallerProfileMapper;
import ai.lawyers.system.service.lawyers.ICallPopupService;

@Service
public class CallPopupServiceImpl implements ICallPopupService
{
    @Autowired
    private AiCallerProfileMapper aiCallerProfileMapper;

    @Autowired
    private AiCallRecordMapper aiCallRecordMapper;

    @Autowired
    private AiCallTicketMapper aiCallTicketMapper;

    @Override
    public Map<String, Object> getPopupProfile(String callerNumber)
    {
        Map<String, Object> result = new HashMap<>();
        // 实时通话统计
        Map<String, Object> callStats = aiCallRecordMapper.selectCallerCallStats(callerNumber);
        long callCount = 0L;
        long monthCallCount = 0L;
        Object lastCallTime = null;
        if (callStats != null) {
            callCount = toLong(callStats.get("callCount"));
            monthCallCount = toLong(callStats.get("monthCallCount"));
            lastCallTime = callStats.get("lastCallTime");
        }
        result.put("callCount", callCount);
        result.put("monthCallCount", monthCallCount);
        result.put("lastCallTime", lastCallTime);

        // 档案 + AI 分析字段
        AiCallerProfile profile = aiCallerProfileMapper.selectAiCallerProfileByCallerNumber(callerNumber);
        result.put("profile", profile);
        if (profile == null) {
            // 无档案时给出默认分析
            result.put("intentPrediction", "未知");
            result.put("intentConfidence", 0);
            result.put("consultPreference", "");
            result.put("highFreqProblem", "");
            result.put("freqMentionCount", 0);
            result.put("riskLevel", "0");
            result.put("emotionStatus", "平稳");
            result.put("emotionWarning", monthCallCount >= 3 ? "该用户近期来电频繁（本月" + monthCallCount + "次），建议关注情绪状态" : "");
            result.put("customerLevel", "");
            result.put("tags", "");
        } else {
            result.put("intentPrediction", profile.getIntentPrediction() != null ? profile.getIntentPrediction() : "未知");
            result.put("intentConfidence", profile.getIntentConfidence() != null ? profile.getIntentConfidence() : 0);
            result.put("consultPreference", profile.getConsultPreference() != null ? profile.getConsultPreference() : "");
            result.put("highFreqProblem", profile.getHighFreqProblem() != null ? profile.getHighFreqProblem() : "");
            result.put("freqMentionCount", profile.getFreqMentionCount() != null ? profile.getFreqMentionCount() : 0);
            result.put("riskLevel", profile.getRiskLevel() != null ? profile.getRiskLevel() : "0");
            result.put("emotionStatus", profile.getEmotionStatus() != null ? profile.getEmotionStatus() : "平稳");
            result.put("emotionWarning", profile.getEmotionWarning() != null ? profile.getEmotionWarning() : "");
            result.put("customerLevel", profile.getCustomerLevel() != null ? profile.getCustomerLevel() : "");
            result.put("tags", profile.getTags() != null ? profile.getTags() : "");
        }
        return result;
    }

    @Override
    public List<?> getPopupHistory(String callerNumber, Integer limit)
    {
        return aiCallRecordMapper.selectAiCallRecordByCallerNumber(callerNumber, limit);
    }

    @Override
    public List<?> getPopupTickets(String callerNumber)
    {
        AiCallTicket query = new AiCallTicket();
        query.setCallerNumber(callerNumber);
        return aiCallTicketMapper.selectAiCallTicketList(query);
    }

    @Override
    public List<Map<String, Object>> getPopupTrack(String callerNumber)
    {
        List<Map<String, Object>> track = new ArrayList<>();
        // 通话轨迹
        List<?> records = aiCallRecordMapper.selectAiCallRecordByCallerNumber(callerNumber, 50);
        for (Object obj : records) {
            if (obj instanceof AiCallRecord) {
                AiCallRecord r = (AiCallRecord) obj;
                Map<String, Object> node = new HashMap<>();
                node.put("type", "call");
                node.put("title", r.getCategoryName() != null ? r.getCategoryName() + "通话" : "来电通话");
                node.put("content", r.getContent());
                node.put("time", r.getCallTime());
                node.put("status", r.getStatus());
                node.put("duration", r.getDuration());
                track.add(node);
            }
        }
        // 工单轨迹
        AiCallTicket query = new AiCallTicket();
        query.setCallerNumber(callerNumber);
        List<?> tickets = aiCallTicketMapper.selectAiCallTicketList(query);
        for (Object obj : tickets) {
            if (obj instanceof AiCallTicket) {
                AiCallTicket t = (AiCallTicket) obj;
                Map<String, Object> node = new HashMap<>();
                node.put("type", "ticket");
                node.put("title", t.getTitle());
                node.put("content", t.getContent());
                node.put("time", t.getCreateTime());
                node.put("status", t.getStatus());
                node.put("ticketNo", t.getTicketNo());
                track.add(node);
            }
        }
        // 按时间倒序
        track.sort(new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                Object ta = a.get("time");
                Object tb = b.get("time");
                if (ta == null && tb == null) return 0;
                if (ta == null) return 1;
                if (tb == null) return -1;
                return tb.toString().compareTo(ta.toString());
            }
        });
        return track;
    }

    @Override
    public AiCallerProfile selectAiCallerProfileByCallerNumber(String callerNumber)
    {
        return aiCallerProfileMapper.selectAiCallerProfileByCallerNumber(callerNumber);
    }

    @Override
    public int updateAiCallerProfile(AiCallerProfile aiCallerProfile)
    {
        return aiCallerProfileMapper.updateAiCallerProfile(aiCallerProfile);
    }

    private long toLong(Object obj)
    {
        if (obj == null) return 0L;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try { return Long.parseLong(obj.toString()); } catch (Exception e) { return 0L; }
    }
}
