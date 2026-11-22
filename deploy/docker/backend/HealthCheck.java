import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * C2/C5：backend 容器 readiness 健康检查（Java 17 单文件源码运行，无需 javac/第三方包）。
 * 探测独立管理端口（容器内 8081，见 MANAGEMENT_SERVER_PORT）的 /actuator/health/readiness：
 * HTTP 200 且响应体含 "UP" 退出 0，否则退出 1。readiness 组含 readinessState + drain，
 * 排空时自动 OUT_OF_SERVICE，配合负载均衡摘流量。
 * 用法：java /app/HealthCheck.java
 */
public class HealthCheck {
    public static void main(String[] args) {
        String url = System.getenv().getOrDefault("HEALTHCHECK_URL",
                "http://127.0.0.1:8081/actuator/health/readiness");
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            boolean healthy = resp.statusCode() == 200 && resp.body() != null && resp.body().contains("\"status\":\"UP\"");
            System.exit(healthy ? 0 : 1);
        } catch (Exception e) {
            System.exit(1);
        }
    }
}
