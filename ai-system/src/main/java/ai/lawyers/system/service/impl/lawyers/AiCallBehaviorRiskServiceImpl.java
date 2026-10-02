package ai.lawyers.system.service.impl.lawyers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.sign.PiiCryptoUtils;
import ai.lawyers.system.domain.lawyers.AiHotspotSuppress;
import ai.lawyers.system.domain.lawyers.risk.AiCallRiskRecord;
import ai.lawyers.system.mapper.lawyers.AiHotspotSuppressMapper;
import ai.lawyers.system.mapper.lawyers.risk.AiCallRiskRecordMapper;
import ai.lawyers.system.service.lawyers.IAiCallBehaviorRiskService;

/**
 * 呼叫行为风控 Service 实现（P2-15）。
 *
 * <p>扫描口径：按号码盲索引（HMAC-SM3，同明文恒定输出）分组聚合，规避密文
 * 随机 IV 无法在库内分组的限制，且聚合结果不暴露明文号码。</p>
 *
 * <p>处置链路：评分超阈值 → 留痕待复核；auto-suppress=PRIORITY 时同步自动生成
 * 高频置底规则（PHONE 精确匹配，复用 {@link AiHotspotSuppress} 体系），
 * REJECT 拦截不做自动处置（避免误杀），班组长复核确认后仍只生成置底规则，
 * 需拦截由人工在置底规则页调整。</p>
 *
 * @author ai-lawyers
 */
@Service
public class AiCallBehaviorRiskServiceImpl implements IAiCallBehaviorRiskService
{
    private static final Logger log = LoggerFactory.getLogger(AiCallBehaviorRiskServiceImpl.class);

    private static final DateTimeFormatter RULE_NAME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiCallRiskRecordMapper riskMapper;

    @Autowired
    private AiHotspotSuppressMapper suppressMapper;

    /** 总开关（关闭后定时任务与扫描接口均空转，已有记录仍可查询复核） */
    @Value("${call.risk.enabled:true}")
    private boolean enabled;

    /** 评估窗口（小时） */
    @Value("${call.risk.window-hours:72}")
    private int windowHours;

    /** 窗口内最小来电次数（样本不足不评估） */
    @Value("${call.risk.min-calls:10}")
    private int minCalls;

    /** 风险评分处置阈值（达到即留痕） */
    @Value("${call.risk.score-threshold:60}")
    private int scoreThreshold;

    /** 中/高风险分界（等级展示用） */
    @Value("${call.risk.medium-threshold:60}")
    private int mediumThreshold;

    @Value("${call.risk.high-threshold:80}")
    private int highThreshold;

    /** 超短通话阈值（秒，≤该时长且接通视为超短） */
    @Value("${call.risk.short-seconds:15}")
    private int shortSeconds;

    /** 夜间时段 [nightStartHour, 次日 nightEndHour) */
    @Value("${call.risk.night-start-hour:22}")
    private int nightStartHour;

    @Value("${call.risk.night-end-hour:7}")
    private int nightEndHour;

    /** 自动处置策略：PRIORITY 自动生成置底规则 / OFF 仅留痕待人工复核 */
    @Value("${call.risk.auto-suppress:PRIORITY}")
    private String autoSuppress;

    /** 自动生成置底规则的频次阈值（窗口内命中 N 次才置底，防单次误伤） */
    @Value("${call.risk.suppress-trigger-count:3}")
    private int suppressTriggerCount;

    /** 自动生成置底规则的频次窗口（秒） */
    @Value("${call.risk.suppress-window-seconds:86400}")
    private int suppressWindowSeconds;

