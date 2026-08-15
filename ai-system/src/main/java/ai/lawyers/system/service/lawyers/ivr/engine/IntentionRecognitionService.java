package ai.lawyers.system.service.lawyers.ivr.engine;

import ai.lawyers.system.domain.lawyers.ivr.IntentionMatchResult;

/**
 * 意图识别服务：正则快速匹配 + AI 大模型深度识别双引擎。
 *
 * 融合方案 3.5.2：意图识别结果自动关联咨询分类，识别日志与通话记录关联。
 */
public interface IntentionRecognitionService
{
    /**
     * 识别输入文本对应的意图（正则优先，AI 模型兜底），并落意图识别日志。
     */
    IntentionMatchResult recognize(String text, Long recordId, String sessionId, Long flowId, Long nodeId);

    /**
     * 仅做识别，不落日志（供界面测试用）。
     */
    IntentionMatchResult recognize(String text);
}
