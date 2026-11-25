package ai.lawyers.system.service.lawyers.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * N3 {@link LegalCitationGuard} 单元测试：条号抽取、上下文核验、无命中保守提示、
 * strip/disclaimer 两种模式、开关与空值安全。
 *
 * @author ai-lawyers
 */
class LegalCitationGuardTest
{
    private LegalCitationGuard guard()
    {
        LegalCitationGuard g = new LegalCitationGuard();
        // @Value 默认值在单测不注入，显式补齐与生产一致的默认配置
        ReflectionTestUtils.setField(g, "enabled", true);
        ReflectionTestUtils.setField(g, "mode", "disclaimer");
        ReflectionTestUtils.setField(g, "noContextHint",
                "（以上涉及的具体法条请以官方公布原文为准，建议查阅原文或咨询人工坐席核实。）");
        return g;
    }

    private RagChunk chunk(Long knowledgeId, Long chunkId, String title, String lawArticle, String content)
    {
        AiLegalKnowledgeChunk entity = new AiLegalKnowledgeChunk();
        entity.setKnowledgeId(knowledgeId);
        entity.setChunkId(chunkId);
        entity.setTitle(title);
        entity.setLawArticle(lawArticle);
        entity.setChunkContent(content);
        return new RagChunk(entity, 1.0d);
    }

    @Test
    void noCitationInAnswer_passesThrough()
    {
        LegalCitationGuard g = guard();
        CitationCheckResult r = g.check("您可以先与对方协商，协商不成再起诉。",
                Collections.singletonList(chunk(1L, 10L, "标题", null, "协商优先")));
        assertThat(r.isIntercepted()).isFalse();
        assertThat(r.getAnswer()).isEqualTo("您可以先与对方协商，协商不成再起诉。");
        assertThat(r.getUnsupportedCitations()).isEmpty();
    }

    @Test
    void citationSupportedByContextLawArticle_notIntercepted()
    {
        LegalCitationGuard g = guard();
        // 上下文法条字段含第一百零八条，答复引用同一条号 → 有支撑
        List<RagChunk> hits = Collections.singletonList(
                chunk(1L, 10L, "民法典", "《民法典》第一百零八条", "不可抗力相关"));
        CitationCheckResult r = g.check("依据《民法典》第一百零八条，您可以主张免责。", hits);
        assertThat(r.isIntercepted()).isFalse();
        assertThat(r.getUnsupportedCitations()).isEmpty();
    }

    @Test
    void citationSupportedByContextContent_notIntercepted()
    {
        LegalCitationGuard g = guard();
        // 条号出现在分块正文而非法条字段，同样视为有支撑
        List<RagChunk> hits = Collections.singletonList(
                chunk(1L, 10L, "劳动法", null, "根据劳动合同法第三十九条，用人单位可以解除劳动合同。"));
        CitationCheckResult r = g.check("根据劳动合同法第三十九条，单位可以解除合同。", hits);
        assertThat(r.isIntercepted()).isFalse();
    }

    @Test
    void unsupportedCitationWithHits_disclaimerMode_appendsHintAndRecords()
    {
        LegalCitationGuard g = guard();
        // 上下文只支撑第三十九条，答复额外编造第一百零八条
        List<RagChunk> hits = Collections.singletonList(
                chunk(1L, 10L, "劳动合同法", null, "劳动合同法第三十九条规定了解除情形。"));
        String answer = "根据劳动合同法第三十九条和第一百零八条，单位应当赔偿。";
        CitationCheckResult r = g.check(answer, hits);
        assertThat(r.isIntercepted()).isTrue();
        assertThat(r.getUnsupportedCitations()).containsExactly("第一百零八条");
        assertThat(r.getAnswer()).contains(answer).contains("请以官方公布原文为准");
    }

    @Test
    void unsupportedCitationWithHits_stripMode_replacesWithGenericWording()
    {
        LegalCitationGuard g = guard();
        ReflectionTestUtils.setField(g, "mode", "strip");
        List<RagChunk> hits = Collections.singletonList(
                chunk(1L, 10L, "劳动合同法", null, "劳动合同法第三十九条规定了解除情形。"));
        CitationCheckResult r = g.check("根据第一百零八条，单位应当赔偿。", hits);
        assertThat(r.isIntercepted()).isTrue();
        assertThat(r.getAnswer()).doesNotContain("第一百零八条").contains("相关法律规定");
    }

    @Test
    void noHitsButCitesArticle_appendsNoContextHint()
    {
        LegalCitationGuard g = guard();
        CitationCheckResult r = g.check("依据刑法第二百六十六条，这属于诈骗。", new ArrayList<>());
        assertThat(r.isIntercepted()).isTrue();
        assertThat(r.getUnsupportedCitations()).contains("第二百六十六条");
        assertThat(r.getAnswer()).contains("刑法第二百六十六条").contains("请以官方公布原文为准");
    }

    @Test
    void noHitsNoCitation_notIntercepted()
    {
        LegalCitationGuard g = guard();
        CitationCheckResult r = g.check("建议您保留好转账记录等证据。", null);
        assertThat(r.isIntercepted()).isFalse();
    }

    @Test
    void disabled_passesThroughEvenWithHallucinatedCitation()
    {
        LegalCitationGuard g = guard();
        ReflectionTestUtils.setField(g, "enabled", false);
        CitationCheckResult r = g.check("依据第一百零八条，您必胜诉。", Collections.emptyList());
        assertThat(r.isIntercepted()).isFalse();
        assertThat(r.getAnswer()).isEqualTo("依据第一百零八条，您必胜诉。");
    }

    @Test
    void blankAnswer_safe()
    {
        LegalCitationGuard g = guard();
        assertThat(g.check(null, Collections.emptyList()).getAnswer()).isNull();
        assertThat(g.check("  ", Collections.emptyList()).isIntercepted()).isFalse();
    }

    @Test
    void multipleCitations_mixedSupport_onlyUnsupportedRecorded()
    {
        LegalCitationGuard g = guard();
        List<RagChunk> hits = Arrays.asList(
                chunk(1L, 10L, "民法典", "第一百零八条", "内容"),
                chunk(2L, 20L, "劳动法", "第三十九条", "内容"));
        CitationCheckResult r = g.check(
                "第一百零八条、第三十九条有规定，另见第九百九十九条。", hits);
        assertThat(r.isIntercepted()).isTrue();
        assertThat(r.getUnsupportedCitations()).containsExactly("第九百九十九条");
    }

    @Test
    void arabicNumeralCitation_extractedAndChecked()
    {
        LegalCitationGuard g = guard();
        List<RagChunk> hits = Collections.singletonList(
                chunk(1L, 10L, "法", null, "第108条内容"));
        assertThat(g.check("依据第108条处理。", hits).isIntercepted()).isFalse();
        assertThat(g.check("依据第999条处理。", hits).isIntercepted()).isTrue();
    }
}
