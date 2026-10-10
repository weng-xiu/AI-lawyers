package ai.lawyers.system.service.lawyers.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link MockWechatChannelAdapter} 全链路测试（#16 可先行部分）：
 * 密文加解密回环、验签通过/篡改拒绝、"验签→解密→归一→回信组装"全链路、
 * 事件消息归一、缺必要字段拒绝、非法密钥拒绝。
 *
 * @author ai-lawyers
 */
class WechatChannelAdapterTest
{
    private static final String TOKEN = "mock-wechat-token";

    private static final String AES_KEY = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";

    private static final String RECEIVE_ID = "gh_mock_12348";

    private MockWechatChannelAdapter adapter;

    @BeforeEach
    void setUp()
    {
        adapter = new MockWechatChannelAdapter();
    }

    private static String textXml(String uid, String content)
    {
        return "<xml><ToUserName><![CDATA[" + RECEIVE_ID + "]]></ToUserName>"
                + "<FromUserName><![CDATA[" + uid + "]]></FromUserName>"
                + "<CreateTime>1728500000</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[" + content + "]]></Content>"
                + "<MsgId>4561255354251345000</MsgId></xml>";
    }

    @Test
    void cipher_encryptDecryptRoundtrip()
    {
        String plain = textXml("oX7uTest", "劳动仲裁怎么申请？");

        String cipher = WechatXmlCipher.encrypt(plain, RECEIVE_ID, AES_KEY);

        assertThat(WechatXmlCipher.decrypt(cipher, AES_KEY)).isEqualTo(plain);
    }

    @Test
    void verifySignature_okAndTamperRejected()
    {
        String encrypt = WechatXmlCipher.encrypt(textXml("oX7uTest", "你好"), RECEIVE_ID, AES_KEY);
        String good = WechatXmlCipher.signature(TOKEN, "1728500000", "nonce1", encrypt);

        assertThat(adapter.verifySignature(good, "1728500000", "nonce1", encrypt)).isTrue();
        // 篡改时间戳 → 验签失败
        assertThat(adapter.verifySignature(good, "1728500001", "nonce1", encrypt)).isFalse();
        // 空签名 → 拒绝
        assertThat(adapter.verifySignature("", "1728500000", "nonce1", encrypt)).isFalse();
    }

    @Test
    void fullPipeline_verifyDecryptNormalize_reply()
    {
        String plain = textXml("oX7uCaller", "想咨询劳动合同解除补偿");
        String encrypt = WechatXmlCipher.encrypt(plain, RECEIVE_ID, AES_KEY);
        String signature = WechatXmlCipher.signature(TOKEN, "1728500000", "n1", encrypt);

        // 1) 验签
        assertThat(adapter.verifySignature(signature, "1728500000", "n1", encrypt)).isTrue();
        // 2) 解密
        String decrypted = adapter.decrypt(encrypt);
        assertThat(decrypted).isEqualTo(plain);
        // 3) 归一
        ChannelMessage msg = adapter.normalize(decrypted);
        assertThat(msg.getChannelType()).isEqualTo("WECHAT_MP");
        assertThat(msg.getChannelUid()).isEqualTo("oX7uCaller");
        assertThat(msg.getMsgType()).isEqualTo("TEXT");
        assertThat(msg.getContent()).isEqualTo("想咨询劳动合同解除补偿");
        assertThat(msg.getMsgId()).isEqualTo("4561255354251345000");
        assertThat(msg.getCreateTimeSec()).isEqualTo(1728500000L);
        assertThat(msg.getReceivedTime()).isNotNull();
        assertThat(msg.getRawPayload()).isEqualTo(plain);
        // 4) 回信组装（Mock 留痕）
        String replyXml = adapter.reply(msg, "已收到您的咨询，正在为您检索相关法规。");
        assertThat(replyXml).contains("oX7uCaller").contains("gh_mock_12348")
                .contains("已收到您的咨询").contains("text");
    }

    @Test
    void normalize_eventMessage()
    {
        String plain = "<xml><ToUserName><![CDATA[" + RECEIVE_ID + "]]></ToUserName>"
                + "<FromUserName><![CDATA[oX7uFan]]></FromUserName>"
                + "<CreateTime>1728500001</CreateTime>"
                + "<MsgType><![CDATA[event]]></MsgType>"
                + "<Event><![CDATA[subscribe]]></Event></xml>";

        ChannelMessage msg = adapter.normalize(plain);

        assertThat(msg.getMsgType()).isEqualTo("EVENT");
        assertThat(msg.getContent()).isEqualTo("subscribe");
        assertThat(msg.getMsgId()).isNull();
    }

    @Test
    void normalize_missingFromUserName_rejected()
    {
        String plain = "<xml><MsgType><![CDATA[text]]></MsgType><Content>hi</Content></xml>";

        assertThatThrownBy(() -> adapter.normalize(plain))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FromUserName");
    }

    @Test
    void decrypt_wrongKey_rejected()
    {
        String encrypt = WechatXmlCipher.encrypt(textXml("oX7uTest", "你好"), RECEIVE_ID, AES_KEY);
        String otherKey = "ZYXWVUTSRQPONMLKJIHGFEDCBAzyxwvutsrqponmlkjihgfedcba";

        assertThatThrownBy(() -> WechatXmlCipher.decrypt(encrypt, otherKey))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decrypt_badKeyLength_rejected()
    {
        assertThatThrownBy(() -> WechatXmlCipher.decrypt("AAAA", "short-key"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void xmlValue_cdataAndPlain_supported()
    {
        String xml = "<xml><A><![CDATA[带CDATA值]]></A><B>裸值</B><C></C></xml>";

        assertThat(WechatXmlCipher.xmlValue(xml, "A")).isEqualTo("带CDATA值");
        assertThat(WechatXmlCipher.xmlValue(xml, "B")).isEqualTo("裸值");
        assertThat(WechatXmlCipher.xmlValue(xml, "C")).isEmpty();
        assertThat(WechatXmlCipher.xmlValue(xml, "D")).isNull();
    }
}
