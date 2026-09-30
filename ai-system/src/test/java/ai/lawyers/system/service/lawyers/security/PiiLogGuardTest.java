package ai.lawyers.system.service.lawyers.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P3-G1（V2.54）：日志 PII 回潮门禁——N10 止血包已清零 11 处明文手机号日志，
 * 本测试作为静态门禁防止回潮：扫描四个模块主源码中的日志调用行，
 * 出现硬编码 11 位手机号 / 18 位身份证字面量即失败（新增日志纳入审计）。
 *
 * <p>边界：仅扫描 {@code src/main/java}（测试代码与注释不进运行时日志，不扫）；
 * 仅匹配 {@code log.info/warn/error/debug/trace} 调用行中的<b>字面量</b>——
 * 变量拼接请在日志层使用 MaskUtils 脱敏，门禁只兜底最低级的硬编码失误。</p>
 */
class PiiLogGuardTest
{
    /** 硬编码手机号字面量（非 12/13 位连续数字语境，用边界控制） */
    private static final Pattern MOBILE = Pattern.compile("(?<![0-9])1[3-9][0-9]{9}(?![0-9])");

    /** 硬编码 18 位身份证字面量 */
    private static final Pattern ID_CARD = Pattern.compile("(?<![0-9])[0-9]{17}[0-9Xx](?![0-9])");

    /** 日志调用行 */
    private static final Pattern LOG_CALL = Pattern.compile("\\b(log|logger)\\.(info|warn|error|debug|trace)\\s*\\(");

    /** 四个模块主源码根（相对 ai-system 模块工作目录；目录缺失自动跳过） */
    private static final String[] SCAN_ROOTS = {
            "src/main/java",
            "../ai-admin/src/main/java",
            "../ai-framework/src/main/java",
            "../ai-common/src/main/java"
    };

    @Test
    void noHardcodedPiiInLogStatements() throws IOException
    {
        List<String> violations = new ArrayList<>();
        for (String root : SCAN_ROOTS)
        {
            Path dir = Paths.get(root).toAbsolutePath().normalize();
            if (!Files.isDirectory(dir))
            {
                continue;
            }
            try (Stream<Path> files = Files.walk(dir))
            {
                files.filter(p -> p.toString().endsWith(".java")).forEach(p -> scan(p, violations));
            }
        }
        assertTrue(violations.isEmpty(),
                "日志语句存在硬编码 PII 字面量（N10 门禁），请改用脱敏工具：" + System.lineSeparator()
                        + String.join(System.lineSeparator(), violations));
    }

    private void scan(Path file, List<String> violations)
    {
        try
        {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++)
            {
                String trimmed = lines.get(i).trim();
                // 注释行不进运行时日志，跳过以降低误报
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*"))
                {
                    continue;
                }
                if (!LOG_CALL.matcher(trimmed).find())
                {
                    continue;
                }
                if (MOBILE.matcher(trimmed).find())
                {
                    violations.add(file + ":" + (i + 1) + " 命中手机号字面量 → " + trimmed);
                }
                if (ID_CARD.matcher(trimmed).find())
                {
                    violations.add(file + ":" + (i + 1) + " 命中身份证字面量 → " + trimmed);
                }
            }
        }
        catch (IOException e)
        {
            // 单文件不可读不阻断门禁（构建环境临时锁文件等），由其余文件兜底
        }
    }
}
