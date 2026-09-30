package ai.lawyers.system.service.lawyers.trunk.gateway.ami.pjsip;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.trunk.PjsipEndpoint;
import ai.lawyers.system.domain.lawyers.trunk.PjsipQueue;
import ai.lawyers.system.service.lawyers.trunk.gateway.ami.pjsip.AsteriskConfParser.Section;

/**
 * P3-B5：PJSIP 端点 / 呼叫队列只读查询服务。
 *
 * <p>数据源为 Asterisk 配置目录（{@code call.gateway.asterisk.config-dir}，默认
 * {@code /etc/asterisk}）中的 {@code pjsip.conf} 与 {@code queues.conf}，支持
 * {@code #include} 展开（相对路径与 {@code dir/*.conf} 文件层通配，递归深度 3 封顶、
 * 重复包含去重）。平台<b>只解析、只展示，不下发任何配置</b>。</p>
 *
 * <p>可靠性：</p>
 * <ul>
 *   <li>配置目录/文件不存在（如开发机无 Asterisk）→ 返回空结果，summary 标注
 *       {@code exists=false}，不抛异常；</li>
 *   <li>读取/解析异常不向接口扩散（监控接口不 500），summary 携带 {@code error}，
 *       且不写缓存、下次请求自动重试；</li>
 *   <li>按"解析涉及文件的 mtime 快照"缓存：任一文件修改才重新解析。</li>
 * </ul>
 *
 * @author ai-lawyers
 */
@Service
public class PjsipConfigQueryService
{
    private static final Logger log = LoggerFactory.getLogger(PjsipConfigQueryService.class);

    private static final String PJSIP_CONF = "pjsip.conf";
    private static final String QUEUES_CONF = "queues.conf";

    /** #include 递归深度上限（根文件为 0） */
    private static final int MAX_INCLUDE_DEPTH = 3;

    /** P3-B5：Asterisk 配置目录（后端同机或挂载该目录） */
    @Value("${call.gateway.asterisk.config-dir:/etc/asterisk}")
    private String configDir;

    /** 最近一次成功解析快照；null=尚未成功加载 */
    private volatile Snapshot snapshot;

    public List<PjsipEndpoint> listEndpoints()
    {
        return load().endpoints;
    }

    public List<PjsipQueue> listQueues()
    {
        return load().queues;
    }

    public Map<String, Object> summary()
    {
        return load().summary;
    }

    // ---------------------------------------------------------------- 加载与缓存

    /** 单次加载结果（不可变） */
    private static final class Snapshot
    {
        final Map<String, Long> fileMtimes;
        final List<PjsipEndpoint> endpoints;
        final List<PjsipQueue> queues;
        final Map<String, Object> summary;

        Snapshot(Map<String, Long> fileMtimes, List<PjsipEndpoint> endpoints,
                List<PjsipQueue> queues, Map<String, Object> summary)
        {
            this.fileMtimes = fileMtimes;
            this.endpoints = endpoints;
            this.queues = queues;
            this.summary = summary;
        }
    }

