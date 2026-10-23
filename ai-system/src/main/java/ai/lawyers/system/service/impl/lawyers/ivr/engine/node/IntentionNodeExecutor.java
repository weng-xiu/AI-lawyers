package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiHotspotSuppress;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IntentionMatchResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.IvrTemplateRenderer;
import ai.lawyers.system.service.lawyers.IAiHotspotSuppressService;
import ai.lawyers.system.service.lawyers.ivr.engine.IntentionRecognitionService;

/**
 * intention 意图识别节点：取问题文本（配置/输入队列/lastInput 兜底）执行意图识别，
 * 命中结果写入 matchedIntention 等变量与结果集。
 *
 * <p>P3-D1 高频置底：识别文本命中 REJECT 规则即终止流程（挂断语义，复用 hangup 终止标记）；
 * 命中 PRIORITY 规则写入 dispatchPriority 变量，后续转人工节点自动读取降权沉底。</p>
 *
 * @author ai-lawyers
 */
@Component
public class IntentionNodeExecutor implements IvrNodeExecutor
{
    private static final Logger log = LoggerFactory.getLogger(IntentionNodeExecutor.class);

    public static final String NODE_TYPE = "intention";

    private final IvrTemplateRenderer templateRenderer;
    private final IntentionRecognitionService intentionRecognitionService;
    private final IAiHotspotSuppressService hotspotSuppressService;

    /** 高频置底总开关（与入站/外呼共用） */
    private final boolean hotspotEnabled;

    public IntentionNodeExecutor(IvrTemplateRenderer templateRenderer,
                                 IntentionRecognitionService intentionRecognitionService,
                                 IAiHotspotSuppressService hotspotSuppressService,
                                 @Value("${hotspot.suppress.enabled:true}") boolean hotspotEnabled)
    {
        this.templateRenderer = templateRenderer;
        this.intentionRecognitionService = intentionRecognitionService;
        this.hotspotSuppressService = hotspotSuppressService;
        this.hotspotEnabled = hotspotEnabled;
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
        IvrExecuteResult result = ctx.getResult();

        String intentText = templateRenderer.render(IvrNodeSupport.configText(current, "question"), variables);
        if (StringUtils.isEmpty(intentText))
        {
            intentText = IvrNodeSupport.pollInput(ctx.getInputs());
        }
        if (StringUtils.isEmpty(intentText))
        {
            Object lastInputValue = variables.get("lastInput");
            intentText = lastInputValue == null ? "" : lastInputValue.toString();
        }
        // P3-D1：高频置底关键词拦截——识别文本命中 REJECT 规则即终止流程（挂断语义，复用 hangup 终止标记）；
        // 命中 PRIORITY 规则写入 dispatchPriority 变量，后续转人工节点 dispatchAgent 自动读取降权沉底
        if (hotspotEnabled && StringUtils.isNotEmpty(intentText))
        {
            String hotspotDirection = StringUtils.isNotEmpty(ctx.getRequest().getCalleeNumber()) ? "OUTBOUND" : "INBOUND";
            AiHotspotSuppress kwSuppress = hotspotSuppressService.matchKeyword(intentText,
                    ctx.getRequest().getCallerNumber(), ctx.getSessionId(), hotspotDirection);
            if (kwSuppress != null && "REJECT".equals(kwSuppress.getAction()))
            {
                log.warn("[IVR] 命中高频置底关键词规则[{}]，流程终止（拦截挂断） sessionId={}",
                        kwSuppress.getRuleName(), ctx.getSessionId());
                step.setAction("HANGUP");
                step.setDetail("命中高频置底关键词规则[" + kwSuppress.getRuleName() + "]，拦截挂断");
                result.getSteps().add(step);
                variables.put("__terminated", "hangup");
                return null;
            }
            if (kwSuppress != null && "PRIORITY".equals(kwSuppress.getAction()))
            {
                int kwPriority = kwSuppress.getPriorityLevel() != null ? kwSuppress.getPriorityLevel() : -100;
                variables.put("dispatchPriority", kwPriority);
                log.info("[IVR] 命中置底降权关键词规则[{}]，转人工降权 priority={} sessionId={}",
                        kwSuppress.getRuleName(), kwPriority, ctx.getSessionId());
            }
        }
        IntentionMatchResult match = intentionRecognitionService.recognize(intentText,
                ctx.getRequest().getRecordId(), ctx.getSessionId(),
                ctx.getFlow().getFlowId(), current.getNodeId());
        variables.put("matchedIntention", match == null || !match.isMatched() ? "" : match.getIntentionCode());
        variables.put("matchedIntentionName", match == null || !match.isMatched() ? "" : match.getIntentionName());
        if (match != null && match.isMatched())
        {
            result.setMatchedIntention(match.getIntentionCode());
            result.setMatchedIntentionName(match.getIntentionName());
            result.setMatchMethod(match.getMatchMethod());
            result.setCategoryId(match.getCategoryId());
            result.setCategoryName(match.getCategoryName());
            if (match.getCategoryId() != null)
            {
                variables.put("matchedCategoryId", match.getCategoryId());
                variables.put("matchedCategoryName", match.getCategoryName());
            }
            step.setAction("INTENTION");
            step.setDetail("意图识别[" + match.getMatchMethod() + "]：" + match.getIntentionName()
                    + "(" + match.getIntentionCode() + ")");
        }
        else
        {
            step.setAction("INTENTION");
            step.setDetail("意图识别：未命中");
        }
        result.getSteps().add(step);
        return IvrNodeSupport.nextNode(current, ctx.getNodeMap(), ctx.getEdges(), variables, null);
    }
}
