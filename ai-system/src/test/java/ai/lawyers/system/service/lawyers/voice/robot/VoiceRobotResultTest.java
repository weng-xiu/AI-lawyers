package ai.lawyers.system.service.lawyers.voice.robot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

/**
 * 语音机器人单轮应答结果测试：正常应答/自助查询/降级三种工厂方法，
 * 以及可空字段（溯源、工单号）的兜底。
 *
 * @author ai-lawyers
 */
class VoiceRobotResultTest
{
    @Test
    void ok_不带降级标记()
    {
        VoiceRobotResult result = VoiceRobotResult.ok("您好，请问有什么可以帮您？");

        assertThat(result.getAnswer()).isEqualTo("您好，请问有什么可以帮您？");
        assertThat(result.isDegraded()).isFalse();
        assertThat(result.getFallbackReason()).isNull();
        assertThat(result.getAction()).isNull();
        assertThat(result.getSources()).isEmpty();
        assertThat(result.getTicketNos()).isEmpty();
    }

    @Test
    void ticketQuery_动作与工单摘要()
    {
        VoiceRobotResult result = VoiceRobotResult.ticketQuery("您有 2 个工单在办", Arrays.asList("T1", "T2"));

        assertThat(result.getAction()).isEqualTo("query_ticket");
        assertThat(result.getTicketCount()).isEqualTo(2);
        assertThat(result.getTicketNos()).containsExactly("T1", "T2");
        assertThat(result.isDegraded()).isFalse();
    }

    @Test
    void ticketQuery_工单号为空时计数为零()
    {
        VoiceRobotResult result = VoiceRobotResult.ticketQuery("暂无在办工单", null);

        assertThat(result.getTicketNos()).isEmpty();
        assertThat(result.getTicketCount()).isZero();
        assertThat(result.getAction()).isEqualTo("query_ticket");
    }

    @Test
    void degraded_携带原因()
    {
        VoiceRobotResult result = VoiceRobotResult.degraded("抱歉，我暂时无法回答", "llm_error");

        assertThat(result.isDegraded()).isTrue();
        assertThat(result.getFallbackReason()).isEqualTo("llm_error");
        assertThat(result.getAnswer()).isEqualTo("抱歉，我暂时无法回答");
    }

    @Test
    void setSources_空值回退空列表()
    {
        VoiceRobotResult result = VoiceRobotResult.ok("答");

        result.setSources(null);
        assertThat(result.getSources()).isEmpty();

        result.setSources(Collections.singletonList("劳动合同法 第十条"));
        assertThat(result.getSources()).containsExactly("劳动合同法 第十条");
    }

    @Test
    void 统计字段可写()
    {
        VoiceRobotResult result = VoiceRobotResult.ok("答");

        result.setRagHits(3);
        result.setRagMs(12L);
        result.setLlmMs(340L);

        assertThat(result.getRagHits()).isEqualTo(3);
        assertThat(result.getRagMs()).isEqualTo(12L);
        assertThat(result.getLlmMs()).isEqualTo(340L);
    }
}
