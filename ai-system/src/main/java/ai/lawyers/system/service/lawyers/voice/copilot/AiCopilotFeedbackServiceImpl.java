package ai.lawyers.system.service.lawyers.voice.copilot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCopilotFeedback;
import ai.lawyers.system.mapper.lawyers.AiCopilotFeedbackMapper;

/**
 * F4：Copilot 采纳行为埋点与采纳率统计服务实现。
 *
 * <p>{@link #record} 由服务端补全坐席ID/姓名/班组（不信任前端），并校验
 * 建议类型与行为枚举；recordId 缺失但 sessionId 为纯数字时解析兜底。
 * 任何异常只记日志（埋点不允许影响坐席操作）。</p>
 *
 * @author ai-lawyers
 */
@Service
public class AiCopilotFeedbackServiceImpl implements IAiCopilotFeedbackService
{
    private static final Logger log = LoggerFactory.getLogger(AiCopilotFeedbackServiceImpl.class);

    private static final Set<String> TYPES = new HashSet<>(
            Arrays.asList(AiCopilotFeedback.TYPE_ELEMENT, AiCopilotFeedback.TYPE_LAW,
                    AiCopilotFeedback.TYPE_TICKET));

    private static final Set<String> ACTIONS = new HashSet<>(
            Arrays.asList(AiCopilotFeedback.ACTION_ADOPT, AiCopilotFeedback.ACTION_MODIFY,
                    AiCopilotFeedback.ACTION_IGNORE));

    @Autowired
    private AiCopilotFeedbackMapper feedbackMapper;

    @Override
    public void record(AiCopilotFeedback feedback)
    {
        if (feedback == null)
        {
            return;
        }
        try
        {
            if (!TYPES.contains(feedback.getSuggestionType())
                    || !ACTIONS.contains(feedback.getAction()))
            {
                log.warn("Copilot 埋点非法 type={}, action={}",
                        feedback.getSuggestionType(), feedback.getAction());
                return;
            }
            fillAgent(feedback);
            resolveRecordId(feedback);
            feedback.setCreateTime(new Date());
            feedbackMapper.insertFeedback(feedback);
        }
        catch (Exception e)
        {
            log.warn("Copilot 埋点写入异常: {}", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> adoptionTotal(Date beginTime, Date endTime)
    {
        Map<String, Object> row = feedbackMapper.selectAdoptionTotal(beginTime, endTime);
        return withRate(row == null ? new LinkedHashMap<String, Object>() : row);
    }

    @Override
    public List<Map<String, Object>> adoptionByAgent(Date beginTime, Date endTime)
    {
        List<Map<String, Object>> out = new ArrayList<>();
        List<Map<String, Object>> rows = feedbackMapper.selectAdoptionByAgent(beginTime, endTime);
        if (rows != null)
        {
            for (Map<String, Object> row : rows)
            {
                out.add(withRate(row));
            }
        }
        return out;
    }

    /** 服务端补坐席信息（SecurityUtils 无登录上下文时保留前端值，单测可注入） */
    private void fillAgent(AiCopilotFeedback feedback)
    {
        try
        {
            Long userId = SecurityUtils.getUserId();
            if (userId != null)
            {
                feedback.setAgentId(userId);
                feedback.setAgentName(SecurityUtils.getUsername());
                feedback.setDeptId(SecurityUtils.getDeptId());
            }
        }
        catch (Exception ignore)
        {
            // 非请求线程/无登录上下文（如单测），不覆盖已有值
        }
    }

    /** recordId 缺失时，纯数字 sessionId 解析兜底 */
    private void resolveRecordId(AiCopilotFeedback feedback)
    {
        if (feedback.getRecordId() != null)
        {
            return;
        }
        String s = feedback.getSessionId();
        if (StringUtils.isEmpty(s))
        {
            return;
        }
        String t = s.trim();
        for (int i = 0; i < t.length(); i++)
        {
            if (!Character.isDigit(t.charAt(i)))
            {
                return;
            }
        }
        try
        {
            feedback.setRecordId(Long.valueOf(t));
        }
        catch (NumberFormatException ignore)
        {
        }
    }

    /** 补 adoptRate（百分比，保留2位小数）；并将各计数 null 归一为 0 */
    private Map<String, Object> withRate(Map<String, Object> row)
    {
        Map<String, Object> m = new LinkedHashMap<>(row);
        long total = toLong(m.get("total"));
        long adopt = toLong(m.get("adoptCount"));
        m.put("total", total);
        m.put("adoptCount", adopt);
        m.put("modifyCount", toLong(m.get("modifyCount")));
        m.put("ignoreCount", toLong(m.get("ignoreCount")));
        m.put("adoptRate", total > 0 ? Math.round(adopt * 10000d / total) / 100d : 0d);
        return m;
    }

    private static long toLong(Object v)
    {
        if (v == null)
        {
            return 0L;
        }
        if (v instanceof Number)
        {
            return ((Number) v).longValue();
        }
        try
        {
            return Long.parseLong(String.valueOf(v));
        }
        catch (NumberFormatException e)
        {
            return 0L;
        }
    }
}
