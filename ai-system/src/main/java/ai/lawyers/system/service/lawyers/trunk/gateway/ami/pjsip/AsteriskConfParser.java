package ai.lawyers.system.service.lawyers.trunk.gateway.ami.pjsip;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P3-B5：Asterisk 配置文件（{@code pjsip.conf} / {@code queues.conf}）解析器。
 *
 * <p>纯静态、无状态，仅做 INI 风格语法解析，不理解任何模块语义（语义组装在
 * {@link PjsipConfigQueryService}）：</p>
 * <ul>
 *   <li>{@code [section]} 开始新配置节；节外的键值行忽略（与 Asterisk 一致）；</li>
 *   <li>键值分隔符同时支持 {@code =}（pjsip.conf）与 {@code =>}（queues.conf 老式语法）；</li>
 *   <li>同键可重复出现（{@code member=}、{@code contact=}、{@code allow=}），
 *       值按出现顺序全部保留；</li>
 *   <li>注释：整行 {@code ;} 注释；行内 {@code ;} 起为注释（与 Asterisk {@code ast_config}
 *       语义一致——配置 URI 中写 {@code ;transport=} 也会被真实 PBX 截断，行为对齐）；
 *       行首 {@code #} 视为注释，但 {@code #include} 指令保留给上层处理；行内 {@code #}
 *       绝不截断（分机/特征码中合法存在）；</li>
 *   <li>容忍 UTF-8 BOM 与键值两侧空白。</li>
 * </ul>
 *
 * @author ai-lawyers
 */
public final class AsteriskConfParser
{
    private AsteriskConfParser() {}

    /**
     * 单个配置节：节名 + 有序键值（同键多值保留）。
     */
    public static final class Section
    {
        private final String name;
        private final Map<String, List<String>> values = new LinkedHashMap<>();

        Section(String name)
        {
            this.name = name;
        }

        public String getName()
        {
            return name;
        }

        void add(String key, String value)
        {
            values.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }

        /** 取该键第一个值；不存在返回 null */
        public String first(String key)
        {
            List<String> list = values.get(key);
            return list == null || list.isEmpty() ? null : list.get(0);
        }

        /** 取该键全部值（可空列表，永不返回 null） */
        public List<String> all(String key)
        {
            return values.getOrDefault(key, Collections.emptyList());
        }
    }

    /**
     * 解析完整配置文本。
     *
     * @param reader 配置文本（调用方负责关闭）
     * @return 按出现顺序的配置节列表
     */
    public static List<Section> parse(Reader reader) throws IOException
    {
        List<Section> sections = new ArrayList<>();
        Section current = null;
        try (BufferedReader br = new BufferedReader(reader))
        {
            String raw;
            while ((raw = br.readLine()) != null)
            {
                // 容忍 UTF-8 BOM（Files.readAllLines 不剥离 BOM）
                String line = stripComment(raw.replace("\uFEFF", ""));
                if (line == null)
                {
                    continue;
                }
                line = line.trim();
                if (line.isEmpty())
                {
                    continue;
                }
                if (line.charAt(0) == '[')
                {
                    int end = line.indexOf(']');
                    String name = end > 1 ? line.substring(1, end).trim() : line.substring(1).trim();
                    current = new Section(name);
                    sections.add(current);
                    continue;
                }
                if (current == null)
                {
                    continue; // 节外键值忽略
                }
                int arrow = line.indexOf("=>");
                int eq;
                int valueStart;
                if (arrow >= 0)
                {
                    // indexOf('=') 必然在 "=>" 内命中，故 => 优先判定不与 eq 比较
                    eq = arrow;
                    valueStart = arrow + 2;
                }
                else
                {
                    eq = line.indexOf('=');
                    if (eq < 0)
                    {
                        continue; // 无分隔符的非法行忽略
                    }
                    valueStart = eq + 1;
                }
                String key = line.substring(0, eq).trim();
                String value = line.substring(valueStart).trim();
                if (!key.isEmpty())
                {
                    current.add(key, value);
                }
            }
        }
        return sections;
    }

    /**
     * 剥离注释：返回去注释后的行内容；整行注释返回 null。
     */
    private static String stripComment(String raw)
    {
        String trimmed = raw.trim();
        if (trimmed.startsWith(";"))
        {
            return null;
        }
        // 行首 #：除 #include 指令外按注释处理（宽容手工配置）
        if (trimmed.startsWith("#") && !trimmed.startsWith("#include"))
        {
            return null;
        }
        int sc = raw.indexOf(';');
        return sc >= 0 ? raw.substring(0, sc) : raw;
    }
}
