package ai.lawyers.system.service.lawyers.voice.copilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Copilot 实时辅助结果测试：默认值、可空字段兜底（空串/空列表/normal），
 * 建议动作透传。
 *
 * @author ai-lawyers
 */
class CopilotAssistResultTest
{
    @Test
    void empty_默认值()
    {
        CopilotAssistResult result = CopilotAssistResult.empty();

        assertThat(result.getDisputeType()).isEmpty();
        assertThat(result.getClaims()).isEmpty();
        assertThat(result.getUrgency()).isEqualTo("normal");
        assertThat(result.getKeyFacts()).isEmpty();
        assertThat(result.getLaws()).isEmpty();
        assertThat(result.getTickets()).isEmpty();
        assertThat(result.getActions()).isEmpty();
        assertThat(result.getSuggestedScript()).isEmpty();
        assertThat(result.isElementDegraded()).isFalse();
    }

    @Test
    void 字符串字段空值兜底()
    {
        CopilotAssistResult result = CopilotAssistResult.empty();

        result.setDisputeType(null);
        result.setUrgency(null);
        result.setSuggestedScript(null);

        assertThat(result.getDisputeType()).isEmpty();
        assertThat(result.getUrgency()).isEqualTo("normal");
        assertThat(result.getSuggestedScript()).isEmpty();
    }

    @Test
    void 列表字段空值兜底()
    {
        CopilotAssistResult result = CopilotAssistResult.empty();

        result.setClaims(null);
        result.setKeyFacts(null);
        result.setLaws(null);
        result.setTickets(null);
        result.setActions(null);

        assertThat(result.getClaims()).isEmpty();
        assertThat(result.getKeyFacts()).isEmpty();
        assertThat(result.getLaws()).isEmpty();
        assertThat(result.getTickets()).isEmpty();
        assertThat(result.getActions()).isEmpty();
    }

    @Test
    void 正常赋值透传()
    {
        CopilotAssistResult result = CopilotAssistResult.empty();

        result.setDisputeType("劳动争议");
        result.setUrgency("urgent");
        result.setClaims(Collections.singletonList("追索加班费"));
        result.setKeyFacts(Collections.singletonList("连续加班 3 个月"));
        result.setElementDegraded(true);
        result.setSuggestedScript("您好，我先帮您梳理一下加班事实。");

        assertThat(result.getDisputeType()).isEqualTo("劳动争议");
        assertThat(result.getUrgency()).isEqualTo("urgent");
        assertThat(result.getClaims()).containsExactly("追索加班费");
        assertThat(result.getKeyFacts()).containsExactly("连续加班 3 个月");
        assertThat(result.isElementDegraded()).isTrue();
        assertThat(result.getSuggestedScript()).contains("加班事实");
    }

    @Test
    void 建议动作透传草稿载荷()
    {
        Map<String, Object> payload = new HashMap<>();
        payload.put("summary", "加班费争议");
        CopilotAssistResult.SuggestedAction action =
                new CopilotAssistResult.SuggestedAction("createTicket", "创建工单", payload);

        assertThat(action.getAction()).isEqualTo("createTicket");
        assertThat(action.getLabel()).isEqualTo("创建工单");
        assertThat(action.getPayload()).containsEntry("summary", "加班费争议");
    }
}
