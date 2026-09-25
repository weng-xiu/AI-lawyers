package ai.lawyers.system.service.lawyers.stat;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 模型响应 Token 用量解析结果（P3-E5）。
 *
 * <p>兼容三类响应：</p>
 * <ul>
 *   <li>OpenAI 兼容 Chat Completions：usage.prompt_tokens / completion_tokens / total_tokens</li>
 *   <li>Anthropic Claude Messages：usage.input_tokens / output_tokens（无 total，合计得出）</li>
 *   <li>OpenAI 兼容 Embeddings：usage.total_tokens（部分实现只回 prompt_tokens），计入输入侧</li>
 * </ul>
 *
 * <p>任何字段缺失按 0 处理，绝不因用量解析失败影响业务调用。</p>
 *
 * @author ai-lawyers
 * @date 2026-09-25
 */
public class ModelUsage
{
    public static final ModelUsage ZERO = new ModelUsage(0, 0, 0);

    private final int promptTokens;
    private final int completionTokens;
    private final int totalTokens;

    public ModelUsage(int promptTokens, int completionTokens, int totalTokens)
    {
        this.promptTokens = Math.max(0, promptTokens);
        this.completionTokens = Math.max(0, completionTokens);
        this.totalTokens = Math.max(0, totalTokens);
    }

    /** 解析 OpenAI 兼容 chat/embeddings 响应根节点 */
    public static ModelUsage parseOpenAi(JsonNode root)
    {
        if (root == null)
        {
            return ZERO;
        }
        JsonNode usage = root.path("usage");
        if (usage.isMissingNode() || !usage.isObject())
        {
            return ZERO;
        }
        int prompt = usage.path("prompt_tokens").asInt(0);
        int completion = usage.path("completion_tokens").asInt(0);
        int total = usage.path("total_tokens").asInt(0);
        if (total <= 0)
        {
            total = prompt + completion;
        }
        return new ModelUsage(prompt, completion, total);
    }

    /** 解析 Anthropic Claude Messages 响应根节点（input/output，无 total） */
    public static ModelUsage parseClaude(JsonNode root)
    {
        if (root == null)
        {
            return ZERO;
        }
        JsonNode usage = root.path("usage");
        if (usage.isMissingNode() || !usage.isObject())
        {
            return ZERO;
        }
        int input = usage.path("input_tokens").asInt(0);
        int output = usage.path("output_tokens").asInt(0);
        return new ModelUsage(input, output, input + output);
    }

    public int getPromptTokens()
    {
        return promptTokens;
    }

    public int getCompletionTokens()
    {
        return completionTokens;
    }

    public int getTotalTokens()
    {
        return totalTokens;
    }
}
