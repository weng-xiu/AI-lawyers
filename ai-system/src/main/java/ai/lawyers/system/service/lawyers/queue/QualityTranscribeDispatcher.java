package ai.lawyers.system.service.lawyers.queue;

import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.system.service.lawyers.quality.IAiQualityInspectionService;

/**
 * T4-1 质检转写队列消费者：消费 quality-transcribe 队列，执行 ASR 转写 + AI 质检。
 *
 * <p>生产端在录音就绪（RECORD_STOP 回写）后 {@link #enqueue(Long)} 投递；Stream 不可用时
 * 同步降级直接执行质检。消费幂等由质检服务按 recordId 唯一 + ai_status CAS 保证。</p>
 *
 * @author ai-lawyers
 */
@Component
public class QualityTranscribeDispatcher
{
    private static final Logger log = LoggerFactory.getLogger(QualityTranscribeDispatcher.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private StreamQueueService streamQueueService;

    @Autowired
    private IAiQualityInspectionService qualityInspectionService;

    /** 质检总开关（与 ai.quality.enabled 对齐，关闭则不投递/不消费） */
    @Value("${ai.quality.enabled:true}")
    private boolean qualityEnabled;

    @PostConstruct
    public void init()
    {
        streamQueueService.registerHandler(QueueNames.QUALITY_TRANSCRIBE, payload ->
        {
            QualityTask task = MAPPER.readValue(payload, QualityTask.class);
            if (task == null || task.getRecordId() == null)
            {
                log.warn("质检队列消息缺少 recordId，丢弃：{}", payload);
                return;
            }
            qualityInspectionService.inspect(task.getRecordId());
        });
        log.info("质检转写队列消费者已注册 queue={}", QueueNames.QUALITY_TRANSCRIBE);
    }

    /**
     * 录音就绪后投递质检任务；Stream 禁用/异常时同步降级直接执行。
     *
     * @param recordId 通话记录ID
     */
    public void enqueue(Long recordId)
    {
        if (!qualityEnabled || recordId == null)
        {
            return;
        }
        try
        {
            QualityTask task = new QualityTask();
            task.setRecordId(recordId);
            String json = MAPPER.writeValueAsString(task);
            if (streamQueueService.enqueue(QueueNames.QUALITY_TRANSCRIBE, json))
            {
                return;
            }
        }
        catch (Exception e)
        {
            log.warn("质检任务投递队列失败，降级同步执行 recordId={}: {}", recordId, e.getMessage());
        }
        // 同步降级
        try
        {
            qualityInspectionService.inspect(recordId);
        }
        catch (Exception e)
        {
            log.warn("质检同步执行失败 recordId={}: {}", recordId, e.getMessage());
        }
    }

    /** 质检队列消息体 */
    public static class QualityTask
    {
        private Long recordId;

        public Long getRecordId() { return recordId; }
        public void setRecordId(Long recordId) { this.recordId = recordId; }
    }
}
