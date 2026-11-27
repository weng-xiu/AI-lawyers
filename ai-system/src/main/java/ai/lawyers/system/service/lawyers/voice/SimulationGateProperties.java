package ai.lawyers.system.service.lawyers.voice;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * N2（语雀 7.4）：仿真评测灰度准入门禁阈值（application.yml 的 ai.simulation.gate 前缀）。
 *
 * <p>三阶段量化门槛中，当前仿真链路已有数据可量的两项先做配置化判定：
 * S3 机器人意图准确率、S2 Copilot 法条命中率；S2 工单有用率因仿真尚无对应检查点，
 * 仅预留阈值字段、本次不参与判定（待工单检查点切片启用）。所有比率为 0~1 量纲，
 * setter 钳制到 [0,1]，非法配置不会放大成异常值。</p>
 *
 * <pre>
 * ai:
 *   simulation:
 *     gate:
 *       intent-accuracy: 0.85
 *       law-hit-rate: 0.70
 *       ticket-useful-rate: 0.60
 * </pre>
 *
 * @author ai-lawyers
 */
@Component
@ConfigurationProperties(prefix = "ai.simulation.gate")
public class SimulationGateProperties
{
    /** S3（语雀 7.4 第三阶段）：机器人意图准确率门槛，取机器人仿真用例通过率，默认 0.85 */
    private double intentAccuracy = 0.85d;

    /** S2（语雀 7.4 第二阶段）：Copilot 法条推荐命中率门槛，默认 0.70 */
    private double lawHitRate = 0.70d;

    /** S2：相似工单 Top3 有用率门槛（预留，当前仿真无工单检查点，不参与判定），默认 0.60 */
    private double ticketUsefulRate = 0.60d;

    public double getIntentAccuracy()
    {
        return intentAccuracy;
    }

    public void setIntentAccuracy(double intentAccuracy)
    {
        this.intentAccuracy = clamp(intentAccuracy);
    }

    public double getLawHitRate()
    {
        return lawHitRate;
    }

    public void setLawHitRate(double lawHitRate)
    {
        this.lawHitRate = clamp(lawHitRate);
    }

    public double getTicketUsefulRate()
    {
        return ticketUsefulRate;
    }

    public void setTicketUsefulRate(double ticketUsefulRate)
    {
        this.ticketUsefulRate = clamp(ticketUsefulRate);
    }

    /** 比率钳制到 [0,1]，容错非法配置 */
    private static double clamp(double v)
    {
        if (v < 0d)
        {
            return 0d;
        }
        if (v > 1d)
        {
            return 1d;
        }
        return v;
    }
}
