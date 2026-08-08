package ai.lawyers.system.service.lawyers.trunk.gateway;

import java.io.Serializable;

/**
 * 网关健康探测结果
 */
public class GatewayHealth implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 是否可达 */
    private boolean reachable;

    /** 探测耗时(ms) */
    private long latencyMs;

    /** 网关侧上报的注册状态，如 REGED / NOREG / UNKNOWN */
    private String registerState;

    /** 失败原因 */
    private String message;

    public static GatewayHealth up(long latencyMs)
    {
        GatewayHealth h = new GatewayHealth();
        h.reachable = true;
        h.latencyMs = latencyMs;
        h.registerState = "REGED";
        return h;
    }

    public static GatewayHealth down(String message)
    {
        GatewayHealth h = new GatewayHealth();
        h.reachable = false;
        h.message = message;
        h.registerState = "NOREG";
        return h;
    }

    public boolean isReachable() { return reachable; }

    public void setReachable(boolean reachable) { this.reachable = reachable; }

    public long getLatencyMs() { return latencyMs; }

    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }

    public String getRegisterState() { return registerState; }

    public void setRegisterState(String registerState) { this.registerState = registerState; }

    public String getMessage() { return message; }

    public void setMessage(String message) { this.message = message; }
}
