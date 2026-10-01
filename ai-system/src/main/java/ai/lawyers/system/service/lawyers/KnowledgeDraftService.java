package ai.lawyers.system.service.lawyers;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.domain.lawyers.quality.AiQualityInspection;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeMapper;
import ai.lawyers.system.mapper.lawyers.quality.AiQualityInspectionMapper;

/**
 * P1-10（V2.59）：知识库自动运营——优质通话/工单经 LLM 沉淀为知识草稿。
 *
 * <p>所有 AI 生成内容一律落为 status=正常、auditStatus=<b>待审核</b> 的知识行，
 * 复用现有知识审核链路（人工核验事实/法条后才进入 RAG 索引），模型不直接发布。
 * 去重：source_url 存 {@code source://call/{recordId}} / {@code source://ticket/{ticketId}}，
 * 已沉淀的来源拒绝重复生成。</p>
 *
 * @author ai-lawyers
 */
@Service
public class KnowledgeDraftService
{
    private static final Logger log = LoggerFactory.getLogger(KnowledgeDraftService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private AiLegalKnowledgeMapper knowledgeMapper;

    @Autowired
    private AiCallRecordMapper callRecordMapper;

    @Autowired
    private AiCallTicketMapper ticketMapper;

    @Autowired
    private AiQualityInspectionMapper inspectionMapper;

    @Autowired(required = false)
    private IAiModelConfigService modelConfigService;

    /**
     * 从通话生成知识草稿。
     *
     * @return 新知识ID
     * @throws IllegalArgumentException 来源不存在/无转写/已沉淀/模型不可用
     */
    public Long draftFromCall(Long recordId)
    {
        if (recordId == null)
        {
            throw new IllegalArgumentException("通话记录ID不能为空");
        }
        AiCallRecord record = callRecordMapper.selectAiCallRecordByRecordId(recordId);
        if (record == null)
        {
            throw new IllegalArgumentException("通话记录不存在");
        }
        String material = StringUtils.isNotEmpty(record.getTranscript())
                ? record.getTranscript() : record.getContent();
        if (StringUtils.isEmpty(material))
        {
            throw new IllegalArgumentException("该通话尚无转写文本或咨询内容，无法沉淀");
        }
        String sourceKey = "source://call/" + recordId;
        return persistDraft(material, "AI沉淀-通话", sourceKey,
                "通话 " + recordId, record.getContent());
    }

    /**
     * 从工单生成知识草稿。
     */
    public Long draftFromTicket(Long ticketId)
    {
        if (ticketId == null)
        {
            throw new IllegalArgumentException("工单ID不能为空");
        }
        AiCallTicket ticket = ticketMapper.selectAiCallTicketByTicketId(ticketId);
        if (ticket == null)
        {
            throw new IllegalArgumentException("工单不存在");
        }
        String material = (StringUtils.isNotEmpty(ticket.getTitle()) ? ticket.getTitle() + "。" : "")
                + (StringUtils.isNotEmpty(ticket.getContent()) ? ticket.getContent() : "")
                + (StringUtils.isNotEmpty(ticket.getProcessContent())
                        ? "\n处理内容：" + ticket.getProcessContent() : "");
        if (StringUtils.isEmpty(material.trim()))
        {
            throw new IllegalArgumentException("该工单无内容，无法沉淀");
        }
        String sourceKey = "source://ticket/" + ticketId;
        return persistDraft(material, "AI沉淀-工单", sourceKey,
                "工单 " + ticketId, null);
    }

    /**
     * 扫描高分优质通话批量生成草稿（可由定时任务每日调用）。
     *
     * @param days     回溯天数（1~30，非法值取 7）
     * @param minScore 最低质检分（默认 90）
     * @param limit    本次最多生成条数（1~50）
     * @return 实际新生成草稿条数（已沉淀的跳过）
     */
    public int scanQualityCalls(Integer days, BigDecimal minScore, Integer limit)
    {
        int d = (days != null && days >= 1 && days <= 30) ? days : 7;
        BigDecimal score = minScore != null ? minScore : BigDecimal.valueOf(90d);
        int lim = (limit != null && limit >= 1 && limit <= 50) ? limit : 20;

        List<AiQualityInspection> candidates =
                inspectionMapper.selectHighScoreInspections(d, score, lim * 2);
        int created = 0;
        if (candidates != null)
        {
            for (AiQualityInspection ins : candidates)
            {
                if (created >= lim || ins.getRecordId() == null)
                {
                    continue;
                }
                try
                {
                    draftFromCall(ins.getRecordId());
                    created++;
                }
                catch (IllegalArgumentException e)
                {
                    // 已沉淀/无转写等：跳过继续
                    log.debug("知识沉淀跳过 recordId={}: {}", ins.getRecordId(), e.getMessage());
                }
            }
        }
        log.info("知识自动沉淀扫描完成：候选 {} 新生成草稿 {}",
                candidates == null ? 0 : candidates.size(), created);
        return created;
    }

    /**
     * LLM 生成草稿字段并落待审知识行，返回新知识ID。
     */
    private Long persistDraft(String material, String source, String sourceKey,
                              String sourceDesc, String consultationContent)
    {
        if (knowledgeMapper.countBySourceUrl(sourceKey) > 0)
        {
            throw new IllegalArgumentException(sourceDesc + " 已沉淀过知识草稿，请勿重复生成");
        }
        if (modelConfigService == null)
        {
            throw new IllegalArgumentException("模型配置服务未装配，无法生成知识草稿");
        }
        try
        {
            String raw = modelConfigService.chatJson(DRAFT_SYSTEM_PROMPT,
                    "原始材料：\n" + material,
                    ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder.SCENE_SUMMARY);
            JsonNode node = MAPPER.readTree(cleanJson(raw));
            String title = node.path("title").asText("").trim();
            String content = node.path("content").asText("").trim();
            if (StringUtils.isEmpty(title) || StringUtils.isEmpty(content))
            {
                throw new IllegalStateException("模型返回的标题或内容为空");
            }
            AiLegalKnowledge knowledge = new AiLegalKnowledge();
            knowledge.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
            knowledge.setContent(content);
            knowledge.setKeywords(clip(node.path("keywords").asText(""), 500));
            knowledge.setLawArticle(clip(node.path("lawArticle").asText(""), 200));
            knowledge.setSource(source);
            knowledge.setSourceUrl(sourceKey);
            knowledge.setStatus("0");
            knowledge.setAuditStatus("0");
            knowledge.setCreateBy("ai-draft");
            knowledge.setAuditRemark("AI 于 " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())
                    + " 从" + sourceDesc + "自动生成，请人工审核并核验事实与法条；"
                    + (StringUtils.isNotEmpty(consultationContent)
                            ? "原始咨询：" + clip(consultationContent, 200) : ""));
            knowledgeMapper.insertAiLegalKnowledge(knowledge);
            log.info("知识草稿已生成待审 {} knowledgeId={}", sourceDesc, knowledge.getKnowledgeId());
            return knowledge.getKnowledgeId();
        }
        catch (RuntimeException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new IllegalStateException("知识草稿生成失败：" + e.getMessage(), e);
        }
    }

    /** 截取模型返回中最外层 { ... } */
    private String cleanJson(String raw)
    {
        if (raw == null)
        {
            return "{}";
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start)
        {
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    private String clip(String value, int max)
    {
        if (value == null)
        {
            return "";
        }
        String v = value.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    /** 草稿生成系统提示词：只提炼材料中可核验内容，不编造法条 */
    static final String DRAFT_SYSTEM_PROMPT =
            "你是12348公共法律服务热线的知识运营专家。请基于给定原始材料，提炼一条可复用的法律知识，"
            + "内容须客观准确、为问答/要点式，材料中没有的事实和法条不得编造，法条不确定时留空。"
            + "仅返回JSON：{\"title\":\"标题，20字内\",\"content\":\"知识正文，条理清晰\","
            + "\"keywords\":\"关键词，逗号分隔\",\"lawArticle\":\"相关法条（无则空）\"}";
}
