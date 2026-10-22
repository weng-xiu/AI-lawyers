package ai.lawyers.system.service.impl.lawyers.quality;

import java.io.File;
import java.nio.file.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.service.impl.lawyers.storage.RecordingUploadService;
import ai.lawyers.system.service.lawyers.storage.RecordingStorageService;
import ai.lawyers.system.service.lawyers.voice.VoiceEngineManager;
import ai.lawyers.system.service.lawyers.voice.VoiceModelEnum;
import ai.lawyers.system.service.lawyers.voice.speaker.SpeakerDiarizationService;

/**
 * 质检录音加载与转写协作类（P5 超大类拆分）。
 *
 * <p>从 {@link AiQualityInspectionServiceImpl} 抽出的"音频链路"叶子职责：
 * 录音字节加载（对象存储/本地 + 落盘就绪等待）、ASR 转写、离线声学分析、
 * 说话人分离影子链路。不反向依赖门面，无循环依赖。</p>
 *
 * @author ai-lawyers
 */
@Component
public class QualityAudioLoader
{
    private static final Logger log = LoggerFactory.getLogger(QualityAudioLoader.class);

    @Autowired
    private VoiceEngineManager voiceEngineManager;

    /** P1-7：声学规则（静默时长/整体语速，离线从录音+VAD 推导） */
    @Autowired
    private AcousticFeatureAnalyzer acousticFeatureAnalyzer;

    /** F2：录音存储抽象（本地/对象存储），为空时退化到本地文件 */
    @Autowired(required = false)
    private RecordingStorageService recordingStorageService;

    /** 录音文件基础路径（对应 FreeSWITCH recordings_dir）；默认相对路径（跨平台），由环境变量覆盖 */
    @Value("${call.recording.base-path:recordings}")
    private String recordingBasePath;

    /**
     * P0-1：质检转写独立引擎覆盖（mock/dashscope/ali/dianxin）。
     * 为空时跟随全局 voice.engine；灰度开量第一阶段可只把质检切到真实 ASR，不影响 IVR/TTS。
     */
    @Value("${ai.quality.asr-engine:}")
    private String qualityAsrEngine;

    /** P0-5：转写前等待录音落盘就绪的最长时间（秒），超时按可恢复异常重试 */
    @Value("${ai.quality.ready-timeout-seconds:60}")
    private long readyTimeoutSeconds;

    /** P0-5：录音就绪轮询间隔（毫秒） */
    @Value("${call.recording.upload.ready-poll-interval-ms:500}")
    private long readyPollIntervalMs;

    /**
     * P2-15：通话内说话人分离影子链路（仅双声道录音触发，best-effort 只打日志不落库；
     * 声纹模型缺席时整体短路，不产生 ONNX 初始化成本）。首次双声道录音到来时才构造。
     */
    private volatile SpeakerDiarizationService speakerDiarizationService;

    /** 已加载到内存的录音（字节 + 来源文件名，供转写与声学分析复用） */
    public static class LoadedAudio
    {
        public final byte[] data;
        public final String fileName;

        LoadedAudio(byte[] data, String fileName)
        {
            this.data = data;
            this.fileName = fileName;
        }
    }

