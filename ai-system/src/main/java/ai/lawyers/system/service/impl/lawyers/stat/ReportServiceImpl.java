package ai.lawyers.system.service.impl.lawyers.stat;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.mapper.lawyers.stat.ReportMapper;
import ai.lawyers.system.service.lawyers.stat.IReportService;

/**
 * 独立多维统计报表 Service 实现（P3-D6）
 *
 * <p>全部实时聚合现有业务表；P3-F3 ai_stat_minute 物化表落地后仅需切换 Mapper 数据源。</p>
 *
 * @author ai-lawyers
 */
@Service
public class ReportServiceImpl implements IReportService
{
    @Autowired
    private ReportMapper reportMapper;

    @Override
    public List<Map<String, Object>> callReport(String beginTime, String endTime, String granularity)
    {
        return reportMapper.selectCallReport(beginTime, endTime, normalizeGranularity(granularity));
    }

    @Override
    public List<Map<String, Object>> serviceReport(String beginTime, String endTime)
    {
        return reportMapper.selectServiceReport(beginTime, endTime);
    }

    @Override
    public List<Map<String, Object>> qualityReport(String beginTime, String endTime, String granularity)
    {
        return reportMapper.selectQualityReport(beginTime, endTime, normalizeGranularity(granularity));
    }

    @Override
    public List<Map<String, Object>> businessReport(String beginTime, String endTime, String granularity)
    {
        return reportMapper.selectBusinessReport(beginTime, endTime, normalizeGranularity(granularity));
    }

    /** 粒度白名单：仅允许 day/week/month，非法值回退 day（防 SQL 片段注入） */
    private String normalizeGranularity(String granularity)
    {
        if ("week".equals(granularity) || "month".equals(granularity))
        {
            return granularity;
        }
        return "day";
    }
}
