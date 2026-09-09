package ai.lawyers.system.service.impl.lawyers.ivr;

import org.junit.jupiter.api.Test;

import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W3 IVR 超时/循环兜底测试（T1-2 回归）：
 * 流程总超时兜底挂断、最大步数防循环，均必须"快速失败、不卡死呼叫线程"。
 *
 * @author ai-lawyers
 */
class IvrEngineTimeoutTest extends IvrEngineTestBase
{
    /** 正常三节点流程：start → say → hangup */
    private AiIvrFlow normalFlow()
    {
        AiIvrFlow flow = flow(1000L, "正常流程");
        registerFlow(flow,
                nodes(
                        node(101L, 1000L, "start", "开始", null),
                        node(102L, 1000L, "say", "欢迎", "{\"text\":\"您好，请讲\"}"),
                        node(103L, 1000L, "hangup", "挂断", null)),
                edges(
                        edge(1L, 1000L, 101L, 102L, null, null),
                        edge(2L, 1000L, 102L, 103L, null, null)));
        return flow;
    }

    /**
     * 流程总超时：deadline 已过 → 记录 TIMEOUT 步骤、FLOW_ERROR 兜底终止（语义=挂断），不卡死。
     */
    @Test
    void flowTimeout_failsFastWithTimeoutStep()
    {
        setField("flowTimeoutMs", -1L); // deadline 立即过期，模拟慢 AI/长流程占用超限

        IvrExecuteResult result = run(normalFlow());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo("FLOW_TIMEOUT");
        assertThat(result.getStatus()).isEqualTo("2");
        assertThat(result.getSteps()).hasSize(1);
        assertThat(result.getSteps().get(0).getAction()).isEqualTo("TIMEOUT");
        assertThat(result.getSteps().get(0).getDetail()).contains("流程总超时");
    }

    /**
     * 超时窗口充足时同一流程正常执行（对照组：兜底逻辑不误伤正常流程）。
     */
    @Test
    void normalFlow_withinTimeout_completes()
    {
        setField("flowTimeoutMs", 180000L);

        IvrExecuteResult result = run(normalFlow());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCode()).isEqualTo("OK");
        assertThat(result.getSteps()).extracting(s -> s.getAction())
                .containsExactly("START", "PLAY", "HANGUP");
    }

    /**
     * 流程自环（start→start）：MAX_STEPS 兜底 LOOP_LIMIT，不无限执行。
     */
    @Test
    void selfLoop_boundedByMaxSteps()
    {
        AiIvrFlow flow = flow(1100L, "自环流程");
        registerFlow(flow,
                nodes(node(111L, 1100L, "start", "开始", null)),
                edges(edge(1L, 1100L, 111L, 111L, null, null)));

        IvrExecuteResult result = run(flow);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo("LOOP_LIMIT");
        assertThat(result.getStatus()).isEqualTo("2");
        assertThat(result.getSteps()).as("恰好执行 MAX_STEPS(200) 步后兜底").hasSize(200);
        assertThat(result.getMessage()).contains("最大执行步数");
    }
}
