package ai.lawyers.system.service.lawyers.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T5-2 向量工具纯函数测试：余弦相似度 + float[]/byte[] 小端互转。
 *
 * @author ai-lawyers
 */
class VectorUtilsTest
{
    @Test
    void cosine_sameVector_isOne()
    {
        float[] v = {1f, 2f, 3f};
        assertThat(VectorUtils.cosine(v, v)).isEqualTo(1f, org.assertj.core.data.Offset.offset(1e-6f));
    }

    @Test
    void cosine_orthogonalVectors_isZero()
    {
        // x 轴与 y 轴正交，余弦为 0
        assertThat(VectorUtils.cosine(new float[]{1f, 0f}, new float[]{0f, 1f})).isEqualTo(0f);
    }

    @Test
    void cosine_oppositeVectors_isMinusOne()
    {
        assertThat(VectorUtils.cosine(new float[]{1f, 1f}, new float[]{-1f, -1f}))
                .isEqualTo(-1f, org.assertj.core.data.Offset.offset(1e-6f));
    }

    @Test
    void cosine_nullOrMismatchedLength_returnsZero()
    {
        assertThat(VectorUtils.cosine(null, new float[]{1f})).isZero();
        assertThat(VectorUtils.cosine(new float[]{1f}, null)).isZero();
        assertThat(VectorUtils.cosine(new float[]{1f, 2f}, new float[]{1f})).isZero();
        assertThat(VectorUtils.cosine(new float[]{}, new float[]{})).isZero();
    }

    @Test
    void cosine_zeroVector_returnsZero()
    {
        // 零向量无法归一化，返回 0 而非 NaN/Infinity
        assertThat(VectorUtils.cosine(new float[]{0f, 0f}, new float[]{1f, 1f})).isZero();
        assertThat(VectorUtils.cosine(new float[]{1f, 1f}, new float[]{0f, 0f})).isZero();
    }

    @Test
    void bytes_roundTrip_preservesValues()
    {
        float[] v = {0.5f, -1.25f, 3.14159f, -100.75f};
        byte[] bytes = VectorUtils.toBytes(v);
        // 每维 float 占 4 字节，小端
        assertThat(bytes).hasSize(v.length * 4);
        float[] back = VectorUtils.fromBytes(bytes);
        assertThat(back).containsExactly(v, org.assertj.core.data.Offset.offset(1e-6f));
    }

    @Test
    void bytes_isLittleEndian()
    {
        // 1.0f 小端字节序为 00 00 80 3F
        byte[] bytes = VectorUtils.toBytes(new float[]{1f});
        assertThat(bytes).containsExactly(0x00, 0x00, (byte) 0x80, 0x3F);
    }

    @Test
    void toBytes_nullOrEmpty_returnsEmpty()
    {
        assertThat(VectorUtils.toBytes(null)).isEmpty();
        assertThat(VectorUtils.toBytes(new float[]{})).isEmpty();
    }

    @Test
    void fromBytes_invalidLength_returnsEmpty()
    {
        assertThat(VectorUtils.fromBytes(null)).isEmpty();
        assertThat(VectorUtils.fromBytes(new byte[]{})).isEmpty();
        // 长度非 4 倍数不可解析，返回空数组而非抛异常
        assertThat(VectorUtils.fromBytes(new byte[]{1, 2, 3})).isEmpty();
    }
}
