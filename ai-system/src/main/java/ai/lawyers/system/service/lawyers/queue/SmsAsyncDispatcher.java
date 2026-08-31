package ai.lawyers.system.service.lawyers.queue;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import ai.lawyers.system.service.lawyers.sms.ISmsService;

/**
 * T2-3 短信异步发送接入：IVR sms 节点把短信投递到 {@code sms-send} Stream 削峰，
 * 消费侧回调 {@link ISmsService#send} 真正发送；Stream 禁用或 Redis 异常时降级为同步直写。
 *
 * <p>幂等：短信发送已在 {@code SmsServiceImpl} 内做"先落待发日志→发送→按 logId 幂等回写"，
 * 队列 at-least-once 重投时由待发日志状态条件保证不重复发送（同一待发日志只回写一次）。</p>
 *
 * @author ai-lawyers
 */
@Component
public class SmsAsyncDispatcher
{
    private static final Logger log = LoggerFactory.getLogger(SmsAsyncDispatcher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private StreamQueueService streamQueueService;

    @Autowired
    private ISmsService smsService;

    @PostConstruct
    public void init()
    {
        streamQueueService.registerHandler(QueueNames.SMS_SEND, payload -> {
            SmsTask task = MAPPER.readValue(payload, SmsTask.class);
            smsService.send(task.phone, task.templateId, task.params, task.sessionId, task.recordId);
        });
    }

    /**
     * 异步发送短信：优先投递 Stream；队列不可用则同步直发短信（降级，保证功能不丢）。
     */
    public void sendAsync(String phone, Long templateId, Map<String, String> params,
                          String sessionId, Long recordId)
    {
        try
        {
            SmsTask task = new SmsTask();
            task.phone = phone;
            task.templateId = templateId;
            task.params = params == null ? new HashMap<>() : params;
            task.sessionId = sessionId;
            task.recordId = recordId;
            String json = MAPPER.writeValueAsString(task);
            if (streamQueueService.enqueue(QueueNames.SMS_SEND, json))
            {
                return;
            }
        }
        catch (Exception e)
        {
            log.warn("短信异步投递失败，降级同步发送 phone={}: {}", phone, e.getMessage());
        }
        // 降级：同步直写
        try
        {
            smsService.send(phone, templateId, params, sessionId, recordId);
        }
        catch (Exception e)
        {
            log.error("短信同步降级发送失败 phone={}: {}", phone, e.getMessage());
        }
    }

    /** sms-send 队列消息体 */
    public static class SmsTask
    {
        public String phone;
        public Long templateId;
        public Map<String, String> params;
        public String sessionId;
        public Long recordId;
    }
}
