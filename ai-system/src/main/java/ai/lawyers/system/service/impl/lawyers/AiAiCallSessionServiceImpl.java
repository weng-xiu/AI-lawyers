package ai.lawyers.system.service.impl.lawyers;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.system.domain.lawyers.AiAiCallSession;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.enums.AiAssistSessionStatusEnum;
import ai.lawyers.system.mapper.lawyers.AiAiCallSessionMapper;
import ai.lawyers.system.service.lawyers.IAiAiCallSessionService;
import ai.lawyers.system.service.lawyers.IAiLegalKnowledgeService;

/**
 * AI律师辅助会话服务实现（独立链路）
 *
 * 设计原则（与人工接听链路彻底解耦）：
 *  1. 独立的触发条件：仅由人工坐席"接听"事件异步触发 startAssistSession，互不直接调用人工状态机；
 *  2. 独立的处理函数：analyzeAndRecommend / summarizeAndEnd 各自管理 AI 辅助状态机；
 *  3. 独立的状态管理：全程只读写 ai_ai_call_session 表与 AiAssistSessionStatusEnum，
 *     不读取、不修改 ai_call_agent_status / ai_call_record 的任何状态字段。
 */
@Service
public class AiAiCallSessionServiceImpl implements IAiAiCallSessionService
{
    @Autowired
    private AiAiCallSessionMapper aiAiCallSessionMapper;

    @Autowired
    private IAiLegalKnowledgeService aiLegalKnowledgeService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public AiAiCallSession selectAiAiCallSessionBySessionId(Long sessionId)
    {
        return aiAiCallSessionMapper.selectAiAiCallSessionBySessionId(sessionId);
    }

    @Override
    public AiAiCallSession selectAiAiCallSessionByRecordId(Long recordId)
    {
        return aiAiCallSessionMapper.selectAiAiCallSessionByRecordId(recordId);
    }

    @Override
    public List<AiAiCallSession> selectAiAiCallSessionList(AiAiCallSession aiAiCallSession)
    {
        return aiAiCallSessionMapper.selectAiAiCallSessionList(aiAiCallSession);
    }

    @Override
    public int insertAiAiCallSession(AiAiCallSession aiAiCallSession)
    {
        return aiAiCallSessionMapper.insertAiAiCallSession(aiAiCallSession);
    }

    @Override
    public int updateAiAiCallSession(AiAiCallSession aiAiCallSession)
    {
        return aiAiCallSessionMapper.updateAiAiCallSession(aiAiCallSession);
    }

    @Override
    public int deleteAiAiCallSessionBySessionId(Long sessionId)
    {
        return aiAiCallSessionMapper.deleteAiAiCallSessionBySessionId(sessionId);
    }

    @Override
    public int deleteAiAiCallSessionBySessionIds(Long[] sessionIds)
    {
        return aiAiCallSessionMapper.deleteAiAiCallSessionBySessionIds(sessionIds);
    }

    /**
     * 独立触发：人工接听后启动 AI 辅助会话。
     * 仅写入 ai_ai_call_session，不触碰任何人工状态。
     */
    @Override
    public AiAiCallSession startAssistSession(Long recordId, Long agentId, String callerPhone, String callerName)
    {
        AiAiCallSession session = new AiAiCallSession();
        session.setRecordId(recordId);
        session.setAgentId(agentId);
        session.setCallerPhone(callerPhone);
        session.setCallerName(callerName);
        session.setSessionStatus(AiAssistSessionStatusEnum.INIT.getCode());
        session.setStartTime(new Date());
        session.setCreateBy("AI-Assist");
        session.setRemark("人工接听触发AI辅助会话");
        aiAiCallSessionMapper.insertAiAiCallSession(session);
        return session;
    }

    /**
     * 独立处理函数：意图识别 + 法条/话术推荐。
     * 状态流转：初始化/分析中 → 推荐中。
     */
    @Override
    public AiAiCallSession analyzeAndRecommend(Long sessionId, String callContent)
    {
        AiAiCallSession session = aiAiCallSessionMapper.selectAiAiCallSessionBySessionId(sessionId);
        if (session == null)
        {
            return null;
        }

        // 独立状态：置为"分析中"
        session.setSessionStatus(AiAssistSessionStatusEnum.ANALYZING.getCode());
        aiAiCallSessionMapper.updateAiAiCallSession(session);

        // 独立意图识别（基于关键词，与人工逻辑无关）
        String intent = recognizeIntent(callContent);
        session.setIntentCategory(intent);

        // 独立法条推荐（检索知识库，不影响人工通话）
        List<AiLegalKnowledge> laws = recommendLaws(callContent, intent);
        String lawsJson = toJson(laws);
        session.setRecommendLaws(lawsJson);

        // 独立话术建议生成
        List<String> scripts = buildScripts(intent, laws);
        session.setRecommendScripts(toJson(scripts));

        // 状态：推荐中
        session.setSessionStatus(AiAssistSessionStatusEnum.RECOMMENDING.getCode());
        aiAiCallSessionMapper.updateAiAiCallSession(session);
        return session;
    }

