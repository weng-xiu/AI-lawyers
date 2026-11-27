package ai.lawyers.system.service.lawyers.voice.copilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ai.lawyers.system.service.lawyers.voice.SimulationGateEvaluator;
import ai.lawyers.system.service.lawyers.voice.SimulationGateProperties;

/**
 * {@link CopilotSimulationService} 单测：用 CopilotAssistService 子类做桩，
 * 无外部依赖，验证检查点判分、聚合通过率、违禁词与坏 JSON 拒绝。
 */
class CopilotSimulationServiceTest
{
    private CopilotSimulationService service;

    @BeforeEach
    void setUp()
    {
        service = new CopilotSimulationService();
        ReflectionTestUtils.setField(service, "copilotAssistService", stubAssist());
        // P1-8：注入关闭语义的真实评分器（纯关键词，无 embedding 依赖）
        ai.lawyers.system.service.lawyers.voice.SimulationScorer scorer =
                new ai.lawyers.system.service.lawyers.voice.SimulationScorer();
        ReflectionTestUtils.setField(scorer, "semanticEnabled", false);
        ReflectionTestUtils.setField(scorer, "semanticThreshold", 0.75d);
        ReflectionTestUtils.setField(service, "scorer", scorer);
    }

    @Test
    void run_mixedCases_aggregatesChecks()
    {
        String cases = "{\"cases\":["
                + "{\"caseId\":\"ok\",\"text\":\"OK\",\"checks\":{"
                + "\"requireElement\":true,\"disputeTypeKeywords\":[\"劳动\"],"
                + "\"claimKeywords\":[\"工资\"],\"expectActions\":[\"createTicket\"]}},"
                + "{\"caseId\":\"bad\",\"text\":\"BAD\",\"checks\":{"
                + "\"requireElement\":true,\"disputeTypeKeywords\":[\"劳动\"]}}]}";

        ObjectNode report = service.run(cases);
        assertEquals(2, report.get("totalCases").asInt());
        assertEquals(1, report.get("passedCases").asInt());
        assertEquals(1, report.get("failedCases").asInt());
        // ok 用例 4 检查点全过；bad 用例 2 检查点（要素+类型）均失败
        assertEquals(4, report.get("passedChecks").asInt());
        assertEquals(6, report.get("totalChecks").asInt());
        assertEquals(50d, report.get("casePassRate").asDouble(), 0.1);
    }

    @Test
    void run_forbiddenWord_caseFails()
    {
        String cases = "{\"cases\":[{\"caseId\":\"f\",\"text\":\"FORBID\",\"checks\":{"
                + "\"requireElement\":true,\"forbidden\":[\"包赢\"]}}]}";

        ObjectNode report = service.run(cases);
        assertEquals(0, report.get("passedCases").asInt());
    }

    @Test
    void run_lawCountAndUrgency_checked()
    {
        String cases = "{\"cases\":[{\"caseId\":\"u\",\"text\":\"OK\",\"checks\":{"
                + "\"expectUrgency\":\"normal\",\"lawKeywords\":[\"劳动\"],"
                + "\"requireLawCount\":1}}]}";

        ObjectNode report = service.run(cases);
        assertEquals(1, report.get("passedCases").asInt());
    }

    @Test
    void run_emptyCases_zeroReport()
    {
        ObjectNode report = service.run("{\"cases\":[]}");
        assertEquals(0, report.get("totalCases").asInt());
        assertEquals(0d, report.get("casePassRate").asDouble(), 0.01);
    }

    @Test
    void run_badJson_rejected()
    {
        assertThrows(IllegalArgumentException.class, () -> service.run("not json"));
    }

    /** N2：注入门禁组件（默认法条门槛 0.70） */
    private void attachGate()
    {
        ReflectionTestUtils.setField(service, "gateProperties", new SimulationGateProperties());
        ReflectionTestUtils.setField(service, "gateEvaluator", new SimulationGateEvaluator());
    }

    @Test
    void run_withGate_lawHitJudgedAndTicketSkipped()
    {
        // 桩 assist 返回的法条含"劳动"，lawKeywords 命中 → 法条命中率 1.0 ≥ 0.70
        attachGate();
        String cases = "{\"cases\":[{\"caseId\":\"u\",\"text\":\"OK\",\"checks\":{"
                + "\"lawKeywords\":[\"劳动\"]}}]}";
        ObjectNode gate = (ObjectNode) service.run(cases).get("gate");
        // 法条 judged 通过；工单 skipped 不参与，总判定仍通过
        assertTrue(gate.get("passed").asBoolean());
        ObjectNode law = (ObjectNode) gate.get("items").get(0);
        assertEquals("lawHitRate", law.get("name").asText());
        assertEquals("judged", law.get("status").asText());
        assertEquals(1.0d, law.get("actualRate").asDouble(), 0.0001);
        assertEquals(0.70d, law.get("thresholdRate").asDouble(), 0.0001);
        ObjectNode ticket = (ObjectNode) gate.get("items").get(1);
        assertEquals("ticketUsefulRate", ticket.get("name").asText());
        assertEquals("skipped", ticket.get("status").asText());
    }

    @Test
    void run_withGate_noLawKeywords_lawItemSkipped()
    {
        attachGate();
        // 用例不含 lawKeywords → 无法条检查点，法条项也 skipped，整体无 judged 项 → passed=true
        String cases = "{\"cases\":[{\"caseId\":\"u\",\"text\":\"OK\",\"checks\":{"
                + "\"disputeTypeKeywords\":[\"劳动\"]}}]}";
        ObjectNode gate = (ObjectNode) service.run(cases).get("gate");
        assertTrue(gate.get("passed").asBoolean());
        assertEquals("skipped", gate.get("items").get(0).get("status").asText());
    }

