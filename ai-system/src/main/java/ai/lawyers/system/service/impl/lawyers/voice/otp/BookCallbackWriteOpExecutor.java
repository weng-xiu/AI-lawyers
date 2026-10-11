package ai.lawyers.system.service.impl.lawyers.voice.otp;

import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.system.domain.lawyers.AiCallback;
import ai.lawyers.system.service.lawyers.IAiCallbackService;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpAction;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpExecutor;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpSession;

/**
 * P3-#12：预约回访执行器（白名单 {@link WriteOpAction#BOOK_CALLBACK}）。
 *
 * <p>payload 契约：{@code {"visitTime": "yyyy-MM-dd HH:mm"(必填),
 * "callerName": "可选", "ledgerId": 可选}}——来电号码强制取会话锚定的
 * {@code callerNumber}；回访单状态固定 0=待回访，回访人记 voice-robot。</p>
 *
 * @author ai-lawyers
 */
@Component
public class BookCallbackWriteOpExecutor implements WriteOpExecutor
{
    private static final Logger log = LoggerFactory.getLogger(BookCallbackWriteOpExecutor.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired(required = false)
    private IAiCallbackService callbackService;

    /** 测试注入用 */
    void setCallbackService(IAiCallbackService callbackService)
    {
        this.callbackService = callbackService;
    }

    @Override
    public WriteOpAction action()
    {
        return WriteOpAction.BOOK_CALLBACK;
    }

    @Override
    public ExecutionResult execute(WriteOpSession session)
    {
        if (callbackService == null)
        {
            return ExecutionResult.fail("回访服务未就绪");
        }
        JsonNode payload;
        try
        {
            payload = MAPPER.readTree(session.getPayloadJson() == null
                    ? "{}" : session.getPayloadJson());
        }
        catch (Exception e)
        {
            return ExecutionResult.fail("payload JSON 非法: " + e.getMessage());
        }
        String visitTimeStr = payload.path("visitTime").asText("").trim();
        if (visitTimeStr.isEmpty())
        {
            return ExecutionResult.fail("payload 缺少 visitTime");
        }
        Date visitTime = DateUtils.parseDate(visitTimeStr);
        if (visitTime == null)
        {
            return ExecutionResult.fail("visitTime 格式非法（应为 yyyy-MM-dd HH:mm）");
        }
        AiCallback callback = new AiCallback();
        callback.setCallerNumber(session.getCallerNumber());
        String callerName = payload.path("callerName").asText("").trim();
        if (!callerName.isEmpty())
        {
            callback.setCallerName(callerName);
        }
        if (payload.hasNonNull("ledgerId") && payload.get("ledgerId").canConvertToLong())
        {
            callback.setLedgerId(payload.get("ledgerId").asLong());
        }
        callback.setVisitTime(visitTime);
        callback.setVisitBy("voice-robot");
        callback.setStatus("0");
        int rows = callbackService.insertAiCallback(callback);
        if (rows <= 0)
        {
            return ExecutionResult.fail("回访单写入失败");
        }
        log.info("机器人预约回访完成 callbackId={} visitTime={}",
                callback.getCallbackId(), visitTimeStr);
        return ExecutionResult.ok("回访已预约",
                callback.getCallbackId() == null ? null : String.valueOf(callback.getCallbackId()));
    }
}
