package ai.lawyers.system.service.lawyers.voice.emotion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 情绪/意图实时识别器（P3-E4）。
 *
 * <p><b>纯规则词库、零网络零成本、单次分析微秒级</b>，满足实时语音链路
 * （ASR partial/final 回调线程内直接调用，不引入排队/远程延迟）。词库按
 * 12348 真实来电口径维护，紧急口径宁错报不遗漏；误报由班长置"已忽略"。</p>
 *
 * <p>情绪两级：</p>
 * <ul>
 *   <li>{@link EmotionIntentResult#EMOTION_URGENT}：自伤/自杀、严重暴力、正在发生的
 *       重大不法侵害等危机信号（命中即高级预警 + 队列置顶）；</li>
 *   <li>{@link EmotionIntentResult#EMOTION_NEGATIVE}：投诉不满、愤怒指责、悲伤哭泣等
 *       负面信号（中级预警 + 队列提前）。</li>
 * </ul>
 *
 * <p>意图七类（映射风险预警建议条线与未来 IVR 路由）：优先级 URGENT &gt; COMPLAINT
 * &gt; LEGAL_AID &gt; MEDIATION &gt; NOTARY &gt; FORENSIC &gt; ARBITRATION，
 * 多类命中时取高者。LLM 语义分类为后续增强（需评估实时性/成本），本类不依赖。</p>
 *
 * @author ai-lawyers
 */
public class EmotionIntentDetector
{
    /** 意图：紧急危机（情绪 urgent 命中时强制归此意图） */
    public static final String INTENT_URGENT = "URGENT";

    /** 意图：投诉举报 */
    public static final String INTENT_COMPLAINT = "COMPLAINT";

    /** 意图：法律咨询/法律援助 */
    public static final String INTENT_LEGAL_AID = "LEGAL_AID";

    /** 意图：人民调解 */
    public static final String INTENT_MEDIATION = "MEDIATION";

    /** 意图：公证 */
    public static final String INTENT_NOTARY = "NOTARY";

    /** 意图：司法鉴定 */
    public static final String INTENT_FORENSIC = "FORENSIC";

    /** 意图：仲裁 */
    public static final String INTENT_ARBITRATION = "ARBITRATION";

    /** urgent 情绪词（危机信号，高特异词优先；如"救命"等口语短词按宁错报保留） */
    private static final String[] URGENT_WORDS = {
        "不想活", "活不下去", "自杀", "轻生", "跳楼", "跳桥", "跳河", "割腕", "自我了断",
        "杀人", "砍死", "打死他", "打死人", "打死我", "弄死", "捅死", "灭门", "毒杀",
        "同归于尽", "报复社会", "炸了", "爆炸", "放火", "纵火", "挟持", "绑架",
        "要出人命", "出人命", "救命", "快来救我", "正在打我", "快被打死", "不敢回家"
    };

    /** negative 情绪词（负面情绪/不满） */
    private static final String[] NEGATIVE_WORDS = {
        "投诉", "举报", "控告", "告你们", "态度恶劣", "态度差", "气死", "气疯",
        "太过分", "过分", "黑暗", "勾结", "串通", "骗子", "骗人", "骗钱", "诈骗我",
        "敷衍", "推诿", "踢皮球", "不作为", "乱来", "乱收费", "骂我", "骂人",
        "威胁我", "恐吓", "不满意", "失望", "委屈", "哭", "白吃白拿", "没人管"
    };

    /** 业务意图词库（顺序即优先级，首个命中的意图胜出） */
    private final Map<String, String[]> intentWords = new LinkedHashMap<>();

    public EmotionIntentDetector()
    {
        // COMPLAINT 先于业务条线：来电含"投诉"时路由按投诉举报处理
        intentWords.put(INTENT_COMPLAINT, new String[] {
            "投诉", "举报", "控告", "纪委", "督察", "不作为", "乱收费", "政风", "行风"
        });
        intentWords.put(INTENT_LEGAL_AID, new String[] {
            "法律援助", "申请援助", "援助中心", "请律师", "没钱请律师", "经济困难",
            "援助律师", "免费律师", "打官司没钱"
        });
        intentWords.put(INTENT_MEDIATION, new String[] {
            "调解", "居委会", "村委会调解", "调解员", "人民调解", "调解委员会",
            "邻居纠纷", "邻里纠纷"
        });
        intentWords.put(INTENT_NOTARY, new String[] {
            "公证", "公证处", "继承权公证", "委托公证", "婚前财产公证", "公证员"
        });
        intentWords.put(INTENT_FORENSIC, new String[] {
            "司法鉴定", "伤残鉴定", "伤情鉴定", "轻伤鉴定", "笔迹鉴定", "精神病鉴定",
            "亲子鉴定", "鉴定机构"
        });
        intentWords.put(INTENT_ARBITRATION, new String[] {
            "仲裁", "劳动仲裁", "仲裁委", "仲裁员", "商事仲裁", "仲裁申请"
        });
    }

    /**
     * 分析一段文本（ASR partial/final 均可）。
     *
     * @param text 转写文本，空文本返回 neutral
     * @return 识别结果（永不返回 null）
     */
    public EmotionIntentResult analyze(String text)
    {
        if (text == null || text.trim().isEmpty())
        {
            return EmotionIntentResult.neutral();
        }
        List<String> urgentHits = match(text, URGENT_WORDS);
        List<String> negativeHits = match(text, NEGATIVE_WORDS);

        String emotion;
        List<String> emotionHits;
        if (!urgentHits.isEmpty())
        {
            emotion = EmotionIntentResult.EMOTION_URGENT;
            emotionHits = urgentHits;
        }
        else if (!negativeHits.isEmpty())
        {
            emotion = EmotionIntentResult.EMOTION_NEGATIVE;
            emotionHits = negativeHits;
        }
        else
        {
            emotion = EmotionIntentResult.EMOTION_NEUTRAL;
            emotionHits = new ArrayList<>();
        }

        // 意图：urgent 强制 URGENT；否则按优先级取首个命中的业务意图
        String intent = null;
        List<String> intentHits = new ArrayList<>();
        if (!urgentHits.isEmpty())
        {
            intent = INTENT_URGENT;
            intentHits = urgentHits;
        }
        else
        {
            for (Map.Entry<String, String[]> e : intentWords.entrySet())
            {
                List<String> hits = match(text, e.getValue());
                if (!hits.isEmpty())
                {
                    intent = e.getKey();
                    intentHits = hits;
                    break;
                }
            }
        }
        return new EmotionIntentResult(emotion, intent, emotionHits, intentHits);
    }

    /** 子串匹配，返回命中词（按词库顺序去重） */
    private List<String> match(String text, String[] words)
    {
        List<String> hits = new ArrayList<>();
        for (String w : words)
        {
            if (text.contains(w) && !hits.contains(w))
            {
                hits.add(w);
            }
        }
        return hits;
    }
}