    @Test
    void run_withoutGate_noGateNodeAndZeroBehaviorChange()
    {
        // 不注入门禁组件（守护纯 new 老用法）：无 gate 节点，既有通过率字段不变
        String cases = "{\"cases\":[{\"caseId\":\"ok\",\"text\":\"OK\",\"checks\":{"
                + "\"disputeTypeKeywords\":[\"劳动\"]}}]}";
        ObjectNode report = service.run(cases);
        assertFalse(report.has("gate"));
        assertEquals(1, report.get("passedCases").asInt());
    }

    @Test
    void run_withGate_ticketHitJudgedAndPassed()
    {
        // 桩工单标题/片段含"工资/拖欠"，ticketKeywords 命中 → 工单有用率 1.0 ≥ 0.60
        attachGate();
        String cases = "{\"cases\":[{\"caseId\":\"u\",\"text\":\"OK\",\"checks\":{"
                + "\"ticketKeywords\":[\"工资\",\"拖欠\"]}}]}";
        ObjectNode gate = (ObjectNode) service.run(cases).get("gate");
        ObjectNode ticket = (ObjectNode) gate.get("items").get(1);
        assertEquals("ticketUsefulRate", ticket.get("name").asText());
        assertEquals("judged", ticket.get("status").asText());
        assertTrue(ticket.get("passed").asBoolean());
        assertEquals(1.0d, ticket.get("actualRate").asDouble(), 0.0001);
        assertEquals(0.60d, ticket.get("thresholdRate").asDouble(), 0.0001);
        assertTrue(gate.get("passed").asBoolean());
    }

    @Test
    void run_withGate_ticketMiss_gateFailed()
    {
        // 桩工单只含工资/欠薪文本，"押金/租赁"全不命中 → 有用率 0，judged 不通过，总判定 false
        attachGate();
        String cases = "{\"cases\":[{\"caseId\":\"u\",\"text\":\"OK\",\"checks\":{"
                + "\"ticketKeywords\":[\"押金\",\"租赁\"]}}]}";
        ObjectNode gate = (ObjectNode) service.run(cases).get("gate");
        ObjectNode ticket = (ObjectNode) gate.get("items").get(1);
        assertEquals("judged", ticket.get("status").asText());
        assertFalse(ticket.get("passed").asBoolean());
        assertEquals(0d, ticket.get("actualRate").asDouble(), 0.0001);
        assertFalse(gate.get("passed").asBoolean());
    }

    @Test
    void run_withGate_ticketKeywordsButNoTickets_zeroRate()
    {
        // 配了 ticketKeywords 但 assist 无工单推荐：仍计 total=1、passed=0，actualRate=0 判失败
        attachGate();
        String cases = "{\"cases\":[{\"caseId\":\"u\",\"text\":\"NOTICKET\",\"checks\":{"
                + "\"ticketKeywords\":[\"工资\"]}}]}";
        ObjectNode report = service.run(cases);
        ObjectNode gate = (ObjectNode) report.get("gate");
        ObjectNode ticket = (ObjectNode) gate.get("items").get(1);
        assertEquals("judged", ticket.get("status").asText());
        assertEquals(0d, ticket.get("actualRate").asDouble(), 0.0001);
        assertFalse(ticket.get("passed").asBoolean());
        // 该用例的工单检查点已计入全局检查点（1 个且不通过）
        assertEquals(1, report.get("totalChecks").asInt());
        assertEquals(0, report.get("passedChecks").asInt());
    }

    @Test
    void run_withGate_noTicketKeywords_ticketItemSkipped()
    {
        // 用例不配 ticketKeywords → 无工单检查点，工单项 skipped 不参与总判定
        attachGate();
        String cases = "{\"cases\":[{\"caseId\":\"u\",\"text\":\"OK\",\"checks\":{"
                + "\"disputeTypeKeywords\":[\"劳动\"]}}]}";
        ObjectNode gate = (ObjectNode) service.run(cases).get("gate");
        assertTrue(gate.get("passed").asBoolean());
        ObjectNode ticket = (ObjectNode) gate.get("items").get(1);
        assertEquals("ticketUsefulRate", ticket.get("name").asText());
        assertEquals("skipped", ticket.get("status").asText());
    }

    /** 桩 assist：按文本返回不同固定结果 */
    private CopilotAssistService stubAssist()
    {
        return new CopilotAssistService()
        {
            @Override
            public CopilotAssistResult assist(String text, String sessionId)
            {
                CopilotAssistResult r = CopilotAssistResult.empty();
                if ("BAD".equals(text))
                {
                    r.setElementDegraded(true);
                    return r;
                }
                r.setDisputeType("劳动争议");
                r.setUrgency("normal");
                r.setClaims(Arrays.asList("支付拖欠工资"));
                r.setLaws(Arrays.asList(new CopilotLaw(1L, "劳动法相关", "第X条", "law")));
                if ("NOTICKET".equals(text))
                {
                    // N2：配置 ticketKeywords 但无工单推荐，验证空工单计 total 不 passed
                    r.setTickets(java.util.Collections.emptyList());
                }
                else
                {
                    r.setTickets(Arrays.asList(
                            new CopilotTicket(1L, "T001", "工资拖欠工单", "2", "老板拖欠工资内容片段"),
                            new CopilotTicket(2L, "T002", "未签劳动合同咨询", "1", "餐饮店欠薪处理记录")));
                }
                Map<String, Object> payload = new LinkedHashMap<>();
                if ("FORBID".equals(text))
                {
                    payload.put("content", "我们包赢");
                }
                else
                {
                    payload.put("content", "正常内容");
                }
                r.setActions(Arrays.asList(
                        new CopilotAssistResult.SuggestedAction("createTicket", "建单", payload)));
                return r;
            }
        };
    }
}
