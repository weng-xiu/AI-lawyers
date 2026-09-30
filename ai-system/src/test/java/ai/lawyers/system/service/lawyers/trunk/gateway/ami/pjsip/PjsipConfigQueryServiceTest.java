package ai.lawyers.system.service.lawyers.trunk.gateway.ami.pjsip;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.domain.lawyers.trunk.PjsipEndpoint;
import ai.lawyers.system.domain.lawyers.trunk.PjsipQueue;

/**
 * P3-B5：{@link PjsipConfigQueryService} 配置文件解析只读模型测试。
 *
 * <p>使用 JUnit5 {@link TempDir} 落地真实 pjsip.conf/queues.conf，覆盖：
 * 端点字段+AOR contact 关联、#include 单文件/通配、循环包含防环、队列 => 老式语法、
 * 文件缺失空结果、mtime 缓存。</p>
 */
class PjsipConfigQueryServiceTest
{
    @TempDir
    Path tempDir;

    private PjsipConfigQueryService service;

    @BeforeEach
    void setUp()
    {
        service = new PjsipConfigQueryService();
        ReflectionTestUtils.setField(service, "configDir", tempDir.toString());
    }

    private void write(String relative, String content) throws IOException
    {
        Path p = tempDir.resolve(relative);
        if (p.getParent() != null)
        {
            Files.createDirectories(p.getParent());
        }
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void endpoints_parsedWithAorContacts() throws IOException
    {
        write("pjsip.conf",
                "; 整行注释\n"
                + "# 非 include 的 # 行也当注释\n"
                + "strayKey=ignored\n" // 节外键值忽略
                + "[transport-udp]\n"
                + "type=transport\n"
                + "protocol=udp\n"
                + "bind=0.0.0.0\n"
                + "\n"
                + "[1001]\n"
                + "type=endpoint\n"
                + "context=from-internal\n"
                + "callerid=一号坐席 <1001>\n"
                + "aors=1001\n"
                + "auth=1001\n"
                + "transport=transport-udp\n"
                + "direct_media=no\n"
                + "force_rport=yes\n"
                + "rewrite_contact=yes ; 行内注释\n"
                + "ice_support=no\n"
                + "media_encryption=no\n"
                + "allow=!all\n"
                + "allow=ulaw,alaw\n"
                + "\n"
                + "[1001]\n"
                + "type=aor\n"
                + "max_contacts=1\n"
                + "contact=sip:1001@192.168.1.20:5060\n");

        List<PjsipEndpoint> endpoints = service.listEndpoints();
        assertThat(endpoints).hasSize(1);
        PjsipEndpoint ep = endpoints.get(0);
        assertThat(ep.getName()).isEqualTo("1001");
        assertThat(ep.getContext()).isEqualTo("from-internal");
        assertThat(ep.getCallerId()).isEqualTo("一号坐席 <1001>");
        assertThat(ep.getAors()).isEqualTo("1001");
        assertThat(ep.getTransport()).isEqualTo("transport-udp");
        assertThat(ep.getDirectMedia()).isEqualTo("no");
        assertThat(ep.getForceRport()).isEqualTo("yes");
        assertThat(ep.getRewriteContact()).isEqualTo("yes");
        assertThat(ep.getIceSupport()).isEqualTo("no");
        assertThat(ep.getMediaEncryption()).isEqualTo("no");
        assertThat(ep.getAllow()).isEqualTo("!all,ulaw,alaw");
        // AOR 静态 contact 关联
        assertThat(ep.getContacts()).containsExactly("sip:1001@192.168.1.20:5060");
        // transport 节不是端点
        assertThat(service.listQueues()).isEmpty();
    }

    @Test
    void include_singleFile_supported() throws IOException
    {
        write("pjsip.conf", "; 主文件仅做包含\n#include pjsip/endpoints.conf\n");
        write("pjsip/endpoints.conf",
                "[2001]\n"
                + "type=endpoint\n"
                + "context=from-internal\n"
                + "aors=2001\n"
                + "[2001]\n"
                + "type=aor\n"
                + "contact=sip:2001@10.0.0.21\n");

        List<PjsipEndpoint> endpoints = service.listEndpoints();
        assertThat(endpoints).extracting(PjsipEndpoint::getName).containsExactly("2001");
        assertThat(endpoints.get(0).getContacts()).containsExactly("sip:2001@10.0.0.21");

        Map<String, Object> summary = service.summary();
        @SuppressWarnings("unchecked")
        List<String> included = (List<String>) summary.get("includedFiles");
        assertThat(included).hasSize(2);
        assertThat(included.toString()).contains("pjsip.conf", "endpoints.conf");
    }

    @Test
    void include_glob_supported() throws IOException
    {
        write("pjsip.conf", "#include pjsip.d/*.conf\n");
        write("pjsip.d/a.conf", "[3001]\ntype=endpoint\ncontext=from-internal\n");
        write("pjsip.d/b.conf", "[3002]\ntype=endpoint\ncontext=from-internal\n");

        assertThat(service.listEndpoints())
                .extracting(PjsipEndpoint::getName)
                .containsExactly("3001", "3002"); // 文件名排序保证稳定
    }

    @Test
    void include_circular_doesNotLoop() throws IOException
    {
        write("pjsip.conf", "#include pjsip/a.conf\n");
        write("pjsip/a.conf", "[4001]\ntype=endpoint\n#include b.conf\n");
        write("pjsip/b.conf", "[4002]\ntype=endpoint\n#include a.conf\n");

        assertThat(service.listEndpoints())
                .extracting(PjsipEndpoint::getName)
                .containsExactlyInAnyOrder("4001", "4002");
    }

    @Test
    void queues_parsedWithLegacyArrowSyntax() throws IOException
    {
        write("queues.conf",
                "[general]\n"
                + "persistent = yes\n"
                + "\n"
                + "[service]\n"
                + "strategy => rrmemory\n"
                + "timeout => 15\n"
                + "wrapuptime => 5\n"
                + "ringinuse => no\n"
                + "musicclass => default\n"
                + "servicelevel => 60\n"
                + "maxlen => 30\n"
                + "member => PJSIP/1001\n"
                + "member => PJSIP/1002,0,二号坐席\n");

        List<PjsipQueue> queues = service.listQueues();
        assertThat(queues).hasSize(1); // [general] 排除
        PjsipQueue q = queues.get(0);
        assertThat(q.getName()).isEqualTo("service");
        assertThat(q.getStrategy()).isEqualTo("rrmemory");
        assertThat(q.getTimeout()).isEqualTo(15);
        assertThat(q.getWrapupTime()).isEqualTo(5);
        assertThat(q.getRingInUse()).isEqualTo("no");
        assertThat(q.getMusicClass()).isEqualTo("default");
        assertThat(q.getServiceLevel()).isEqualTo(60);
        assertThat(q.getMaxlen()).isEqualTo(30);
        assertThat(q.getMembers()).containsExactly("PJSIP/1001", "PJSIP/1002,0,二号坐席");
    }

    @Test
    void missingConfigFiles_emptyResultAndFlags()
    {
        assertThat(service.listEndpoints()).isEmpty();
        assertThat(service.listQueues()).isEmpty();
        Map<String, Object> summary = service.summary();
        assertThat(summary.get("pjsipExists")).isEqualTo(false);
        assertThat(summary.get("queuesExists")).isEqualTo(false);
        assertThat(summary.get("endpointCount")).isEqualTo(0);
        assertThat(summary.get("queueCount")).isEqualTo(0);
    }

    @Test
    void cache_servedWhileFilesUnchanged()
    {
        // 空目录场景：连续读取，文件 mtime 快照不变 → 命中同一缓存（loadedAt 不变）
        long first = (Long) service.summary().get("loadedAt");
        long second = (Long) service.summary().get("loadedAt");
        assertThat(first).isEqualTo(second);
    }

    @Test
    void cache_reloadedAfterRootFileChanges() throws IOException, InterruptedException
    {
        long first = (Long) service.summary().get("loadedAt");

        // 新增根文件（原"不存在"→ 存在，mtime 快照变化）触发重新解析
        Thread.sleep(12L);
        write("pjsip.conf", "[5001]\ntype=endpoint\ncontext=from-internal\n");

        Map<String, Object> summary = service.summary();
        assertThat(summary.get("loadedAt")).isNotEqualTo(first);
        assertThat(service.listEndpoints())
                .extracting(PjsipEndpoint::getName).containsExactly("5001");
    }
}
