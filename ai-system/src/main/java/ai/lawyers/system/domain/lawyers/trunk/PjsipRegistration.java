package ai.lawyers.system.domain.lawyers.trunk;

import java.util.List;

/**
 * PJSIP 端点实时注册态（V2.53，来源 AMI PJSIPShowEndpoints 的 EndpointList 事件，
 * 与 PjsipEndpoint 静态配置视图互补；只读展示，不下发任何配置）
 *
 * @author ai-lawyers
 */
public class PjsipRegistration
{
    /** 端点名（ObjectName） */
    private String name;

    /** 设备状态（DeviceState：Not in use / In use / Busy / Unavailable / Invalid ...） */
    private String deviceState;

    /** 活动通道数（ActiveChannels） */
    private Integer activeChannels;

    /** 实时 contact 列表（URI + 到达状态） */
    private List<Contact> contacts;

    /** 原始 Contacts 行（解析兜底展示） */
    private String rawContacts;

    /** 单条 contact 的实时状态 */
    public static class Contact
    {
        /** contact URI */
        private String uri;
        /** 到达状态（Avail / Unavail / NonQualified / Rejected ...），可能为 null */
        private String status;

        public String getUri() { return uri; }
        public void setUri(String uri) { this.uri = uri; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDeviceState() { return deviceState; }
    public void setDeviceState(String deviceState) { this.deviceState = deviceState; }
    public Integer getActiveChannels() { return activeChannels; }
    public void setActiveChannels(Integer activeChannels) { this.activeChannels = activeChannels; }
    public List<Contact> getContacts() { return contacts; }
    public void setContacts(List<Contact> contacts) { this.contacts = contacts; }
    public String getRawContacts() { return rawContacts; }
    public void setRawContacts(String rawContacts) { this.rawContacts = rawContacts; }
}
