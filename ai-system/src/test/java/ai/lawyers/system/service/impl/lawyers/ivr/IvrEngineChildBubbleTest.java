package ai.lawyers.system.service.impl.lawyers.ivr;

import java.util.List;

import org.junit.jupiter.api.Test;

import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W3 IVR 子流程测试：hangup/transfer 终止语义冒泡终止父流程（T1-3/L1 修复回归）。
 *
 * <p>说明：父结果只包含父流程自身的节点步骤（executeChild 不合并子流程 steps），
 * 子流程执行痕迹通过 childResult 变量与 transferTarget 上抛体现。</p>
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>子流程内 hangup → 父流程立即终止，父后续节点不执行，childResult 记录 terminated=hangup；</li>
 *   <li>子流程内 transfer → 父流程终止且 transferTarget 上抛；</li>
 *   <li>子流程正常结束（无终止标记）→ 父流程继续执行后续节点；</li>
 *   <li>嵌套深度超过 MAX_CHILD_DEPTH(3) → 跳过执行不递归（防循环引用爆栈）。</li>
 * </ul>
 *
 * @author ai-lawyers
 */
class IvrEngineChildBubbleTest extends IvrEngineTestBase
{
    /**
     * 子流程 hangup → 父流程立即终止，父流程 say/hangup 不再执行。
     */
    @Test
    void childHangup_terminatesParentFlow()
    {
        // 父流程：start(101) → child(102, flowId=2000) → say(103) → hangup(104)
        AiIvrFlow parent = flow(1000L, "父流程");
        registerFlow(parent,
                nodes(
                        node(101L, 1000L, "start", "开始", null),
                        node(102L, 1000L, "child", "转子流程", "{\"flowId\":2000}"),
                        node(103L, 1000L, "say", "父播报", "{\"text\":\"不应该被播报\"}"),
                        node(104L, 1000L, "hangup", "父挂断", null)),
                edges(
                        edge(1L, 1000L, 101L, 102L, null, null),
                        edge(2L, 1000L, 102L, 103L, null, null),
                        edge(3L, 1000L, 103L, 104L, null, null)));

        // 子流程：start(201) → say(202) → hangup(203)
        AiIvrFlow child = flow(2000L, "子流程");
        registerFlow(child,
                nodes(
                        node(201L, 2000L, "start", "子开始", null),
                        node(202L, 2000L, "say", "子播报", "{\"text\":\"您好\"}"),
                        node(203L, 2000L, "hangup", "子挂断", null)),
                edges(
                        edge(11L, 2000L, 201L, 202L, null, null),
                        edge(12L, 2000L, 202L, 203L, null, null)));

        IvrExecuteResult result = run(parent);

        assertThat(result.isSuccess()).isTrue();
        // 冒泡：父流程只执行了 start 与 child 两个节点，say(103)/hangup(104) 必须未执行
        List<IvrNodeStep> steps = result.getSteps();
        assertThat(steps).extracting(IvrNodeStep::getNodeId).containsExactly(101L, 102L);
        assertThat(stepsOf(result, 103L, "PLAY"))
                .as("子流程挂断后父流程后续节点必须终止").isEmpty();
        assertThat(stepsOf(result, 104L, "HANGUP")).isEmpty();
        // childResult 记录终止语义，内部终止标记被清除
        assertThat(String.valueOf(result.getVariables().get("childResult")))
                .contains("success=true").contains("terminated=hangup");
        assertThat(result.getVariables()).doesNotContainKey("__terminated");
    }

    /**
     * 子流程 transfer → 父流程终止且 transferTarget 上抛到顶层结果。
     */
    @Test
    void childTransfer_propagatesTargetAndTerminatesParent()
    {
        // 父流程：start(101) → child(102, flowId=2100) → say(103) → hangup(104)
        AiIvrFlow parent = flow(1000L, "父流程");
        registerFlow(parent,
                nodes(
                        node(101L, 1000L, "start", "开始", null),
                        node(102L, 1000L, "child", "转子流程", "{\"flowId\":2100}"),
                        node(103L, 1000L, "say", "父播报", "{\"text\":\"不应该被播报\"}"),
                        node(104L, 1000L, "hangup", "父挂断", null)),
                edges(
                        edge(1L, 1000L, 101L, 102L, null, null),
                        edge(2L, 1000L, 102L, 103L, null, null),
                        edge(3L, 1000L, 103L, 104L, null, null)));

        // 子流程：start(211) → transfer(212, 外线号码)
        AiIvrFlow child = flow(2100L, "转接子流程");
        registerFlow(child,
                nodes(
                        node(211L, 2100L, "start", "子开始", null),
                        node(212L, 2100L, "transfer", "转外线", "{\"number\":\"07551112222\"}")),
                edges(
                        edge(11L, 2100L, 211L, 212L, null, null)));

        IvrExecuteResult result = run(parent);

        assertThat(result.getTransferTarget()).as("转接目标必须上抛").isEqualTo("07551112222");
        assertThat(stepsOf(result, 103L, "PLAY")).isEmpty();
        assertThat(stepsOf(result, 104L, "HANGUP")).isEmpty();
        // child 节点 step 明确记录冒泡终止语义
        assertThat(stepsOf(result, 102L, "CHILD").get(0).getDetail()).contains("冒泡终止父流程");
        assertThat(String.valueOf(result.getVariables().get("childResult"))).contains("terminated=transfer");
    }

