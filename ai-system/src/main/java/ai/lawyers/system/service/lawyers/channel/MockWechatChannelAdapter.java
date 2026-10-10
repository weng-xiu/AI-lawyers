package ai.lawyers.system.service.lawyers.channel;

import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * P3-#16（全渠道 G9）：政务微信/公众号 Mock 通道适配器（WECHAT_MP）。
 *
 * <p>真实凭证（AppId/AppSecret/EncodingAESKey/Token）与政务外网连通前，
 * 以默认 Mock 凭证跑通"验签 → 解密 → 归一 → 回信组装"全链路；
 * 联调时将真实凭证覆盖 {@code ai.channel.wechat-mp.*} 配置即可切换，
 * 代码不变。回复仅组装报文并留痕（Mock 不外发），真实发送在凭证到位后按
 * 渠道 API 实装。</p>
 *
 * @author ai-lawyers
 */
@Service("mockWechatChannelAdapter")
public class MockWechatChannelAdapter implements ChannelAdapter
{
    private static final Logger log = LoggerFactory.getLogger(MockWechatChannelAdapter.class);

    /** 回调 Token（验签） */
    @Value("${ai.channel.wechat-mp.token:mock-wechat-token}")
    private String token = "mock-wechat-token";

    /** EncodingAESKey（43 字符，Mock 默认值；真实环境覆盖配置） */
    @Value("${ai.channel.wechat-mp.aes-key:abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ}")
    private String aesKey = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";

    /** 接收方账号（公众号原始ID；Mock 默认值） */
    @Value("${ai.channel.wechat-mp.from-account:gh_mock_12348}")
    private String fromAccount = "gh_mock_12348";

    @Override
    public String code()
    {
        return "WECHAT_MP";
    }

    @Override
    public boolean verifySignature(String signature, String timestamp, String nonce, String encrypt)
    {
        if (signature == null || signature.isEmpty())
        {
            return false;
        }
        return signature.equalsIgnoreCase(WechatXmlCipher.signature(token, timestamp, nonce, encrypt));
    }

    @Override
    public String decrypt(String encrypt)
    {
        return WechatXmlCipher.decrypt(encrypt, aesKey);
    }

    @Override
    public ChannelMessage normalize(String plaintext)
    {
        String fromUser = WechatXmlCipher.xmlValue(plaintext, "FromUserName");
        if (fromUser == null || fromUser.isEmpty())
        {
            throw new IllegalArgumentException("报文缺少 FromUserName，无法归一");
        }
        ChannelMessage m = new ChannelMessage();
        m.setChannelType(code());
        m.setChannelUid(fromUser);
        String msgType = WechatXmlCipher.xmlValue(plaintext, "MsgType");
        m.setMsgType(msgType == null ? "TEXT" : msgType.toUpperCase());
        if ("EVENT".equalsIgnoreCase(msgType))
        {
            m.setContent(WechatXmlCipher.xmlValue(plaintext, "Event"));
        }
        else
        {
            m.setContent(WechatXmlCipher.xmlValue(plaintext, "Content"));
        }
        m.setMsgId(WechatXmlCipher.xmlValue(plaintext, "MsgId"));
        String create = WechatXmlCipher.xmlValue(plaintext, "CreateTime");
        m.setCreateTimeSec(create == null ? null : Long.valueOf(create));
        m.setReceivedTime(new Date());
        m.setRawPayload(plaintext);
        return m;
    }

    @Override
    public String reply(ChannelMessage incoming, String replyText)
    {
        long now = System.currentTimeMillis() / 1000;
        String xml = "<xml>"
                + "<ToUserName><![CDATA[" + incoming.getChannelUid() + "]]></ToUserName>"
                + "<FromUserName><![CDATA[" + fromAccount + "]]></FromUserName>"
                + "<CreateTime>" + now + "</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[" + replyText + "]]></Content>"
                + "</xml>";
        log.info("[MOCK WECHAT_MP] 回复 uid={} 内容={}", incoming.getChannelUid(), replyText);
        return xml;
    }
}