    private synchronized Snapshot load()
    {
        Path dir = Paths.get(configDir);
        Path pjsipRoot = dir.resolve(PJSIP_CONF);
        Path queuesRoot = dir.resolve(QUEUES_CONF);

        Snapshot cur = this.snapshot;
        if (cur != null && upToDate(cur, pjsipRoot, queuesRoot))
        {
            return cur;
        }
        try
        {

            Set<Path> pjsipFiles = new LinkedHashSet<>();
            collectFiles(pjsipRoot, pjsipFiles, 0);
            Set<Path> queueFiles = new LinkedHashSet<>();
            collectFiles(queuesRoot, queueFiles, 0);

            List<PjsipEndpoint> endpoints = buildEndpoints(parseAll(pjsipFiles));
            List<PjsipQueue> queues = buildQueues(parseAll(queueFiles));

            Set<Path> allFiles = new LinkedHashSet<>();
            allFiles.addAll(pjsipFiles);
            allFiles.addAll(queueFiles);
            Map<String, Long> mtimes = new LinkedHashMap<>();
            List<String> included = new ArrayList<>();
            for (Path f : allFiles)
            {
                Path abs = f.toAbsolutePath().normalize();
                mtimes.put(abs.toString(), mtimeOf(f));
                included.add(abs.toString());
            }
            Collections.sort(included);

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("configDir", dir.toAbsolutePath().normalize().toString());
            summary.put("pjsipExists", Files.isRegularFile(pjsipRoot));
            summary.put("queuesExists", Files.isRegularFile(queuesRoot));
            summary.put("endpointCount", endpoints.size());
            summary.put("queueCount", queues.size());
            summary.put("includedFiles", included);
            summary.put("loadedAt", System.currentTimeMillis());

            Snapshot next = new Snapshot(mtimes, endpoints, queues, summary);
            this.snapshot = next;
            return next;
        }
        catch (Exception e)
        {
            // 监控类接口不因配置问题 500；不缓存错误态，下次请求重试
            log.warn("[PJSIP-B5] 配置解析失败 dir={} err={}", configDir, e.getMessage());
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("configDir", configDir);
            summary.put("endpointCount", 0);
            summary.put("queueCount", 0);
            summary.put("includedFiles", Collections.emptyList());
            summary.put("error", e.getMessage());
            return new Snapshot(Collections.emptyMap(), Collections.emptyList(),
                    Collections.emptyList(), summary);
        }
    }

    /** 快照涉及的文件 mtime 是否全部未变；两个根文件始终校验（覆盖"空快照后新增根文件"） */
    private boolean upToDate(Snapshot snap, Path pjsipRoot, Path queuesRoot)
    {
        long pjsipRecorded = snap.fileMtimes.getOrDefault(
                pjsipRoot.toAbsolutePath().normalize().toString(), -1L);
        if (mtimeOf(pjsipRoot) != pjsipRecorded)
        {
            return false;
        }
        long queuesRecorded = snap.fileMtimes.getOrDefault(
                queuesRoot.toAbsolutePath().normalize().toString(), -1L);
        if (mtimeOf(queuesRoot) != queuesRecorded)
        {
            return false;
        }
        for (Map.Entry<String, Long> e : snap.fileMtimes.entrySet())
        {
            if (mtimeOf(Paths.get(e.getKey())) != e.getValue())
            {
                return false;
            }
        }
        return true;
    }

    private static long mtimeOf(Path p)
    {
        try
        {
            return Files.isRegularFile(p) ? Files.getLastModifiedTime(p).toMillis() : -1L;
        }
        catch (IOException e)
        {
            return -1L;
        }
    }

    // ---------------------------------------------------------------- #include 展开

    /**
     * 收集配置文件及其 {@code #include} 链上的全部文件（去重、深度限制）。
     * 根文件不存在时静默返回空集合（离线/无 Asterisk 场景）。
     */
    private void collectFiles(Path file, Set<Path> seen, int depth) throws IOException
    {
        if (file == null || depth > MAX_INCLUDE_DEPTH || !Files.isRegularFile(file))
        {
            return;
        }
        if (!seen.add(file.toAbsolutePath().normalize()))
        {
            return; // 已包含（防环/防重复）
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        Path base = file.toAbsolutePath().getParent();
        for (String raw : lines)
        {
            String t = raw.trim();
            if (!t.startsWith("#include")
                    || (t.length() > 8 && !Character.isWhitespace(t.charAt(8))))
            {
                continue;
            }
            String target = t.length() > 8 ? t.substring(8).trim() : "";
            // 容忍引号
            if (target.length() >= 2 && target.startsWith("\"") && target.endsWith("\""))
            {
                target = target.substring(1, target.length() - 1);
            }
            if (target.isEmpty())
            {
                continue;
            }
            if (target.indexOf('*') >= 0 || target.indexOf('?') >= 0)
            {
                expandGlob(base, target, seen, depth);
            }
            else
            {
                Path resolved = resolveInclude(base, target);
                collectFiles(resolved, seen, depth + 1);
            }
        }
    }

    /** 展开文件层通配（如 pjsip.d/*.conf），按文件名排序保证展示/解析稳定 */
    private void expandGlob(Path base, String target, Set<Path> seen, int depth) throws IOException
    {
        String pattern = target;
        Path parent = base;
        int slash = Math.max(target.lastIndexOf('/'), target.lastIndexOf('\\'));
        if (slash >= 0)
        {
            parent = resolveInclude(base, target.substring(0, slash));
            pattern = target.substring(slash + 1);
        }
        if (!Files.isDirectory(parent))
        {
            return;
        }
        List<Path> matched = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(parent, pattern))
        {
            ds.forEach(matched::add);
        }
        Collections.sort(matched);
        for (Path p : matched)
        {
            collectFiles(p, seen, depth + 1);
        }
    }

