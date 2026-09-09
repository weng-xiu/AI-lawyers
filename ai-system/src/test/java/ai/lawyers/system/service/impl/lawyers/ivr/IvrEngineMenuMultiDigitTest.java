package ai.lawyers.system.service.impl.lawyers.ivr;

import java.util.List;

import org.junit.jupiter.api.Test;

import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W3 IVR menu 多位按键测试（T1-4 回归）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>maxDigits=4 + endKey="#"：输入 "8001#" 整体匹配分机号 8001（核心场景）；</li>
 *   <li>maxDigits=1（默认）：旧行为不变，仅取首位；</li>
 *   <li>无结束键输入超长：按 maxDigits 截断；</li>
 *   <li>无输入：走默认连线不挂死。</li>
 * </ul>
 *
 * @author ai-lawyers
 */
class IvrEngineMenuMultiDigitTest extends IvrEngineTestBase
{
    /** 父流程骨架：start(101) → menu(102) → 分机接转 hangup(103) / 默认 hangup(104) */
    private AiIvrFlow registerMenuFlow(String menuConfig)
    {
        AiIvrFlow flow = flow(1000L, "话务菜单");
        registerFlow(flow,
                nodes(
                        node(101L, 1000L, "start", "开始", null),
                        node(102L, 1000L, "menu", "服务菜单", menuConfig),
                        node(103L, 1000L, "hangup", "分机接转", null),
                        node(104L, 1000L, "hangup", "默认挂断", null)),
                edges(
                        edge(1L, 1000L, 101L, 102L, null, null),
                        edge(2L, 1000L, 102L, 103L, "8001", null),
                        edge(3L, 1000L, 102L, 104L, null, null)));
        return flow;
    }

    /**
     * 核心场景：maxDigits=4 + endKey=#，输入 "8001#" 去掉结束键后整体匹配分机 8001。
     */
    @Test
    void menu_fourDigits_endKey_strippedAndMatched()
    {
        AiIvrFlow flow = registerMenuFlow("{\"maxDigits\":4,\"endKey\":\"#\"}");

        IvrExecuteResult result = run(flow, "8001#");

        assertThat(result.getVariables().get("dtmf")).as("应去掉 # 保留完整分机号").isEqualTo("8001");
        assertThat(lastHangupName(result)).isEqualTo("分机接转");
    }

    /**
     * 兼容旧行为：maxDigits=1（默认）时多位输入仅取首位。
     */
    @Test
    void menu_singleDigit_legacyBehavior()
    {
        AiIvrFlow flow = registerMenuFlow("{\"maxDigits\":1}");

        IvrExecuteResult result = run(flow, "8001#");

        assertThat(result.getVariables().get("dtmf")).as("旧模式仅取首个按键").isEqualTo("8");
        // dtmf=8 无对应连线 → 默认挂断
        assertThat(lastHangupName(result)).isEqualTo("默认挂断");
    }

    /**
     * 无结束键的超长输入按 maxDigits 截断（如银行式卡号输入）。
     */
    @Test
    void menu_overLengthInput_truncatedToMaxDigits()
    {
        // 未配置 endKey 时默认 "#"；输入无 # 则整体截断到 4 位
        AiIvrFlow flow = registerMenuFlow("{\"maxDigits\":4}");

        IvrExecuteResult result = run(flow, "123456");

        assertThat(result.getVariables().get("dtmf")).isEqualTo("1234");
        assertThat(lastHangupName(result)).as("1234 无匹配连线 → 默认").isEqualTo("默认挂断");
    }

    /**
     * 无任何输入：dtmf 为空串，沿默认连线继续，流程正常终止不挂死。
     */
    @Test
    void menu_noInput_routesDefault()
    {
        AiIvrFlow flow = registerMenuFlow("{\"maxDigits\":4,\"endKey\":\"#\"}");

        IvrExecuteResult result = run(flow);

        assertThat(result.getVariables().get("dtmf")).isEqualTo("");
        assertThat(result.isSuccess()).isTrue();
        assertThat(lastHangupName(result)).isEqualTo("默认挂断");
    }

    private String lastHangupName(IvrExecuteResult result)
    {
        List<IvrNodeStep> hangups = result.getSteps().stream()
                .filter(s -> "HANGUP".equals(s.getAction()))
                .collect(java.util.stream.Collectors.toList());
        assertThat(hangups).as("流程必须以挂断终止").isNotEmpty();
        return hangups.get(hangups.size() - 1).getNodeName();
    }
}
