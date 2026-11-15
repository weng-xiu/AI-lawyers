package ai.lawyers.system.service.lawyers.trunk.gateway.ami.pjsip;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AsteriskConfParser 单测：配置节顺序、重复键、= 与 => 分隔符、
 * 注释/BOM 容忍、节外与非法行忽略及 first/all 取值。
 *
 * @author ai-lawyers
 */
class AsteriskConfParserTest
{
    @Test
    void parsesSectionsInOrder()
    {
        List<AsteriskConfParser.Section> sections = parse(
                "[general]\n"
                + "type=global\n"
                + "[1001]\n"
                + "type=endpoint\n");

        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).getName()).isEqualTo("general");
        assertThat(sections.get(1).getName()).isEqualTo("1001");
        assertThat(sections.get(0).first("type")).isEqualTo("global");
    }

    @Test
    void keepsDuplicateKeysInOrder()
    {
        List<AsteriskConfParser.Section> sections = parse(
                "[1001]\n"
                + "allow=ulaw\n"
                + "allow=alaw\n"
                + "allow=g729\n");

        AsteriskConfParser.Section section = sections.get(0);
        assertThat(section.first("allow")).isEqualTo("ulaw");
        assertThat(section.all("allow")).containsExactly("ulaw", "alaw", "g729");
    }

    @Test
    void supportsArrowSeparator()
    {
        List<AsteriskConfParser.Section> sections = parse(
                "[queue]\n"
                + "member => PJSIP/1001\n"
                + "strategy=>ringall\n");

        assertThat(sections.get(0).first("member")).isEqualTo("PJSIP/1001");
        assertThat(sections.get(0).first("strategy")).isEqualTo("ringall");
    }

    @Test
    void stripsInlineComment()
    {
        List<AsteriskConfParser.Section> sections = parse(
                "[endpoint]\n"
                + "server_uri=sip:pbx;transport=udp\n");

        assertThat(sections.get(0).first("server_uri")).isEqualTo("sip:pbx");
    }

    @Test
    void ignoresFullLineComments()
    {
        List<AsteriskConfParser.Section> sections = parse(
                "; 注释行\n"
                + "# 井号注释\n"
                + "#include other.conf\n"
                + "[a]\n"
                + ";type=x\n"
                + "type=y\n");

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).all("type")).containsExactly("y");
    }

    @Test
    void keepsInlineHash()
    {
        List<AsteriskConfParser.Section> sections = parse(
                "[dialplan]\n"
                + "exten=100#\n");

        assertThat(sections.get(0).first("exten")).isEqualTo("100#");
    }

    @Test
    void ignoresKeysOutsideSection()
    {
        List<AsteriskConfParser.Section> sections = parse(
                "orphan=1\n"
                + "[a]\n"
                + "key=2\n");

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).all("orphan")).isEmpty();
        assertThat(sections.get(0).first("key")).isEqualTo("2");
    }

    @Test
    void toleratesBomAndWhitespace()
    {
        List<AsteriskConfParser.Section> sections = parse(
                "\uFEFF[a]\n"
                + "   key   =   value   \n");

        assertThat(sections.get(0).getName()).isEqualTo("a");
        assertThat(sections.get(0).first("key")).isEqualTo("value");
    }

    @Test
    void ignoresMalformedLinesAndEmptyKeys()
    {
        List<AsteriskConfParser.Section> sections = parse(
                "[a]\n"
                + "no-separator-line\n"
                + "=value-without-key\n"
                + "ok=1\n");

        assertThat(sections.get(0).all("")).isEmpty();
        assertThat(sections.get(0).first("ok")).isEqualTo("1");
    }

    @Test
    void missingKeyAccessorsAreSafe()
    {
        AsteriskConfParser.Section section = parse("[a]\nkey=1\n").get(0);

        assertThat(section.first("missing")).isNull();
        assertThat(section.all("missing")).isEmpty();
    }

    private List<AsteriskConfParser.Section> parse(String content)
    {
        try
        {
            return AsteriskConfParser.parse(new StringReader(content));
        }
        catch (java.io.IOException e)
        {
            throw new IllegalStateException(e);
        }
    }
}