    /** 读取录音文件到内存（对象存储/本地两种模式）；录音文件不存在时抛异常（队列重投） */
    public LoadedAudio loadRecording(AiCallRecord record) throws Exception
    {
        byte[] audio;
        String fileName;

        // F2：对象存储模式，优先从对象存储读取
        if (recordingStorageService != null && recordingStorageService.isObjectStorage())
        {
            String key = record.getRecordFile();
            if (StringUtils.isEmpty(key))
            {
                throw new RecordingNotReadyException("录音文件尚未就绪 recordId=" + record.getRecordId());
            }
            try (java.io.InputStream in = recordingStorageService.load(key))
            {
                if (in == null)
                {
                    throw new RecordingNotReadyException("录音文件尚未就绪 recordId=" + record.getRecordId());
                }
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1)
                {
                    bos.write(buf, 0, n);
                }
                audio = bos.toByteArray();
            }
            fileName = key;
        }
        else
        {
            File file = resolveRecordingFile(record);
            if (file == null)
            {
                throw new RecordingNotReadyException("录音文件尚未就绪 recordId=" + record.getRecordId());
            }
            // P0-5：等待落盘完成（存在 + 长度 > 0 + 连续两次轮询长度不变），避免读到不完整音频
            long timeoutMs = Math.max(0L, readyTimeoutSeconds) * 1000L;
            if (!RecordingUploadService.waitUntilReady(file, timeoutMs, Math.max(1L, readyPollIntervalMs)))
            {
                throw new RecordingNotReadyException("录音文件尚未就绪 recordId=" + record.getRecordId());
            }
            audio = Files.readAllBytes(file.toPath());
            fileName = file.getName();
        }
        return new LoadedAudio(audio, fileName);
    }

    /** 用质检 ASR 引擎转写已加载的录音字节 */
    public String transcribeAudio(AiCallRecord record, LoadedAudio loaded) throws Exception
    {
        String format = guessFormat(loaded.fileName);
        VoiceModelEnum engine = StringUtils.isNotEmpty(qualityAsrEngine)
                ? VoiceModelEnum.of(qualityAsrEngine)
                : voiceEngineManager.defaultEngine();
        String text = voiceEngineManager.transcribe(engine, loaded.data, format, 16000, null);
        return text == null ? "" : text.trim();
    }

    /** 离线声学分析：仅处理 WAV（PCM），非 WAV 或解析失败时跳过，不阻断质检 */
    public AcousticFeatureAnalyzer.AcousticResult analyzeAcoustic(LoadedAudio loaded, String transcript)
    {
        try
        {
            if (!"wav".equals(guessFormat(loaded.fileName)))
            {
                return null;
            }
            WavPcmDecoder.Pcm pcm = WavPcmDecoder.decode(loaded.data);
            // P2-15：双声道录音额外跑一次通话内说话人分离（影子链路，只打日志不落库；
            // 单声道/模型缺席时 service 内部直接短路，不影响评分）
            runDiarizationShadow(loaded);
            return acousticFeatureAnalyzer.analyze(pcm.getSampleRate(), pcm.getData(), transcript);
        }
        catch (Exception e)
        {
            log.info("声学规则跳过（非 PCM WAV 或解析失败）：{}", e.getMessage());
            return null;
        }
    }

    /**
     * P2-15：通话内说话人分离影子执行——仅双声道 WAV 触发，结果只打结构化日志，
     * 不落库、不与号码关联（跨通话建档待 PIA/法务评估）；任何异常不外抛。
     */
    private void runDiarizationShadow(LoadedAudio loaded)
    {
        try
        {
            WavPcmDecoder.ChannelPcm channels = WavPcmDecoder.decodeChannels(loaded.data);
            if (channels.getChannelCount() < 2)
            {
                return;
            }
            SpeakerDiarizationService service = speakerDiarizationService;
            if (service == null)
            {
                synchronized (this)
                {
                    service = speakerDiarizationService;
                    if (service == null)
                    {
                        service = new SpeakerDiarizationService();
                        speakerDiarizationService = service;
                    }
                }
            }
            SpeakerDiarizationService.Result result = service.diarizeStereo(loaded.data);
            if (result.getSpeakerCount() > 0)
            {
                StringBuilder roles = new StringBuilder();
                for (SpeakerDiarizationService.Speaker sp : result.getSpeakers())
                {
                    if (roles.length() > 0)
                    {
                        roles.append(',');
                    }
                    roles.append(sp.getRoleName()).append('=').append(sp.getSegmentCount()).append("段/")
                            .append(sp.getDurationMs()).append("ms/avgConf=")
                            .append(String.format(java.util.Locale.ROOT, "%.3f", sp.getAvgConfidence()));
                }
                log.info("通话内说话人分离（影子，不落库）file={} sampleRate={} speakers={} [{}]",
                        loaded.fileName, result.getSampleRate(), result.getSpeakerCount(), roles);
            }
        }
        catch (Exception e)
        {
            log.info("通话内说话人分离影子跳过：{}", e.getMessage());
        }
    }

    /** 解析录音文件路径（绝对路径直接用，相对路径拼 recordings_dir，兼容 $${recordings_dir} 前缀） */
    private File resolveRecordingFile(AiCallRecord record)
    {
        if (record == null || StringUtils.isEmpty(record.getRecordFile()))
        {
            return null;
        }
        String path = record.getRecordFile().trim();
        File file = new File(path);
        if (file.isAbsolute())
        {
            return file;
        }
        if (path.startsWith("$${recordings_dir}") || path.startsWith("${recordings_dir}"))
        {
            path = path.substring(path.indexOf('}') + 1);
            if (path.startsWith("/") || path.startsWith("\\"))
            {
                path = path.substring(1);
            }
        }
        return new File(new File(recordingBasePath), path);
    }

    private String guessFormat(String fileName)
    {
        String name = fileName == null ? "" : fileName.toLowerCase();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "wav";
    }
}
