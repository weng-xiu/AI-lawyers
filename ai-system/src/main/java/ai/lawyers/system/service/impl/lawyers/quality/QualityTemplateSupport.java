package ai.lawyers.system.service.impl.lawyers.quality;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.quality.AiQualityTemplate;
import ai.lawyers.system.mapper.lawyers.quality.AiQualityTemplateMapper;

/**
 * P1-7：质检模板支撑——加载生效模板、解析维度权重、保存校验、加权总分。
 *
 * @author ai-lawyers
 */
@Component
public class QualityTemplateSupport
{
    private static final Logger log = LoggerFactory.getLogger(QualityTemplateSupport.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 系统已知维度 key（规则引擎只能评 serviceNorm/compliance） */
    private static final Set<String> KNOWN_KEYS = new HashSet<>(Arrays.asList(
            "serviceNorm", "answerAccuracy", "emotionAttitude", "compliance"));

    @Autowired
    private AiQualityTemplateMapper templateMapper;

    /** 解析后的生效模板（未配置/解析失败时为 null，调用方回退内置四维等权） */
    public ActiveTemplate loadActive()
    {
        try
        {
            AiQualityTemplate raw = templateMapper.selectActiveTemplate();
            if (raw == null)
            {
                return null;
            }
            List<Dimension> dims = parseDimensions(raw.getDimensions());
            if (dims.isEmpty())
            {
                log.warn("生效质检模板维度为空，回退内置默认 templateId={}", raw.getTemplateId());
                return null;
            }
            return new ActiveTemplate(raw.getTemplateId(), dims, raw.getScorePrompt());
        }
        catch (Exception e)
        {
            log.warn("加载质检模板失败，回退内置默认：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 按模板维度权重计算加权总分；只统计 scores 中非 null 的维度，权重在可用维度上归一化。
     */
    public double weightedTotal(ActiveTemplate template, JsonNode scores)
    {
        double sum = 0d, weight = 0d;
        for (Dimension dim : template.dimensions)
        {
            JsonNode node = scores.path(dim.key);
            if (node == null || node.isNull() || node.isMissingNode())
            {
                continue;
            }
            sum += node.asDouble(0d) * dim.weight;
            weight += dim.weight;
        }
        return weight <= 0d ? 0d : sum / weight;
    }

    /** 保存前校验：名称非空、维度 JSON 合法（key 已知/不重复、权重>0） */
    public void validate(AiQualityTemplate template)
    {
        if (template == null)
        {
            throw new IllegalArgumentException("模板不能为空");
        }
        if (StringUtils.isEmpty(template.getTemplateName()))
        {
            throw new IllegalArgumentException("模板名称不能为空");
        }
        List<Dimension> dims = parseDimensions(template.getDimensions());
        if (dims.isEmpty())
        {
            throw new IllegalArgumentException("至少配置一个评分维度");
        }
    }

    /** 解析维度 JSON，非法项跳过；全部非法时返回空表 */
    public List<Dimension> parseDimensions(String json)
    {
        List<Dimension> dims = new ArrayList<>();
        if (StringUtils.isEmpty(json))
        {
            return dims;
        }
        try
        {
            JsonNode array = MAPPER.readTree(json);
            if (!array.isArray())
            {
                return dims;
            }
            Set<String> seen = new HashSet<>();
            for (JsonNode item : array)
            {
                String key = item.path("key").asText("").trim();
                double weight = item.path("weight").asDouble(0d);
                if (!KNOWN_KEYS.contains(key) || weight <= 0d || seen.contains(key))
                {
                    continue;
                }
                seen.add(key);
                dims.add(new Dimension(key, item.path("name").asText(key), weight));
            }
        }
        catch (Exception e)
        {
            log.debug("维度JSON解析失败：{}", e.getMessage());
        }
        return dims;
    }

    /** 生效模板 */
    public static class ActiveTemplate
    {
        public final Long templateId;
        public final List<Dimension> dimensions;
        public final String scorePrompt;

        ActiveTemplate(Long templateId, List<Dimension> dimensions, String scorePrompt)
        {
            this.templateId = templateId;
            this.dimensions = dimensions;
            this.scorePrompt = scorePrompt;
        }

        public boolean hasKey(String key)
        {
            for (Dimension dim : dimensions)
            {
                if (dim.key.equals(key))
                {
                    return true;
                }
            }
            return false;
        }
    }

    /** 单维度定义 */
    public static class Dimension
    {
        public final String key;
        public final String name;
        public final double weight;

        Dimension(String key, String name, double weight)
        {
            this.key = key;
            this.name = name;
            this.weight = weight;
        }
    }
}