    /**
     * 独立处理函数：生成小结并结束 AI 辅助会话。
     * 状态流转：推荐中 → 已小结 → 已结束。
     */
    @Override
    public AiAiCallSession summarizeAndEnd(Long sessionId, String callContent)
    {
        AiAiCallSession session = aiAiCallSessionMapper.selectAiAiCallSessionBySessionId(sessionId);
        if (session == null)
        {
            return null;
        }

        // 若尚未分析，先补一次推荐
        if (AiAssistSessionStatusEnum.INIT.getCode().equals(session.getSessionStatus())
                || AiAssistSessionStatusEnum.ANALYZING.getCode().equals(session.getSessionStatus()))
        {
            analyzeAndRecommend(sessionId, callContent);
            session = aiAiCallSessionMapper.selectAiAiCallSessionBySessionId(sessionId);
        }

        // 独立小结生成（不影响人工记录）
        String summary = buildSummary(session, callContent);
        session.setCallSummary(summary);
        session.setSessionStatus(AiAssistSessionStatusEnum.SUMMARIZED.getCode());
        aiAiCallSessionMapper.updateAiAiCallSession(session);

        // 结束会话
        session.setSessionStatus(AiAssistSessionStatusEnum.ENDED.getCode());
        session.setEndTime(new Date());
        aiAiCallSessionMapper.updateAiAiCallSession(session);
        return session;
    }

    // ===================== 以下为 AI 辅助链路内部的独立处理算法 =====================

    /** 独立意图识别（关键词匹配，与人工分类逻辑完全分离） */
    private String recognizeIntent(String content)
    {
        if (content == null || content.isEmpty())
        {
            return "普通咨询";
        }
        if (content.contains("离婚") || content.contains("抚养") || content.contains("婚姻"))
        {
            return "婚姻家庭";
        }
        if (content.contains("劳动") || content.contains("工伤") || content.contains("合同") || content.contains("工资"))
        {
            return "劳动纠纷";
        }
        if (content.contains("房产") || content.contains("拆迁") || content.contains("物业"))
        {
            return "房产纠纷";
        }
        if (content.contains("遗产") || content.contains("继承") || content.contains("遗嘱"))
        {
            return "遗产继承";
        }
        if (content.contains("刑事") || content.contains("诈骗") || content.contains("盗窃"))
        {
            return "刑事辩护";
        }
        return "普通咨询";
    }

    /** 独立法条推荐：检索知识库，取前3条 */
    private List<AiLegalKnowledge> recommendLaws(String content, String intent)
    {
        try
        {
            List<AiLegalKnowledge> byIntent = aiLegalKnowledgeService.searchAiLegalKnowledge(intent);
            if (byIntent != null && !byIntent.isEmpty())
            {
                return byIntent.size() > 3 ? byIntent.subList(0, 3) : byIntent;
            }
            if (content != null && !content.isEmpty())
            {
                List<AiLegalKnowledge> byContent = aiLegalKnowledgeService.searchAiLegalKnowledge(content);
                return (byContent == null || byContent.isEmpty()) ? new ArrayList<>()
                        : (byContent.size() > 3 ? byContent.subList(0, 3) : byContent);
            }
        }
        catch (Exception ignored)
        {
            // AI辅助失败不应影响人工通话
        }
        return new ArrayList<>();
    }

    /** 独立话术建议生成 */
    private List<String> buildScripts(String intent, List<AiLegalKnowledge> laws)
    {
        List<String> scripts = new ArrayList<>();
        scripts.add("您好，我是您的AI辅助律师，正在为您整理【" + intent + "】相关法律依据，请稍候。");
        if (laws != null)
        {
            for (AiLegalKnowledge k : laws)
            {
                scripts.add("可参考：" + (k.getTitle() == null ? "法律知识" : k.getTitle()));
            }
        }
        scripts.add("建议您首先安抚当事人情绪，再就核心争议点进行事实确认。");
        return scripts;
    }

    /** 独立小结生成 */
    private String buildSummary(AiAiCallSession session, String callContent)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("【AI辅助小结】\n");
        sb.append("咨询意图：").append(session.getIntentCategory() == null ? "普通咨询" : session.getIntentCategory()).append("\n");
        sb.append("主叫号码：").append(session.getCallerPhone() == null ? "未知" : session.getCallerPhone()).append("\n");
        sb.append("客户姓名：").append(session.getCallerName() == null ? "未知" : session.getCallerName()).append("\n");
        sb.append("通话概要：").append(callContent == null || callContent.isEmpty() ? "（无文本记录）" : callContent).append("\n");
        sb.append("推荐法条数：").append(session.getRecommendLaws() == null ? 0 : 1).append("\n");
        sb.append("生成时间：").append(new Date()).append("\n");
        sb.append("（本小结由AI辅助独立生成，仅供坐席参考，不写入人工通话记录）");
        return sb.toString();
    }

    private String toJson(Object obj)
    {
        try
        {
            return MAPPER.writeValueAsString(obj);
        }
        catch (Exception e)
        {
            return "[]";
        }
    }
}
