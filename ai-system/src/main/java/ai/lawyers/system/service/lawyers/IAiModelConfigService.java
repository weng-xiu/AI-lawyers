package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiModelConfig;

/**
 * AI模型参数配置Service接口
 * 
 * @author ai-lawyers
 * @date 2025-07-15
 */
public interface IAiModelConfigService 
{
    /**
     * 查询AI模型参数配置
     * 
     * @param configId AI模型参数配置主键
     * @return AI模型参数配置
     */
    public AiModelConfig selectAiModelConfigByConfigId(Long configId);

    /**
     * 查询AI模型参数配置列表
     * 
     * @param aiModelConfig AI模型参数配置
     * @return AI模型参数配置集合
     */
    public List<AiModelConfig> selectAiModelConfigList(AiModelConfig aiModelConfig);

    /**
     * 新增AI模型参数配置
     * 
     * @param aiModelConfig AI模型参数配置
     * @return 结果
     */
    public int insertAiModelConfig(AiModelConfig aiModelConfig);

    /**
     * 修改AI模型参数配置
     * 
     * @param aiModelConfig AI模型参数配置
     * @return 结果
     */
    public int updateAiModelConfig(AiModelConfig aiModelConfig);

    /**
     * 批量删除AI模型参数配置
     * 
     * @param configIds 需要删除的AI模型参数配置主键集合
     * @return 结果
     */
    public int deleteAiModelConfigByConfigIds(Long[] configIds);

    /**
     * 删除AI模型参数配置信息
     * 
     * @param configId AI模型参数配置主键
     * @return 结果
     */
    public int deleteAiModelConfigByConfigId(Long configId);

    /**
     * 获取默认的AI模型配置
     * 
     * @return AI模型参数配置
     */
    public AiModelConfig getDefaultAiModelConfig();

    /**
     * 设置默认配置
     * 
     * @param configId 配置ID
     * @return 结果
     */
    public int setDefaultConfig(Long configId);

    /**
     * 测试AI模型连接
     * 
     * @param configId 配置ID
     * @return 测试结果
     */
    public boolean testAiModelConnection(Long configId);
    
    /**
     * 调用AI模型API生成回答（业务场景记 other，P3-E5 成本归因建议用带 scene 的重载）
     *
     * @param question 用户问题
     * @param context 上下文信息
     * @return AI生成的回答
     */
    public String callAiModel(String question, String context);

    /**
     * 调用AI模型API生成回答并指定业务场景（P3-E5 成本看板按场景归因）。
     *
     * @param question 用户问题
     * @param context  上下文信息
     * @param scene    业务场景（见 AiModelCallLogRecorder.SCENE_*）
     * @return AI生成的回答
     */
    public String callAiModel(String question, String context, String scene);

    /**
     * 以系统提示词 + 用户消息的方式调用默认模型（OpenAI/Claude 兼容 Chat Completions）。
     *
     * @param systemPrompt 系统提示词（可为空）
     * @param userMessage  用户消息
     * @return 模型返回的文本内容
     */
    public String chat(String systemPrompt, String userMessage);

    /**
     * 带业务场景的 chat 调用（P3-E5 成本看板按场景归因）。
     *
     * @param systemPrompt 系统提示词（可为空）
     * @param userMessage  用户消息
     * @param scene        业务场景（见 AiModelCallLogRecorder.SCENE_*）
     * @return 模型返回的文本内容
     */
    public String chat(String systemPrompt, String userMessage, String scene);

    /**
     * 以 JSON 模式调用默认模型，要求模型返回合法的 JSON 文本。
     *
     * @param systemPrompt 系统提示词（可为空）
     * @param userMessage  用户消息
     * @return 模型返回的 JSON 文本
     */
    public String chatJson(String systemPrompt, String userMessage);

    /**
     * 带业务场景的 chatJson 调用（P3-E5 成本看板按场景归因）。
     *
     * @param systemPrompt 系统提示词（可为空）
     * @param userMessage  用户消息
     * @param scene        业务场景（见 AiModelCallLogRecorder.SCENE_*）
     * @return 模型返回的 JSON 文本
     */
    public String chatJson(String systemPrompt, String userMessage, String scene);

    /**
     * T3 RAG：批量生成文本向量（OpenAI 兼容 /embeddings 协议，如 bge-small-zh 部署的兼容端点）。
     * 模型由 ai.rag.embedding-model（优先）或 ai.rag.embedding-config-id 指定，未配置则抛异常。
     *
     * @param texts 待向量化文本（单条调用时长度为 1）
     * @return 与入参顺序一一对应的向量；服务不可用/未配置时抛异常，由调用方降级到关键词路
     */
    public List<float[]> embedTexts(List<String> texts);

    /**
     * 带业务场景的向量生成（P3-E5 区分检索问答 rag 与知识库建索引 rag_index 的成本）。
     *
     * @param texts 待向量化文本
     * @param scene 业务场景（rag / rag_index）
     * @return 与入参顺序一一对应的向量
     */
    public List<float[]> embedTexts(List<String> texts, String scene);
}
