package ai.lawyers.system.service.lawyers.rag;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * T3 RAG 向量工具：余弦相似度 + float[] 与 LONGBLOB 字节互转。
 *
 * <p>向量以小端 float32 连续存储于 MySQL LONGBLOB，作为内存索引的备份/重启重建来源，
 * 不引入任何向量数据库或新中间件。</p>
 *
 * @author ai-lawyers
 */
public final class VectorUtils
{
    private VectorUtils() {}

    /** 余弦相似度：值越接近 1 越相关；任一向量为零向量返回 0 */
    public static float cosine(float[] a, float[] b)
    {
        if (a == null || b == null || a.length != b.length || a.length == 0)
        {
            return 0f;
        }
        float dot = 0f, normA = 0f, normB = 0f;
        for (int i = 0; i < a.length; i++)
        {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0f || normB == 0f)
        {
            return 0f;
        }
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    /** float[] 转小端字节（用于落 LONGBLOB） */
    public static byte[] toBytes(float[] vector)
    {
        if (vector == null || vector.length == 0)
        {
            return new byte[0];
        }
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vector)
        {
            buffer.putFloat(v);
        }
        return buffer.array();
    }

    /** 小端字节转 float[]（用于从 LONGBLOB 重建内存索引） */
    public static float[] fromBytes(byte[] bytes)
    {
        if (bytes == null || bytes.length == 0 || bytes.length % 4 != 0)
        {
            return new float[0];
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[bytes.length / 4];
        for (int i = 0; i < vector.length; i++)
        {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }
}
