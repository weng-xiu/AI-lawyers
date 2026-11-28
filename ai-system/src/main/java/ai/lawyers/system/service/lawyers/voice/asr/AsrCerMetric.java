package ai.lawyers.system.service.lawyers.voice.asr;

import java.text.Normalizer;
import org.springframework.stereotype.Component;

/**
 * N2（语雀 7.4 S1）：ASR 转写质量度量内核——字符级字错率（CER）。
 *
 * <p>对参考文本（人工金标准）与识别假设文本先做归一化，再计算 Levenshtein 编辑距离，
 * 以 <code>CER = (替换 S + 删除 D + 插入 I) / 参考字符数 N</code> 量化转写准确率。
 * 中文按字符逐字比较、不做分词；纯 JDK 实现，无第三方依赖，任何路径不外抛。</p>
 *
 * <p>归一化：NFKC 收敛全半角 → 英文小写 → 去除 Unicode 标点与空白，使标点/空格/大小写
 * 差异不计入错误。参考为空时：双空视为 0 错误，转写非空给有限值 1.0（避免除零、可入门禁）；
 * 插入过多时 CER 可能大于 1，不裁剪。</p>
 *
 * @author ai-lawyers
 */
@Component
public class AsrCerMetric
{
    /**
     * 文本归一化：NFKC 全半角收敛、英文小写、去标点与空白；null/空串返回空串。
     */
    public String normalize(String text)
    {
        if (text == null || text.isEmpty())
        {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        StringBuilder sb = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++)
        {
            char ch = normalized.charAt(i);
            if (isPunctuation(ch) || Character.isWhitespace(ch) || Character.isSpaceChar(ch))
            {
                continue;
            }
            sb.append(Character.toLowerCase(ch));
        }
        return sb.toString();
    }

    /**
     * 计算两段文本归一化后的字符级 Levenshtein 编辑距离（S+D+I 最小次数）。
     * 滚动数组实现，空间复杂度 O(min 长度)。
     */
    public int editDistance(String reference, String hypothesis)
    {
        return levenshtein(normalize(reference), normalize(hypothesis));
    }

    /**
     * 计算单条样本的 CER 度量结果。
     *
     * @param reference  人工金标准参考文本
     * @param hypothesis ASR 识别假设文本
     */
    public AsrCerResult score(String reference, String hypothesis)
    {
        String ref = normalize(reference);
        String hyp = normalize(hypothesis);
        int distance = levenshtein(ref, hyp);
        int refLength = ref.length();
        double cer;
        if (refLength == 0)
        {
            // 参考为空：双空视为完美；转写非空给有限值 1.0，避免除零
            cer = hyp.isEmpty() ? 0d : 1.0d;
        }
        else
        {
            cer = round3((double) distance / refLength);
        }
        return new AsrCerResult(distance, refLength, cer);
    }

    /** 字符级 Levenshtein，入参应为已归一化文本 */
    private int levenshtein(String a, String b)
    {
        int n = a.length();
        int m = b.length();
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++)
        {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++)
        {
            cur[0] = i;
            for (int j = 1; j <= m; j++)
            {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[m];
    }

    private boolean isPunctuation(char ch)
    {
        switch (Character.getType(ch))
        {
            case Character.CONNECTOR_PUNCTUATION:
            case Character.DASH_PUNCTUATION:
            case Character.START_PUNCTUATION:
            case Character.END_PUNCTUATION:
            case Character.INITIAL_QUOTE_PUNCTUATION:
            case Character.FINAL_QUOTE_PUNCTUATION:
            case Character.OTHER_PUNCTUATION:
                return true;
            default:
                return false;
        }
    }

    /** 比率保留 3 位小数（0~1 量纲，CER 因插入可能略大于 1） */
    private static double round3(double v)
    {
        return Math.round(v * 1000d) / 1000d;
    }

    /** 单条样本 CER 度量结果（值对象，手写 getter） */
    public static class AsrCerResult
    {
        private final int editDistance;

        private final int refLength;

        private final double cer;

        public AsrCerResult(int editDistance, int refLength, double cer)
        {
            this.editDistance = editDistance;
            this.refLength = refLength;
            this.cer = cer;
        }

        public int getEditDistance() { return editDistance; }

        public int getRefLength() { return refLength; }

        public double getCer() { return cer; }
    }
}