    /** 解析 include 目标：绝对路径原样，相对路径相对当前文件目录 */
    private static Path resolveInclude(Path base, String target)
    {
        Path p = Paths.get(target);
        return p.isAbsolute() ? p : base.resolve(target);
    }

    // ---------------------------------------------------------------- 语义组装

    private List<Section> parseAll(Set<Path> files) throws IOException
    {
        List<Section> all = new ArrayList<>();
        for (Path f : files)
        {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8))
            {
                all.addAll(AsteriskConfParser.parse(r));
            }
        }
        return all;
    }

    /** 组装端点视图：type=endpoint + 关联 type=aor 的静态 contact */
    private List<PjsipEndpoint> buildEndpoints(List<Section> sections)
    {
        Map<String, List<String>> aorContacts = new LinkedHashMap<>();
        for (Section s : sections)
        {
            if ("aor".equals(s.first("type")))
            {
                aorContacts.put(s.getName(), s.all("contact"));
            }
        }
        List<PjsipEndpoint> list = new ArrayList<>();
        for (Section s : sections)
        {
            if (!"endpoint".equals(s.first("type")))
            {
                continue;
            }
            PjsipEndpoint ep = new PjsipEndpoint();
            ep.setName(s.getName());
            ep.setContext(s.first("context"));
            ep.setCallerId(s.first("callerid"));
            ep.setAors(s.first("aors"));
            ep.setAllow(String.join(",", s.all("allow")));
            ep.setTransport(s.first("transport"));
            ep.setDirectMedia(s.first("direct_media"));
            ep.setForceRport(s.first("force_rport"));
            ep.setRewriteContact(s.first("rewrite_contact"));
            ep.setIceSupport(s.first("ice_support"));
            ep.setMediaEncryption(s.first("media_encryption"));

            List<String> contacts = new ArrayList<>();
            if (ep.getAors() != null)
            {
                for (String aorName : ep.getAors().split(","))
                {
                    List<String> c = aorContacts.get(aorName.trim());
                    if (c != null)
                    {
                        contacts.addAll(c);
                    }
                }
            }
            ep.setContacts(contacts);
            list.add(ep);
        }
        return list;
    }

    /** 组装队列视图：queues.conf 中除 [general] 外的全部配置节 */
    private List<PjsipQueue> buildQueues(List<Section> sections)
    {
        List<PjsipQueue> list = new ArrayList<>();
        for (Section s : sections)
        {
            if ("general".equalsIgnoreCase(s.getName()))
            {
                continue;
            }
            PjsipQueue q = new PjsipQueue();
            q.setName(s.getName());
            q.setStrategy(s.first("strategy"));
            q.setTimeout(parseInt(s.first("timeout")));
            q.setWrapupTime(parseInt(s.first("wrapuptime")));
            q.setRingInUse(s.first("ringinuse"));
            q.setMusicClass(s.first("musicclass"));
            q.setServiceLevel(parseInt(s.first("servicelevel")));
            q.setMaxlen(parseInt(s.first("maxlen")));
            q.setMembers(s.all("member"));
            list.add(q);
        }
        return list;
    }

    private static Integer parseInt(String value)
    {
        if (value == null)
        {
            return null;
        }
        String v = value.trim();
        if (v.isEmpty())
        {
            return null;
        }
        try
        {
            return Integer.valueOf(v);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
