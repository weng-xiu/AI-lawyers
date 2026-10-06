package ai.lawyers.system.service.impl.lawyers.quality;

/**
 * P0-5：录音文件尚未就绪（可恢复异常）。
 *
 * <p>质检转写前等待录音落盘超时、或对象存储中对象尚不可读时抛出。
 * 区别于不可恢复失败：质检状态置回待检(0)并继续由队列/定时补扫重试。</p>
 *
 * @author ai-lawyers
 */
public class RecordingNotReadyException extends IllegalStateException
{
    private static final long serialVersionUID = 1L;

    public RecordingNotReadyException(String message)
    {
        super(message);
    }
}
