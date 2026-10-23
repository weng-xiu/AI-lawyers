package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.IvrTemplateRenderer;
import ai.lawyers.system.service.lawyers.voice.VoiceEngineManager;
import ai.lawyers.system.service.lawyers.voice.VoiceModelEnum;

/**
 * say 播报节点：渲染文本（${表达式} SpEL 模板）写入 lastSay；
 * 节点配置 voiceEngine 时按所选引擎合成音频，未配置时保持纯文本播报（由语音网关执行）。
 *
 * @author ai-lawyers
 */
@Component
public class SayNodeExecutor implements IvrNodeExecutor
{
    public static final String NODE_TYPE = "say";

    private final IvrTemplateRenderer templateRenderer;
    private final VoiceEngineManager voiceEngineManager;

    public SayNodeExecutor(IvrTemplateRenderer templateRenderer, VoiceEngineManager voiceEngineManager)
    {
        this.templateRenderer = templateRenderer;
        this.voiceEngineManager = voiceEngineManager;
    }

    @Override
    public String[] supportedTypes()
    {
        return new String[] { NODE_TYPE };
    }

    @Override
    public AiIvrNode execute(IvrNodeContext ctx)
    {
        AiIvrNode current = ctx.getCurrent();
        Map<String, Object> variables = ctx.getVariables();
        IvrNodeStep step = ctx.getStep();

        String text = render(IvrNodeSupport.configText(current, "text"), variables);
        if (StringUtils.isEmpty(text) && current.getNodeConfig() != null
                && current.getNodeConfig().contains("\"play\""))
        {
            text = render(IvrNodeSupport.configText(current, "play"), variables);
        }
        if (StringUtils.isEmpty(text))
        {
            text = current.getNodeName();
        }
        text = render(text, variables);
        variables.put("lastSay", text);
        step.setAction("PLAY");
        String ttsNote = synthSay(current, text, variables);
        step.setDetail("语音播报：" + text + ttsNote);
        ctx.getResult().getSteps().add(step);
        return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables, null);
    }

    private String render(String template, Map<String, Object> variables)
    {
        return templateRenderer.render(template, variables);
    }

    /**
     * say 节点可选的 TTS 合成：节点配置 voiceEngine 时按所选引擎合成音频，
     * 未配置时保持纯文本播报（由语音网关执行），避免无密钥环境产生网络调用。
     */
    private String synthSay(AiIvrNode node, String text, Map<String, Object> variables)
    {
        JsonNode json = IvrNodeSupport.config(node);
        String engineCode = json.has("voiceEngine") ? json.get("voiceEngine").asText() : null;
        if (StringUtils.isEmpty(engineCode))
        {
            return "";
        }
        Map<String, Object> options = new HashMap<>();
        JsonNode tts = json.path("tts");
        if (tts.isObject())
        {
            Iterator<Map.Entry<String, JsonNode>> fields = tts.fields();
            while (fields.hasNext())
            {
                Map.Entry<String, JsonNode> entry = fields.next();
                options.put(entry.getKey(), entry.getValue().asText());
            }
        }
        String format = json.path("format").asText("wav");
        int sampleRate = json.path("sampleRate").asInt(8000);
        byte[] audio = voiceEngineManager.synthesize(VoiceModelEnum.of(engineCode), text,
                format, sampleRate, options);
        if (audio != null && audio.length > 0)
        {
            variables.put("lastSayAudioLength", audio.length);
            return "（TTS已合成" + audio.length + "字节音频）";
        }
        return "";
    }
}
