package ai.lawyers.system.service.lawyers.trunk.gateway.ami;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** B1：AMI 事件块解析器单测 */
class AmiEventParserTest
{
    @Test
    void parsesEventBlockWithHeaders()
    {
        String block = "Event: Newchannel\n"
                + "Privilege: call,all\n"
                + "Uniqueid: 1719600000.12\n"
                + "CallerIDNum: 13800138000\n"
                + "Exten: 12348\n"
                + "Context: from-pstn\n\n";
        AmiEvent event = AmiEventParser.parse(block);
        assertThat(event).isNotNull();
        assertThat(event.getName()).isEqualTo("Newchannel");
        assertThat(event.get("Uniqueid")).isEqualTo("1719600000.12");
        assertThat(event.get("CallerIDNum")).isEqualTo("13800138000");
        assertThat(event.get("Exten")).isEqualTo("12348");
    }

    @Test
    void headerLookupIsCaseInsensitive()
    {
        AmiEvent event = AmiEventParser.parse("Event: Dial\nUniqueid: 1\nDestUniqueid: 2\n\n");
        assertThat(event.get("uniqueID")).isEqualTo("1");
        assertThat(event.get("destuniqueid")).isEqualTo("2");
    }

    @Test
    void responseBlockReturnsNull()
    {
        assertThat(AmiEventParser.parse("Response: Success\nMessage: Authentication accepted\n\n")).isNull();
    }

    @Test
    void emptyAndBannerReturnNull()
    {
        assertThat(AmiEventParser.parse(null)).isNull();
        assertThat(AmiEventParser.parse("")).isNull();
        assertThat(AmiEventParser.parse("\n\n")).isNull();
        assertThat(AmiEventParser.parse("Asterisk Call Manager/7.0.2\n")).isNull();
    }

    @Test
    void emptyEventNameReturnsNullAndColonlessLineSkipped()
    {
        // Event: 空值（EventList 附属结构）不作为可路由事件
        assertThat(AmiEventParser.parse("Event: \nEventList: start\n\n")).isNull();
        // 无冒号噪声行跳过，事件正常解析
        AmiEvent event = AmiEventParser.parse("Event: Hangup\nnoise-line-without-colon\nCause: 16\n\n");
        assertThat(event.getName()).isEqualTo("Hangup");
        assertThat(event.get("Cause")).isEqualTo("16");
    }

    @Test
    void customChannelVariableSurfacesAsHeader()
    {
        AmiEvent event = AmiEventParser.parse("Event: VarSet\nUniqueid: 1\nVariable: AI_CALL_UUID\nValue: uuid-abc\n\n");
        assertThat(event.getName()).isEqualTo("VarSet");
        assertThat(event.get("Variable")).isEqualTo("AI_CALL_UUID");
        assertThat(event.get("Value")).isEqualTo("uuid-abc");
    }
}