    @Override
    public int scanRisk()
    {
        if (!enabled)
        {
            return 0;
        }
        // 已启用 PHONE 置底规则的目标号码（盲索引集合），避免对已处置号码重复留痕
        Set<String> suppressedIndexes = loadSuppressedIndexes();
        LocalDateTime windowBegin = LocalDateTime.now().minusHours(windowHours);
        String begin = windowBegin.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select caller_number_index,"
              + " count(*) as call_count,"
              + " sum(case when status in ('1','2') and duration is not null and duration <= " + shortSeconds
              + "        then 1 else 0 end) as short_count,"
              + " sum(case when hour(call_time) >= " + nightStartHour
              + "        or hour(call_time) < " + nightEndHour + " then 1 else 0 end) as night_count,"
              + " sum(case when status = '3' then 1 else 0 end) as missed_count"
              + " from ai_call_record"
              + " where call_time >= ? and caller_number_index is not null and caller_number_index != ''"
              + " group by caller_number_index having count(*) >= " + minCalls
              + " order by call_count desc", begin);
        if (rows == null || rows.isEmpty())
        {
            return 0;
        }
        int inserted = 0;
        for (Map<String, Object> row : rows)
        {
            String index = String.valueOf(row.get("caller_number_index"));
            if (suppressedIndexes.contains(index))
            {
                continue;
            }
            // 同号码已有待复核记录则跳过，等班组长处理后再评估新窗口
            if (riskMapper.selectLatestByIndex(index, "0") != null)
            {
                continue;
            }
            int callCount = intOf(row.get("call_count"));
            int shortCount = intOf(row.get("short_count"));
            int nightCount = intOf(row.get("night_count"));
            int missedCount = intOf(row.get("missed_count"));
            int score = CallBehaviorRiskScorer.score(callCount, shortCount, nightCount, missedCount);
            if (score < scoreThreshold)
            {
                continue;
            }
            AiCallRiskRecord record = new AiCallRiskRecord();
            record.setCallerNumberIndex(index);
            record.setMaskedNumber(loadMaskedNumber(index));
            record.setWindowHours(windowHours);
            record.setCallCount(callCount);
            record.setShortCount(shortCount);
            record.setNightCount(nightCount);
            record.setMissedCount(missedCount);
            record.setRiskScore(score);
            record.setScoreDetail(CallBehaviorRiskScorer.scoreDetail(callCount, shortCount, nightCount, missedCount));
            record.setRiskLevel(CallBehaviorRiskScorer.level(score, mediumThreshold, highThreshold));
            record.setReviewStatus("0");
            if ("PRIORITY".equalsIgnoreCase(autoSuppress))
            {
                Long suppressId = createSuppressRule(record, index);
                record.setSuppressId(suppressId);
            }
            riskMapper.insertRisk(record);
            inserted++;
            log.info("[CallRisk] 风险号码留痕 masked={} score={} level={} detail={} suppressId={}",
                    record.getMaskedNumber(), score, record.getRiskLevel(), record.getScoreDetail(), record.getSuppressId());
        }
        return inserted;
    }

    @Override
    public List<AiCallRiskRecord> selectRiskList(AiCallRiskRecord query)
    {
        return riskMapper.selectRiskList(query);
    }

    @Override
    @Transactional
    public int reviewRisk(AiCallRiskRecord review)
    {
        if (review == null || review.getRiskId() == null)
        {
            throw new ServiceException("风控记录ID不能为空");
        }
        AiCallRiskRecord record = riskMapper.selectRiskById(review.getRiskId());
        if (record == null)
        {
            throw new ServiceException("风控记录不存在");
        }
        String status = review.getReviewStatus();
        if (!"1".equals(status) && !"2".equals(status))
        {
            throw new ServiceException("复核状态非法（1确认 2忽略）");
        }
        if ("1".equals(status) && record.getSuppressId() == null)
        {
            // 确认时未生成置底规则（auto-suppress=OFF 留痕或历史遗留）则按当前配置生成
            Long suppressId = createSuppressRule(record, record.getCallerNumberIndex());
            record.setSuppressId(suppressId);
        }
        record.setReviewStatus(status);
        record.setReviewBy(review.getReviewBy());
        record.setReviewTime(new Date());
        record.setReviewRemark(review.getReviewRemark());
        return riskMapper.updateReview(record);
    }

    /** 已启用 PHONE 规则的匹配值盲索引集合（规则量级有限，内存计算） */
    private Set<String> loadSuppressedIndexes()
    {
        Set<String> indexes = new HashSet<>();
        AiHotspotSuppress query = new AiHotspotSuppress();
        query.setMatchType("PHONE");
        query.setStatus("0");
        List<AiHotspotSuppress> rules = suppressMapper.selectSuppressList(query);
        if (rules != null)
        {
            for (AiHotspotSuppress rule : rules)
            {
                String index = PiiCryptoUtils.blindIndex(rule.getMatchValue());
                if (index != null)
                {
                    indexes.add(index);
                }
            }
        }
        return indexes;
    }

    /** 从话单取一个密文号码解密后脱敏；失败返回 null（脱敏缺失不阻断留痕） */
    private String loadMaskedNumber(String index)
    {
        try
        {
            List<String> numbers = jdbcTemplate.queryForList(
                    "select caller_number from ai_call_record where caller_number_index = ?"
                  + " and caller_number is not null order by record_id desc limit 1",
                    String.class, index);
            if (numbers == null || numbers.isEmpty())
            {
                return null;
            }
            String stored = numbers.get(0);
            String plain = PiiCryptoUtils.isEncrypted(stored) ? PiiCryptoUtils.decrypt(stored) : stored;
            return CallBehaviorRiskScorer.maskNumber(plain);
        }
        catch (Exception ex)
        {
            log.warn("[CallRisk] 号码脱敏失败 index={}: {}", index, ex.getMessage());
            return null;
        }
    }

    /**
     * 生成高频置底规则（PHONE 精确匹配，动作固定 PRIORITY 置底，频次窗口可配）。
     * 已存在同号码规则（uk_match 冲突）时返回 null。
     */
    private Long createSuppressRule(AiCallRiskRecord record, String index)
    {
        String plain = loadPlainNumber(index);
        if (plain == null)
        {
            log.warn("[CallRisk] 无法取到明文号码，跳过置底规则生成 index={}", index);
            return null;
        }
        AiHotspotSuppress suppress = new AiHotspotSuppress();
        suppress.setRuleName("行为风控-" + (record.getMaskedNumber() == null ? index.substring(0, 8) : record.getMaskedNumber())
                + "-" + java.time.LocalDateTime.now().format(RULE_NAME_FMT));
        suppress.setMatchType("PHONE");
        suppress.setMatchValue(plain);
        suppress.setAction("PRIORITY");
        suppress.setPriorityLevel(-100);
        suppress.setTriggerCount(suppressTriggerCount);
        suppress.setWindowSeconds(suppressWindowSeconds);
        suppress.setStatus("0");
        suppress.setCreateBy("risk-scan");
        suppress.setRemark("P2-15 呼叫行为风控自动生成 score=" + record.getRiskScore()
                + " " + record.getScoreDetail()
                + "（窗口" + windowHours + "h 来电" + record.getCallCount() + "次）");
        try
        {
            suppressMapper.insertSuppress(suppress);
            return suppress.getSuppressId();
        }
        catch (Exception ex)
        {
            // 同号码规则已存在（唯一键冲突）等场景：不阻断留痕
            log.warn("[CallRisk] 置底规则生成失败 masked={}: {}", record.getMaskedNumber(), ex.getMessage());
            return null;
        }
    }

    /** 盲索引反查明文号码（仅内部用于生成置底规则，不落风控表） */
    private String loadPlainNumber(String index)
    {
        try
        {
            List<String> numbers = jdbcTemplate.queryForList(
                    "select caller_number from ai_call_record where caller_number_index = ?"
                  + " and caller_number is not null order by record_id desc limit 1",
                    String.class, index);
            if (numbers == null || numbers.isEmpty())
            {
                return null;
            }
            String stored = numbers.get(0);
            return PiiCryptoUtils.isEncrypted(stored) ? PiiCryptoUtils.decrypt(stored) : stored;
        }
        catch (Exception ex)
        {
            log.warn("[CallRisk] 明文号码解密失败 index={}: {}", index, ex.getMessage());
            return null;
        }
    }

    private int intOf(Object value)
    {
        if (value == null)
        {
            return 0;
        }
        if (value instanceof Number)
        {
            return ((Number) value).intValue();
        }
        try
        {
            return Integer.parseInt(String.valueOf(value));
        }
        catch (NumberFormatException ex)
        {
            return 0;
        }
    }
}
