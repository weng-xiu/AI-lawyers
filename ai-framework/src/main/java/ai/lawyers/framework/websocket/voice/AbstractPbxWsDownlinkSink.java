package ai.lawyers.framework.websocket.voice;

import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * N1（语雀 7.3.3）：PBX 下行 sink 复用 fork WebSocket 会话的公共骨架。
 *
 * <p>承载与传输编码无关的共享语义：关闭幂等守卫、会话可写性检查、发送异常统一
 * 转 {@code false}（供 {@link DownlinkDebouncer} 计丢帧，不重试）。具体帧编码
 * （mod_audio_stream 的文本 JSON base64 / mod_audio_fork 的二进制裸 PCM）
 * 由子类 {@link #sendFrame} 实现。</p>
 *
 * <p>并发：下行写入发生在 TTS 合成回调线程，经 {@link DownlinkDebouncer} 的
 * {@code synchronized} 方法串行化，本类无锁；同时该 fork 连接此前无其它主动下行，
 * sink 是唯一的 {@code getBasicRemote()} 发送者，满足 JSR-356「同一 RemoteEndpoint
 * 禁止多线程并发发送」约束。</p>
 *
 * @author ai-lawyers
 */
public abstract class AbstractPbxWsDownlinkSink implements PbxDownlinkAudioSink
{
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final Session wsSession;

    protected final String recordId;

    /** 是否已关闭（幂等；关闭后拒绝写入，避免向已释放连接发送） */
    private final AtomicBoolean closed = new AtomicBoolean();

    protected AbstractPbxWsDownlinkSink(Session wsSession, String recordId)
    {
        this.wsSession = wsSession;
        this.recordId = recordId;
    }

    @Override
    public boolean write(byte[] pcm, int sampleRate)
    {
        if (closed.get() || pcm == null || pcm.length == 0)
        {
            return false;
        }
        Session session = wsSession;
        if (session == null || !session.isOpen())
        {
            log.debug("[PbxDownlink] 会话不可写，丢弃下行合片 recordId={} open={}", recordId,
                    session == null ? "null" : session.isOpen());
            return false;
        }
        try
        {
            sendFrame(session, pcm, sampleRate);
            return true;
        }
        catch (Exception e)
        {
            log.debug("[PbxDownlink] 下行发送异常（分片丢弃） recordId={} bytes={} rate={}: {}",
                    recordId, pcm.length, sampleRate, e.getMessage());
            return false;
        }
    }

    @Override
    public void close()
    {
        if (closed.compareAndSet(false, true))
        {
            log.debug("[PbxDownlink] sink 已关闭 recordId={}", recordId);
        }
    }

    /**
     * 子类实现：把一段已合片的 PCM 按目标协议写到 fork 会话（文本帧或二进制帧）。
     * 实现只需关注编码与发送，守卫/异常归一由本类承担。
     */
    protected abstract void sendFrame(Session session, byte[] pcm, int sampleRate) throws Exception;
}
