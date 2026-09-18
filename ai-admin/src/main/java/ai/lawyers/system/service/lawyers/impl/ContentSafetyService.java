package ai.lawyers.system.service.lawyers.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.StringUtils;

/**
 * 公众端文本内容安全过滤（F8 安全基线，2026-09-17）
 *
 * <p>离线环境可用的本地敏感词过滤：命中即拒绝（政务热线咨询场景对违法/垃圾内容采用
 * 宁可拦截的保守策略，不做替换放行，避免垃圾/违规内容入库）。词库通过配置
 * {@code content.safety.words} 维护，逗号分隔；开关 {@code content.safety.enabled}
 * 默认开启。后续接入第三方文本内容安全 API 时，仅替换本类实现，调用方不变。</p>
 *
 * @author AI律师
 */
@Service
public class ContentSafetyService
{
    /** 问题内容最大长度（与前端 maxlength 对齐） */
    public static final int MAX_CONTENT_LENGTH = 1000;

    /** 文字反馈最大长度（与前端 maxlength 对齐） */
    public static final int MAX_FEEDBACK_LENGTH = 500;

    /** 问题内容最小长度（与前端校验对齐） */
    public static final int MIN_CONTENT_LENGTH = 10;

    @Value("${content.safety.enabled:true}")
    private boolean enabled;

    @Value("${content.safety.words:}")
    private String wordsConfig;

    /** 词库懒加载缓存（配置变更重启生效；默认词库为空时不拦截，仅做长度校验） */
    private volatile List<String> cachedWords;

    /**
     * 校验咨询提交内容
     *
     * @param category 问题分类
     * @param content 问题内容
     * @throws ai.lawyers.common.exception.ServiceException 校验不通过时抛出，由全局异常处理转业务码
     */
    public void validateConsultation(String category, String content)
    {
        if (StringUtils.isEmpty(category))
        {
            throw new ai.lawyers.common.exception.ServiceException("请选择问题分类");
        }
        if (StringUtils.isEmpty(content) || content.trim().length() < MIN_CONTENT_LENGTH)
        {
            throw new ai.lawyers.common.exception.ServiceException("问题描述至少需要10个字");
        }
        if (content.length() > MAX_CONTENT_LENGTH)
        {
            throw new ai.lawyers.common.exception.ServiceException("问题描述不能超过" + MAX_CONTENT_LENGTH + "个字");
        }
        String hit = firstHit(content);
        if (hit != null)
        {
            throw new ai.lawyers.common.exception.ServiceException("您提交的内容包含违规或不当信息（" + hit + "），请修改后重试");
        }
    }

    /**
     * 校验评价反馈文本（评分由 Controller 校验）
     *
     * @param feedback 文字反馈，可空（评价反馈为选填）
     */
    public void validateFeedback(String feedback)
    {
        if (StringUtils.isEmpty(feedback))
        {
            return;
        }
        if (feedback.length() > MAX_FEEDBACK_LENGTH)
        {
            throw new ai.lawyers.common.exception.ServiceException("反馈意见不能超过" + MAX_FEEDBACK_LENGTH + "个字");
        }
        String hit = firstHit(feedback);
        if (hit != null)
        {
            throw new ai.lawyers.common.exception.ServiceException("您提交的反馈包含违规或不当信息，请修改后重试");
        }
    }

    /**
     * 返回首个命中的敏感词；未命中（或开关关闭、词库为空）返回 null
     */
    public String firstHit(String text)
    {
        if (!enabled || StringUtils.isEmpty(text))
        {
            return null;
        }
        for (String word : words())
        {
            if (text.contains(word))
            {
                return word;
            }
        }
        return null;
    }

    private List<String> words()
    {
        List<String> words = cachedWords;
        if (words == null)
        {
            synchronized (this)
            {
                words = cachedWords;
                if (words == null)
                {
                    if (StringUtils.isEmpty(wordsConfig))
                    {
                        words = Collections.emptyList();
                    }
                    else
                    {
                        words = Arrays.stream(wordsConfig.split("[,，]"))
                                .map(String::trim)
                                .filter(StringUtils::isNotEmpty)
                                .collect(Collectors.toList());
                    }
                    cachedWords = words;
                }
            }
        }
        return words;
    }
}