    /**
     * 子流程正常结束（无 hangup/transfer）→ 父流程继续执行后续节点。
     */
    @Test
    void childNormalCompletion_parentContinues()
    {
        // 父流程：start(101) → child(102, flowId=2200) → say(103) → hangup(104)
        AiIvrFlow parent = flow(1000L, "父流程");
        registerFlow(parent,
                nodes(
                        node(101L, 1000L, "start", "开始", null),
                        node(102L, 1000L, "child", "转子流程", "{\"flowId\":2200}"),
                        node(103L, 1000L, "say", "父播报", "{\"text\":\"欢迎致电\"}"),
                        node(104L, 1000L, "hangup", "父挂断", null)),
                edges(
                        edge(1L, 1000L, 101L, 102L, null, null),
                        edge(2L, 1000L, 102L, 103L, null, null),
                        edge(3L, 1000L, 103L, 104L, null, null)));

        // 子流程：start(221) → say(222)，say 无出线 → 子流程自然结束
        AiIvrFlow child = flow(2200L, "普通子流程");
        registerFlow(child,
                nodes(
                        node(221L, 2200L, "start", "子开始", null),
                        node(222L, 2200L, "say", "子播报", "{\"text\":\"子流程播报\"}")),
                edges(
                        edge(11L, 2200L, 221L, 222L, null, null)));

        IvrExecuteResult result = run(parent);

        assertThat(result.isSuccess()).isTrue();
        // 父流程继续：say(103) 执行、hangup(104) 挂断
        assertThat(stepsOf(result, 103L, "PLAY")).hasSize(1);
        assertThat(stepsOf(result, 104L, "HANGUP")).hasSize(1);
        assertThat(String.valueOf(result.getVariables().get("childResult")))
                .contains("success=true").contains("terminated=");
    }

    /**
     * 嵌套深度超过 MAX_CHILD_DEPTH=3 → 跳过执行，不因流程循环引用递归爆栈。
     * （f1000→f1001→f1002→f1003→(f1001) 构成循环引用，第 4 层 child 触发深度上限）
     */
    @Test
    void childDepthLimit_noStackOverflowOnCycle()
    {
        // 父流程 f1000: start(101) → child(102→f1001) → hangup(103)
        AiIvrFlow f1 = flow(1000L, "层级1");
        registerFlow(f1,
                nodes(
                        node(101L, 1000L, "start", "开始", null),
                        node(102L, 1000L, "child", "下钻", "{\"flowId\":1001}"),
                        node(103L, 1000L, "hangup", "挂断", null)),
                edges(
                        edge(1L, 1000L, 101L, 102L, null, null),
                        edge(2L, 1000L, 102L, 103L, null, null)));
        // f1001: start(201) → child(202→f1002) → hangup(203)
        AiIvrFlow f2 = flow(1001L, "层级2");
        registerFlow(f2,
                nodes(
                        node(201L, 1001L, "start", "开始", null),
                        node(202L, 1001L, "child", "下钻", "{\"flowId\":1002}"),
                        node(203L, 1001L, "hangup", "挂断", null)),
                edges(
                        edge(1L, 1001L, 201L, 202L, null, null),
                        edge(2L, 1001L, 202L, 203L, null, null)));
        // f1002: start(301) → child(302→f1003) → hangup(303)
        AiIvrFlow f3 = flow(1002L, "层级3");
        registerFlow(f3,
                nodes(
                        node(301L, 1002L, "start", "开始", null),
                        node(302L, 1002L, "child", "下钻", "{\"flowId\":1003}"),
                        node(303L, 1002L, "hangup", "挂断", null)),
                edges(
                        edge(1L, 1002L, 301L, 302L, null, null),
                        edge(2L, 1002L, 302L, 303L, null, null)));
        // f1003: start(401) → child(402→f1001 循环引用！) → hangup(403)
        AiIvrFlow f4 = flow(1003L, "层级4-循环引用");
        registerFlow(f4,
                nodes(
                        node(401L, 1003L, "start", "开始", null),
                        node(402L, 1003L, "child", "回环", "{\"flowId\":1001}"),
                        node(403L, 1003L, "hangup", "挂断", null)),
                edges(
                        edge(1L, 1003L, 401L, 402L, null, null),
                        edge(2L, 1003L, 402L, 403L, null, null)));

        IvrExecuteResult result = run(f1);

        // 第 4 层 child 因深度=3 >= MAX_CHILD_DEPTH 被跳过：不递归、不爆栈、不触发 LOOP_LIMIT，
        // 全链路按终止语义正常收口（最深层 hangup 标记逐级冒泡，父流程自身 hangup 不再执行）。
        // 若无深度守卫，f1001→f1002→f1003→f1001 循环引用将无限递归，
        // StackOverflowError 属 Error 不被 executeChild 的 catch(Exception) 捕获，会直接上抛导致本测试失败。
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCode()).isEqualTo("OK");
        assertThat(result.getSteps()).extracting(IvrNodeStep::getAction)
                .as("父流程仅执行 start 与 child，随后冒泡终止")
                .containsExactly("START", "CHILD");
    }

    private List<IvrNodeStep> stepsOf(IvrExecuteResult result, long nodeId, String action)
    {
        return result.getSteps().stream()
                .filter(s -> s.getNodeId() != null && s.getNodeId() == nodeId
                        && action.equals(s.getAction()))
                .collect(java.util.stream.Collectors.toList());
    }
}
