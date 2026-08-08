package ai.lawyers.system.service.impl.lawyers.trunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.domain.lawyers.trunk.AiNumberSegment;
import ai.lawyers.system.enums.CarrierEnum;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallTrunkMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiNumberSegmentMapper;
import ai.lawyers.system.service.lawyers.trunk.ICarrierRouteService;
import ai.lawyers.system.utils.trunk.NumberTransformUtils;

/**
 * 运营商识别与线路选路服务实现
 *
 * 号段数据量小（数百条）且极少变更，启动时全量加载到内存 Map，
 * 按"最长前缀优先"策略匹配，单次识别为 O(k)（k = 最大前缀长度），
 * 避免每次外呼都查库。
 */
@Service
public class CarrierRouteServiceImpl implements ICarrierRouteService
{
    private static final Logger log = LoggerFactory.getLogger(CarrierRouteServiceImpl.class);

    /** 号段最长前缀位数，覆盖 4 位虚商号段 */
    private static final int MAX_PREFIX_LEN = 7;

    /** 最短前缀位数（固话区号最短 3 位，如 010） */
    private static final int MIN_PREFIX_LEN = 2;

    @Autowired
    private AiNumberSegmentMapper numberSegmentMapper;

    @Autowired
    private AiCallTrunkMapper callTrunkMapper;

    /** 前缀 -> 号段，内存缓存 */
    private final Map<String, AiNumberSegment> segmentCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init()
    {
        try
        {
            refreshSegmentCache();
        }
        catch (Exception e)
        {
            // 启动阶段表可能尚未创建，不阻断应用启动
            log.warn("号段缓存初始化失败，稍后可通过接口手动刷新: {}", e.getMessage());
        }
    }

    @Override
    public void refreshSegmentCache()
    {
        List<AiNumberSegment> list = numberSegmentMapper.selectAllEnabled();
        Map<String, AiNumberSegment> fresh = new ConcurrentHashMap<>();
        if (list != null)
        {
            for (AiNumberSegment seg : list)
            {
                if (seg.getSegmentPrefix() != null && !seg.getSegmentPrefix().isEmpty())
                {
                    fresh.put(seg.getSegmentPrefix(), seg);
                }
            }
        }
        segmentCache.clear();
        segmentCache.putAll(fresh);
        log.info("号段缓存刷新完成，共加载 {} 条号段", segmentCache.size());
    }

    @Override
    public AiNumberSegment recognizeSegment(String calleeNumber)
    {
        String number = NumberTransformUtils.normalize(calleeNumber);
        if (number.isEmpty())
        {
            return null;
        }
        if (segmentCache.isEmpty())
        {
            refreshSegmentCache();
        }
        // 最长前缀优先：从长到短依次尝试
        int maxLen = Math.min(MAX_PREFIX_LEN, number.length());
        for (int len = maxLen; len >= MIN_PREFIX_LEN; len--)
        {
            AiNumberSegment seg = segmentCache.get(number.substring(0, len));
            if (seg != null)
            {
                return seg;
            }
        }
        return null;
    }

    @Override
    public String recognizeCarrier(String calleeNumber)
    {
        AiNumberSegment seg = recognizeSegment(calleeNumber);
        if (seg != null && seg.getCarrier() != null)
        {
            return seg.getCarrier();
        }
        log.debug("号码 {} 未匹配到号段，运营商记为未知", NumberTransformUtils.mask(calleeNumber));
        return CarrierEnum.UNKNOWN.getCode();
    }

    @Override
    public List<AiCallTrunk> selectCandidateTrunks(String carrier, List<Long> excludeTrunkIds)
    {
        String target = (carrier == null || carrier.isEmpty()) ? CarrierEnum.UNKNOWN.getCode() : carrier;
        List<AiCallTrunk> candidates = callTrunkMapper.selectAvailableTrunks(target);
        if (candidates == null || candidates.isEmpty())
        {
            log.warn("运营商 {} 无可用线路", target);
            return Collections.emptyList();
        }
        if (excludeTrunkIds == null || excludeTrunkIds.isEmpty())
        {
            return candidates;
        }
        List<AiCallTrunk> filtered = new ArrayList<>(candidates.size());
        for (AiCallTrunk trunk : candidates)
        {
            if (!excludeTrunkIds.contains(trunk.getTrunkId()))
            {
                filtered.add(trunk);
            }
        }
        return filtered;
    }

    @Override
    public AiCallTrunk getTrunkByCode(String trunkCode)
    {
        if (trunkCode == null || trunkCode.trim().isEmpty())
        {
            return null;
        }
        return callTrunkMapper.selectAiCallTrunkByCode(trunkCode.trim());
    }
}
