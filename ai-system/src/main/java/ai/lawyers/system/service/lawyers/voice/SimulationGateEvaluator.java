package ai.lawyers.system.service.lawyers.voice;

import java.util.List;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * N2（语雀 7.4）：仿真评测准入门禁判定与报告拼装。
 *
 * <p>把各仿真服务算出的实测比率与 {@link SimulationGateProperties} 的门槛逐项比对，
 * 统一产出 gate 报告块：judged 项给出 actualRate/thresholdRate/passed，skipped 项
 * （暂无数据/未启用的检查点）只记录原因、不参与总判定；顶层 passed 仅当全部 judged
 * 项通过才为 true，无 judged 项时为 true。门禁只做报告判定，不抛异常、不阻断异步任务。</p>
 *
 * @author ai-lawyers
 */
@Component
public class SimulationGateEvaluator
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 构造一个参与判定的检查项（actual ≥ threshold 即通过） */
    public GateCheck judged(String name, String stage, double actualRate, double thresholdRate)
    {
        GateCheck c = new GateCheck();
        c.name = name;
        c.stage = stage;
        c.actualRate = round3(actualRate);
        c.thresholdRate = round3(thresholdRate);
        c.skipped = false;
        c.direction = "up";
        c.passed = c.actualRate >= c.thresholdRate;
        return c;
    }

    /**
     * 构造一个「越低越好」的判定检查项（actual ≤ threshold 即通过），用于 CER 等错误率门槛。
     * 与 {@link #judged} 方向相反，报告 item 额外输出 direction=down 以示区别。
     */
    public GateCheck judgedMax(String name, String stage, double actualRate, double thresholdRate)
    {
        GateCheck c = new GateCheck();
        c.name = name;
        c.stage = stage;
        c.actualRate = round3(actualRate);
        c.thresholdRate = round3(thresholdRate);
        c.skipped = false;
        c.direction = "down";
        c.passed = c.actualRate <= c.thresholdRate;
        return c;
    }

    /** 构造一个暂不判定的检查项（无数据/检查点未实现），不参与总判定 */
    public GateCheck skipped(String name, String stage, String reason)
    {
        GateCheck c = new GateCheck();
        c.name = name;
        c.stage = stage;
        c.skipped = true;
        c.reason = reason;
        return c;
    }

    /** 拼装 gate 报告块 */
    public ObjectNode buildGate(List<GateCheck> checks)
    {
        ObjectNode gate = MAPPER.createObjectNode();
        ArrayNode items = gate.putArray("items");
        boolean allPassed = true;
        if (checks != null)
        {
            for (GateCheck c : checks)
            {
                ObjectNode item = items.addObject();
                item.put("name", c.name);
                item.put("stage", c.stage);
                if (c.skipped)
                {
                    item.put("status", "skipped");
                    item.put("reason", c.reason);
                }
                else
                {
                    item.put("status", "judged");
                    item.put("actualRate", c.actualRate);
                    item.put("thresholdRate", c.thresholdRate);
                    // 仅「越低越好」项输出 direction，保持旧 up 项报告结构不变
                    if ("down".equals(c.direction))
                    {
                        item.put("direction", c.direction);
                    }
                    item.put("passed", c.passed);
                    if (!c.passed)
                    {
                        allPassed = false;
                    }
                }
            }
        }
        gate.put("passed", allPassed);
        return gate;
    }

    /** 比率保留 3 位小数（0~1 量纲） */
    private static double round3(double v)
    {
        return Math.round(v * 1000d) / 1000d;
    }

    /** 单个门禁检查项（内部值对象，工厂方法构造） */
    public static class GateCheck
    {
        private String name;

        private String stage;

        private boolean skipped;

        private boolean passed;

        /** 判定方向：up=越高越好（默认），down=越低越好（CER 等错误率） */
        private String direction = "up";

        private double actualRate;

        private double thresholdRate;

        private String reason;
    }
}
