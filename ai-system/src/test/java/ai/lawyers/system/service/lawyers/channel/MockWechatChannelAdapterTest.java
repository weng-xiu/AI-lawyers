package ai.lawyers.system.service.lawyers.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * P3-#16：{@link MockWechatChannelAdapter} 政务微信 Mock 通道测试。
 *
 * <p>覆盖渠道编码、验签（空签名/大小写/错误签名）、AES 解密回环、
 * 文本与事件报文归一（缺 FromUserName 报错、MsgType 默认值、时间字段）、回复 XML 组装。</p>
 *
 * @author ai-lawyers
 */
class MockWechatChannelAdapterTest
{
    private static final String AES_KEY = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";

    private MockWechatChannelAdapter adapter;

    @BeforeEach
    void setUp()
    {
        adapter = new MockWechatChannelAdapter();
        ReflectionTestUtils.setField(adapter, "token", "mock-token");
        ReflectionTestUtils.setField(adapter, "aesKey", AES_KEY);
        ReflectionTestUtils.setField(adapter, "fromAccount", "gh_mock_12348");
    }

    @Test
    void codeIsWechatMp()
    {
        assertThat(adapter.code()).isEqualTo("WECHAT_MP");
    }

    @Test
    void verifySignatureChecksTokenDigest()
    {
        String expected = WechatXmlCipher.signature("mock-token", "1700000000", "nonce", "cipher");

        assertThat(adapter.verifySignature(expected, "1700000000", "nonce", "cipher")).isTrue();
        assertThat(adapter.verifySignature(expected.toUpperCase(), "1700000000", "nonce", "cipher")).isTrue();
        assertThat(adapter.verifySignature("bad", "1700000000", "nonce", "cipher")).isFalse();
        assertThat(adapter.verifySignature(null, "1700000000", "nonce", "cipher")).isFalse();
        assertThat(adapter.verifySignature("", "1700000000", "nonce", "cipher")).isFalse();
    }

    @Test
    void decryptRoundTripsEncryptedPayload()
    {
        String plain = "<xml><FromUserName><![CDATA[user-1]]></FromUserName></xml>";
        String encrypted = WechatXmlCipher.encrypt(plain, "gh_mock_12348", AES_KEY);

        assertThat(adapter.decrypt(encrypted)).isEqualTo(plain);
    }

    @Test
    void normalizeTextMessage()
    {
        String xml = "<xml><FromUserName><![CDATA[user-1]]></FromUserName>"
                + "<MsgType><![CDATA[text]]></MsgType><Content><![CDATA[你好]]></Content>"
                + "<MsgId>123456</MsgId><CreateTime>1700000000</CreateTime></xml>";

        ChannelMessage message = adapter.normalize(xml);

        assertThat(message.getChannelType()).isEqualTo("WECHAT_MP");
        assertThat(message.getChannelUid()).isEqualTo("user-1");
        assertThat(message.getMsgType()).isEqualTo("TEXT");
        assertThat(message.getContent()).isEqualTo("你好");
        assertThat(message.getMsgId()).isEqualTo("123456");
        assertThat(message.getCreateTimeSec()).isEqualTo(1700000000L);
        assertThat(message.getReceivedTime()).isNotNull();
        assertThat(message.getRawPayload()).isEqualTo(xml);
    }

    @Test
    void normalizeEventMessageUsesEventAsContent()
    {
        String xml = "<xml><FromUserName>user-2</FromUserName><MsgType>event</MsgType>"
                + "<Event>subscribe</Event></xml>";

        ChannelMessage message = adapter.normalize(xml);

        assertThat(message.getMsgType()).isEqualTo("EVENT");
        assertThat(message.getContent()).isEqualTo("subscribe");
        assertThat(message.getCreateTimeSec()).isNull();
    }

    @Test
    void normalizeDefaultsMsgTypeToTextAndRejectsMissingFromUser()
    {
        ChannelMessage message = adapter.normalize(
                "<xml><FromUserName>u</FromUserName><Content>hi</Content></xml>");
        assertThat(message.getMsgType()).isEqualTo("TEXT");

        assertThatThrownBy(() -> adapter.normalize("<xml><MsgType>text</MsgType></xml>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FromUserName");
    }

    @Test
    void replyBuildsTextXml()
    {
        ChannelMessage incoming = new ChannelMessage();
        incoming.setChannelUid("user-3");

        String xml = adapter.reply(incoming, "已收到");

        assertThat(xml).contains("<ToUserName><![CDATA[user-3]]></ToUserName>")
                .contains("<FromUserName><![CDATA[gh_mock_12348]]></FromUserName>")
                .contains("<MsgType><![CDATA[text]]></MsgType>")
                .contains("<Content><![CDATA[已收到]]></Content>")
                .contains("<CreateTime>");
    }
}
