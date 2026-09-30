package ai.lawyers.system.domain.lawyers.trunk;

import java.util.List;

/**
 * P3-B5：Asterisk 呼叫队列只读视图（来源于 {@code queues.conf}，{@code [general]}
 * 节除外）。
 *
 * <p>本期仅配置文件只读展示，平台不下发队列配置、不修改实时成员态。</p>
 *
 * @author ai-lawyers
 */
public class PjsipQueue
{
    /** 队列名称（配置节名） */
    private String name;

    /** 振铃策略 strategy（ringall/rrmemory/fewestcalls 等） */
    private String strategy;

    /** 坐席振铃超时（秒） */
    private Integer timeout;

    /** 话后整理时长（秒） */
    private Integer wrapupTime;

    /** 是否振铃占用中的坐席 ringinuse（yes/no） */
    private String ringInUse;

    /** 等待保持音乐 musicclass */
    private String musicClass;

    /** 服务水平阈值（秒，用于 service level 统计） */
    private Integer serviceLevel;

    /** 队列最大等待人数 maxlen（0=不限） */
    private Integer maxlen;

    /** 静态成员列表（member= 原值，如 PJSIP/1001 或带 penalty/姓名的完整串） */
    private List<String> members;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }

    public Integer getTimeout() { return timeout; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }

    public Integer getWrapupTime() { return wrapupTime; }
    public void setWrapupTime(Integer wrapupTime) { this.wrapupTime = wrapupTime; }

    public String getRingInUse() { return ringInUse; }
    public void setRingInUse(String ringInUse) { this.ringInUse = ringInUse; }

    public String getMusicClass() { return musicClass; }
    public void setMusicClass(String musicClass) { this.musicClass = musicClass; }

    public Integer getServiceLevel() { return serviceLevel; }
    public void setServiceLevel(Integer serviceLevel) { this.serviceLevel = serviceLevel; }

    public Integer getMaxlen() { return maxlen; }
    public void setMaxlen(Integer maxlen) { this.maxlen = maxlen; }

    public List<String> getMembers() { return members; }
    public void setMembers(List<String> members) { this.members = members; }
}
