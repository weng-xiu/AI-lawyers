package ai.lawyers.system.service.lawyers.storage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * F2：录音文件存储抽象层（本地 / 对象存储）。
 *
 * <p>所有录音相关操作（保存、读取、删除、播放 URL）统一走本接口，
 * 屏蔽底层是本地文件系统还是 S3 兼容对象存储的差异。</p>
 *
 * @author ai-lawyers
 */
public interface RecordingStorageService
{
    /**
     * 保存录音文件。
     *
     * @param key  存储标识（对象存储为 object key；本地为相对/绝对路径）
     * @param file 本地文件
     * @throws IOException IO 或网络异常
     */
    void save(String key, File file) throws IOException;

    /**
     * 保存录音文件（流式）。
     *
     * @param key           存储标识
     * @param inputStream   输入流（调用方负责关闭）
     * @param contentLength 内容长度（字节），对象存储 PUT 需要 Content-Length
     * @param contentType   MIME 类型，可为 null
     * @throws IOException IO 或网络异常
     */
    void save(String key, InputStream inputStream, long contentLength, String contentType) throws IOException;

    /**
     * 读取录音文件。
     *
     * @param key 存储标识
     * @return 输入流（调用方负责关闭）；不存在时返回 null
     * @throws IOException IO 或网络异常
     */
    InputStream load(String key) throws IOException;

    /**
     * 删除录音文件。
     *
     * @param key 存储标识
     * @return true=删除成功或文件不存在；false=删除失败
     */
    boolean delete(String key);

    /**
     * 判断录音文件是否存在。
     *
     * @param key 存储标识
     * @return true=存在
     */
    boolean exists(String key);

    /**
     * 获取可直接播放/下载的 URL。
     * <ul>
     *   <li>本地存储：返回 null（调用方应继续走后端流式接口）；</li>
     *   <li>对象存储：返回预签名 URL（带临时访问凭证，支持 HTTP Range）。</li>
     * </ul>
     *
     * @param key           存储标识
     * @param expirySeconds URL 有效时长（秒）
     * @return 可直接访问的 URL；本地模式返回 null
     */
    String getPlayUrl(String key, int expirySeconds);

    /**
     * 当前是否为对象存储模式（非本地文件系统）。
     *
     * @return true=S3/OSS/MinIO 等对象存储
     */
    boolean isObjectStorage();
}
