package ai.lawyers.system.domain.lawyers.trunk;

import java.util.List;

/**
 * P3-B5：PJSIP 端点只读视图（来源于 {@code pjsip.conf} 中 {@code type=endpoint}
 * 配置节及其关联 {@code type=aor} 的静态 contact，<b>非实时注册状态</b>）。
 *
 * <p>本期仅配置文件只读展示，平台不下发任何 PJSIP 配置。</p>
 *
 * @author ai-lawyers
 */
public class PjsipEndpoint
{
    /** 端点名称（配置节名） */
    private String name;

    /** 拨号上下文 context */
    private String context;

    /** 主叫标识 callerid */
    private String callerId;

    /** 关联 AOR 名称（原样，可能逗号分隔） */
    private String aors;

    /** 编解码 allow（多行合并，逗号分隔） */
    private String allow;

    /** 绑定传输 transport 名称 */
    private String transport;

    /** 直连媒体 direct_media（yes/no） */
    private String directMedia;

    /** 强制 rport force_rport（yes/no） */
    private String forceRport;

    /** 重写 Contact rewrite_contact（yes/no） */
    private String rewriteContact;

    /** ICE 支持 ice_support（yes/no） */
    private String iceSupport;

    /** 媒体加密 media_encryption（no/sdes/dtls） */
    private String mediaEncryption;

    /** 关联 AOR 上配置的静态 contact 列表（实时注册 contact 不在配置模型内） */
    private List<String> contacts;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }

    public String getCallerId() { return callerId; }
    public void setCallerId(String callerId) { this.callerId = callerId; }

    public String getAors() { return aors; }
    public void setAors(String aors) { this.aors = aors; }

    public String getAllow() { return allow; }
    public void setAllow(String allow) { this.allow = allow; }

    public String getTransport() { return transport; }
    public void setTransport(String transport) { this.transport = transport; }

    public String getDirectMedia() { return directMedia; }
    public void setDirectMedia(String directMedia) { this.directMedia = directMedia; }

    public String getForceRport() { return forceRport; }
    public void setForceRport(String forceRport) { this.forceRport = forceRport; }

    public String getRewriteContact() { return rewriteContact; }
    public void setRewriteContact(String rewriteContact) { this.rewriteContact = rewriteContact; }

    public String getIceSupport() { return iceSupport; }
    public void setIceSupport(String iceSupport) { this.iceSupport = iceSupport; }

    public String getMediaEncryption() { return mediaEncryption; }
    public void setMediaEncryption(String mediaEncryption) { this.mediaEncryption = mediaEncryption; }

    public List<String> getContacts() { return contacts; }
    public void setContacts(List<String> contacts) { this.contacts = contacts; }
}
