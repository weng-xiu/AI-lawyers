package ai.lawyers.system.service.impl.lawyers.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiRiskWarning;
import ai.lawyers.system.domain.lawyers.AiRiskWarningRule;
import ai.lawyers.system.domain.lawyers.quality.AiQualityInspection;
import ai.lawyers.system.mapper.lawyers.AiCallAgentStatusMapper;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.quality.AiQualityInspectionMapper;
import ai.lawyers.system.service.lawyers.IAiRiskWarningRuleService;
import ai.lawyers.system.service.lawyers.IAiRiskWarningService;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import ai.lawyers.system.service.lawyers.queue.MessageNotifyDispatcher;
import ai.lawyers.system.service.lawyers.summary.IAiCallSummaryService;

/**
 * 智能质检编排门面测试：入检采样/幂等、转写与评分落库、风险与小结联动、复核与申诉闭环。
 */
class AiQualityInspectionServiceImplTest
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AiQualityInspectionServiceImpl service;
    private AiQualityInspectionMapper inspectionMapper;
    private AiCallRecordMapper callRecordMapper;
    private IAiRiskWarningService riskWarningService;
    private IAiRiskWarningRuleService riskWarningRuleService;
    private HotlineMetrics metrics;
    private MessageNotifyDispatcher messageNotifyDispatcher;
    private AiCallAgentStatusMapper agentStatusMapper;
    private QualityAudioLoader audioLoader;
    private QualityScoringEngine scoringEngine;
    private IAiCallSummaryService callSummaryService;

    @BeforeEach
    void setUp()
    {
        service = new AiQualityInspectionServiceImpl();
        inspectionMapper = mock(AiQualityInspectionMapper.class);
        callRecordMapper = mock(AiCallRecordMapper.class);
        riskWarningService = mock(IAiRiskWarningService.class);
        riskWarningRuleService = mock(IAiRiskWarningRuleService.class);
        metrics = mock(HotlineMetrics.class);
        messageNotifyDispatcher = mock(MessageNotifyDispatcher.class);
        agentStatusMapper = mock(AiCallAgentStatusMapper.class);
        audioLoader = mock(QualityAudioLoader.class);
        scoringEngine = mock(QualityScoringEngine.class);
        callSummaryService = mock(IAiCallSummaryService.class);
        ReflectionTestUtils.setField(service, "inspectionMapper", inspectionMapper);
        ReflectionTestUtils.setField(service, "callRecordMapper", callRecordMapper);
        ReflectionTestUtils.setField(service, "riskWarningService", riskWarningService);
        ReflectionTestUtils.setField(service, "riskWarningRuleService", riskWarningRuleService);
        ReflectionTestUtils.setField(service, "metrics", metrics);
        ReflectionTestUtils.setField(service, "messageNotifyDispatcher", messageNotifyDispatcher);
        ReflectionTestUtils.setField(service, "agentStatusMapper", agentStatusMapper);
        ReflectionTestUtils.setField(service, "audioLoader", audioLoader);
        ReflectionTestUtils.setField(service, "scoringEngine", scoringEngine);
        ReflectionTestUtils.setField(service, "callSummaryService", callSummaryService);
        ReflectionTestUtils.setField(service, "qualityEnabled", true);
        ReflectionTestUtils.setField(service, "autoOnHangup", true);
        ReflectionTestUtils.setField(service, "sampleRate", 1.0d);
        ReflectionTestUtils.setField(service, "summaryAutoOnInspect", true);
    }

    private static AiQualityInspection pending(Long inspectionId, Long recordId)
    {
        AiQualityInspection ins = new AiQualityInspection();
        ins.setInspectionId(inspectionId);
        ins.setRecordId(recordId);
        ins.setAiStatus("0");
        return ins;
    }

    private static AiCallRecord record(Long recordId, String transcript)
    {
        AiCallRecord r = new AiCallRecord();
        r.setRecordId(recordId);
        r.setAgentId(3L);
        r.setCallerNumber("13800000000");
        r.setCallerName("张三");
        r.setTranscript(transcript);
        return r;
    }

    private static QualityScoringEngine.AiScore score(String dimensionJson, String violationJson) throws Exception
    {
        QualityScoringEngine.AiScore s = new QualityScoringEngine.AiScore();
        s.totalScore = new BigDecimal("91.5");
        s.dimensionJson = dimensionJson;
        s.violationJson = violationJson;
        s.violationNodes = violationJson == null ? null : MAPPER.readTree(violationJson);
        s.remark = "总体良好";
        return s;
    }

    // ------------------------------------------------------------ 入检

    @Test
    void submit_disabled_skips()
    {
        ReflectionTestUtils.setField(service, "qualityEnabled", false);

        service.submitForRecord(1L);

        verifyNoInteractions(inspectionMapper, callRecordMapper);
    }

    @Test
    void submit_autoDisabled_skips()
    {
        ReflectionTestUtils.setField(service, "autoOnHangup", false);

        service.submitForRecord(1L);

        verifyNoInteractions(inspectionMapper, callRecordMapper);
    }

    @Test
    void submit_nullRecordId_skips()
    {
        service.submitForRecord(null);

        verifyNoInteractions(inspectionMapper, callRecordMapper);
    }

    @Test
    void submit_existingInspection_idempotent()
    {
        when(inspectionMapper.selectByRecordId(1L)).thenReturn(pending(10L, 1L));

        service.submitForRecord(1L);

        verify(inspectionMapper, never()).insertAiQualityInspection(any());
    }

    @Test
    void submit_recordMissing_noInsert()
    {
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(null);

        service.submitForRecord(1L);

        verify(inspectionMapper, never()).insertAiQualityInspection(any());
    }

    @Test
    void submit_samplingMiss_skips()
    {
        ReflectionTestUtils.setField(service, "sampleRate", 0.0d);

        service.submitForRecord(1L);

        verifyNoInteractions(callRecordMapper);
        verify(inspectionMapper, never()).insertAiQualityInspection(any());
    }

    @Test
    void submit_success_insertsFromCallRecord()
    {
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(record(1L, null));

        service.submitForRecord(1L);

        verify(inspectionMapper).insertAiQualityInspection(argThat(ins ->
                Long.valueOf(1L).equals(ins.getRecordId())
                        && "1".equals(ins.getSourceType())
                        && "0".equals(ins.getAiStatus())
                        && Long.valueOf(3L).equals(ins.getAgentId())
                        && "13800000000".equals(ins.getCallerNumber())
                        && "quality-auto".equals(ins.getCreateBy())));
    }

    // ------------------------------------------------------------ 质检执行

    @Test
    void inspect_nullRecordId_skips()
    {
        service.inspect(null);

        verifyNoInteractions(inspectionMapper);
    }

    @Test
    void inspect_completed_idempotent()
    {
        AiQualityInspection ins = pending(10L, 1L);
        ins.setAiStatus("2");
        when(inspectionMapper.selectByRecordId(1L)).thenReturn(ins);

        service.inspect(1L);

        verify(inspectionMapper, never()).casAiStatus(any(), any(), any());
    }

    @Test
    void inspect_inProgress_idempotent()
    {
        AiQualityInspection ins = pending(10L, 1L);
        ins.setAiStatus("1");
        when(inspectionMapper.selectByRecordId(1L)).thenReturn(ins);

        service.inspect(1L);

        verify(inspectionMapper, never()).casAiStatus(any(), any(), any());
    }

    @Test
    void inspect_noInspection_rebuildsBeforeContinue()
    {
        AiQualityInspection ins = pending(10L, 1L);
        // 首次查无记录（队列消息早到），submit 幂等判断时已可见
        when(inspectionMapper.selectByRecordId(1L)).thenReturn(null, ins);
        when(inspectionMapper.casAiStatus(10L, "0", "1")).thenReturn(1);
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(null);

        service.inspect(1L);

        verify(inspectionMapper, never()).insertAiQualityInspection(any());
        verify(callRecordMapper).selectAiCallRecordByRecordId(1L);
    }

    @Test
    void inspect_casConflict_skipsScoring()
    {
        when(inspectionMapper.selectByRecordId(1L)).thenReturn(pending(10L, 1L));
        when(inspectionMapper.casAiStatus(10L, "0", "1")).thenReturn(0);

        service.inspect(1L);

        verifyNoInteractions(callRecordMapper, scoringEngine);
    }

    @Test
    void inspect_recordMissing_marksFailed()
    {
        when(inspectionMapper.selectByRecordId(1L)).thenReturn(pending(10L, 1L));
        when(inspectionMapper.casAiStatus(10L, "0", "1")).thenReturn(1);
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(null);

        service.inspect(1L);

        verify(inspectionMapper).updateAiQualityInspection(argThat(u ->
                Long.valueOf(10L).equals(u.getInspectionId())
                        && "3".equals(u.getAiStatus())
                        && u.getAiRemark() != null && u.getAiRemark().contains("话单不存在")));
        verify(metrics).incrementQuality("fail");
    }

    @Test
    void inspect_existingTranscript_scoresWithoutAsr() throws Exception
    {
        AiCallRecord rec = record(1L, "您好，这里是12348热线");
        when(inspectionMapper.selectByRecordId(1L)).thenReturn(pending(10L, 1L));
        when(inspectionMapper.casAiStatus(10L, "0", "1")).thenReturn(1);
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(rec);
        when(scoringEngine.scoreByDualEngine(eq("您好，这里是12348热线"), eq(rec), eq(null)))
                .thenReturn(score("{\"serviceNorm\":90,\"answerAccuracy\":85,"
                        + "\"emotionAttitude\":88,\"compliance\":95}", "[]"));

        service.inspect(1L);

        verifyNoInteractions(audioLoader);
        verify(callRecordMapper, never()).updateRecordingInfo(any());
        verify(inspectionMapper).updateAiQualityInspection(argThat(u ->
                Long.valueOf(10L).equals(u.getInspectionId())
                        && "2".equals(u.getAiStatus())
                        && "您好，这里是12348热线".equals(u.getTranscript())
                        && new BigDecimal("91.5").equals(u.getTotalScore())
                        && "quality-ai".equals(u.getUpdateBy())));
        verify(metrics).incrementQuality("success");
        verify(callSummaryService).generateSummary(1L, false);
    }

    @Test
    void inspect_noTranscript_transcribesAndWritesBack() throws Exception
    {
        AiCallRecord rec = record(1L, null);
        QualityAudioLoader.LoadedAudio audio = new QualityAudioLoader.LoadedAudio(new byte[] { 1, 2 }, "a.wav");
        when(inspectionMapper.selectByRecordId(1L)).thenReturn(pending(10L, 1L));
        when(inspectionMapper.casAiStatus(10L, "0", "1")).thenReturn(1);
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(rec);
        when(audioLoader.loadRecording(rec)).thenReturn(audio);
        when(audioLoader.transcribeAudio(rec, audio)).thenReturn(" 转写内容 ");
        when(audioLoader.analyzeAcoustic(audio, " 转写内容 ")).thenReturn(null);
        when(scoringEngine.scoreByDualEngine(" 转写内容 ", rec, null))
                .thenReturn(score("{\"serviceNorm\":90,\"answerAccuracy\":85,"
                        + "\"emotionAttitude\":88,\"compliance\":95}", "[]"));

        service.inspect(1L);

        verify(callRecordMapper).updateRecordingInfo(argThat(u ->
                Long.valueOf(1L).equals(u.getRecordId())
                        && " 转写内容 ".equals(u.getTranscript())
                        && Integer.valueOf(2).equals(u.getAsrStatus())));
        verify(audioLoader).analyzeAcoustic(audio, " 转写内容 ");
        verify(inspectionMapper).updateAiQualityInspection(argThat(u -> "2".equals(u.getAiStatus())));
    }

    @Test
    void inspect_emptyTranscript_marksFailed()
    {
        AiCallRecord rec = record(1L, null);
        QualityAudioLoader.LoadedAudio audio = new QualityAudioLoader.LoadedAudio(new byte[] { 1 }, "a.wav");
        when(inspectionMapper.selectByRecordId(1L)).thenReturn(pending(10L, 1L));
        when(inspectionMapper.casAiStatus(10L, "0", "1")).thenReturn(1);
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(rec);
        try
        {
            when(audioLoader.loadRecording(rec)).thenReturn(audio);
            when(audioLoader.transcribeAudio(rec, audio)).thenReturn("");
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }

        service.inspect(1L);

        verify(inspectionMapper).updateAiQualityInspection(argThat(u ->
                "3".equals(u.getAiStatus())
                        && u.getAiRemark() != null && u.getAiRemark().contains("转写为空")));
        verify(callRecordMapper, never()).updateRecordingInfo(any());
        verifyNoInteractions(scoringEngine, callSummaryService);
        verify(metrics).incrementQuality("fail");
    }

    @Test
    void inspect_recordingNotReady_revertsToPending()
    {
        AiCallRecord rec = record(1L, null);
        when(inspectionMapper.selectByRecordId(1L)).thenReturn(pending(10L, 1L));
        when(inspectionMapper.casAiStatus(10L, "0", "1")).thenReturn(1);
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(rec);
        try
        {
            when(audioLoader.loadRecording(rec)).thenThrow(new RecordingNotReadyException("录音未就绪"));
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }

        service.inspect(1L);

        verify(inspectionMapper).casAiStatus(10L, "1", "0");
        verify(inspectionMapper, never()).updateAiQualityInspection(any());
        verify(metrics, never()).incrementQuality(anyString());
    }

    @Test
    void inspect_scoringThrows_marksFailed() throws Exception
    {
        AiCallRecord rec = record(1L, "转写");
        when(inspectionMapper.selectByRecordId(1L)).thenReturn(pending(10L, 1L));
        when(inspectionMapper.casAiStatus(10L, "0", "1")).thenReturn(1);
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(rec);
        when(scoringEngine.scoreByDualEngine(any(), any(), any()))
                .thenThrow(new IllegalStateException("模型序列化失败"));

        service.inspect(1L);

        verify(inspectionMapper).updateAiQualityInspection(argThat(u ->
                "3".equals(u.getAiStatus())
                        && u.getAiRemark() != null && u.getAiRemark().contains("模型序列化失败")));
        verify(metrics).incrementQuality("fail");
    }

    @Test
    void inspect_failureRemark_longTruncatedTo900()
    {
        AiCallRecord rec = record(1L, "转写");
        String longMsg = "x".repeat(1200);
        when(inspectionMapper.selectByRecordId(1L)).thenReturn(pending(10L, 1L));
        when(inspectionMapper.casAiStatus(10L, "0", "1")).thenReturn(1);
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(rec);
        when(scoringEngine.scoreByDualEngine(any(), any(), any()))
                .thenThrow(new IllegalStateException(longMsg));

        service.inspect(1L);

        verify(inspectionMapper).updateAiQualityInspection(argThat(u ->
                "3".equals(u.getAiStatus()) && u.getAiRemark() != null && u.getAiRemark().length() == 900));
    }

    // ------------------------------------------------------------ 风险联动

    @Test
    void inspect_aiViolation_createsHighRiskWarning()
    {
        AiCallRecord rec = record(1L, "我保证这个案子一定能赢");
        String dims = "{\"serviceNorm\":90,\"answerAccuracy\":80,\"emotionAttitude\":85,\"compliance\":70}";
        String violations = "[{\"keyword\":\"保证胜诉\",\"reason\":\"违规承诺\"}]";
        when(inspectionMapper.selectByRecordId(1L)).thenReturn(pending(10L, 1L));
        when(inspectionMapper.casAiStatus(10L, "0", "1")).thenReturn(1);
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(rec);
        try
        {
            when(scoringEngine.scoreByDualEngine(any(), any(), any())).thenReturn(score(dims, violations));
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
        when(riskWarningService.insertAiRiskWarning(any())).thenAnswer(inv -> {
            inv.getArgument(0, AiRiskWarning.class).setWarningId(77L);
            return 1;
        });

        service.inspect(1L);

        verify(riskWarningService).insertAiRiskWarning(argThat(w ->
                "合规风险".equals(w.getWarningType())
                        && "1".equals(w.getWarningLevel())
                        && "通话".equals(w.getSourceType())
                        && Long.valueOf(1L).equals(w.getSourceId())
                        && w.getContent().contains("AI判定问题数：1")));
        verify(inspectionMapper).updateRiskWarningId(10L, 77L);
    }

    @Test
    void inspect_keywordOnly_createsMediumSensitiveWarning()
    {
        AiCallRecord rec = record(1L, "我要投诉你们");
        AiRiskWarningRule rule = new AiRiskWarningRule();
        rule.setIsEnabled("1");
        rule.setKeywords("投诉,起诉");
        when(inspectionMapper.selectByRecordId(1L)).thenReturn(pending(10L, 1L));
        when(inspectionMapper.casAiStatus(10L, "0", "1")).thenReturn(1);
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(rec);
        when(riskWarningRuleService.selectAiRiskWarningRuleList(any())).thenReturn(Arrays.asList(rule));
        try
        {
            when(scoringEngine.scoreByDualEngine(any(), any(), any()))
                    .thenReturn(score("{\"serviceNorm\":90,\"answerAccuracy\":85,"
                            + "\"emotionAttitude\":88,\"compliance\":95}", "[]"));
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
        when(riskWarningService.insertAiRiskWarning(any())).thenAnswer(inv -> {
            inv.getArgument(0, AiRiskWarning.class).setWarningId(88L);
            return 1;
        });

        service.inspect(1L);

        verify(riskWarningService).insertAiRiskWarning(argThat(w ->
                "敏感词".equals(w.getWarningType())
                        && "2".equals(w.getWarningLevel())
                        && w.getContent().contains("投诉")
                        && w.getContent().contains("质检ID：10")));
        verify(inspectionMapper).updateRiskWarningId(10L, 88L);
    }

    @Test
    void inspect_complianceLow_createsHighRiskWarning()
    {
        AiCallRecord rec = record(1L, "普通对话");
        when(inspectionMapper.selectByRecordId(1L)).thenReturn(pending(10L, 1L));
        when(inspectionMapper.casAiStatus(10L, "0", "1")).thenReturn(1);
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(rec);
        try
        {
            when(scoringEngine.scoreByDualEngine(any(), any(), any()))
                    .thenReturn(score("{\"serviceNorm\":90,\"answerAccuracy\":85,"
                            + "\"emotionAttitude\":88,\"compliance\":50}", "[]"));
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }

        service.inspect(1L);

        verify(riskWarningService).insertAiRiskWarning(argThat(w ->
                "合规风险".equals(w.getWarningType()) && "1".equals(w.getWarningLevel())));
    }

    @Test
    void inspect_noRiskSignal_noWarning()
    {
        AiCallRecord rec = record(1L, "普通对话");
        when(inspectionMapper.selectByRecordId(1L)).thenReturn(pending(10L, 1L));
        when(inspectionMapper.casAiStatus(10L, "0", "1")).thenReturn(1);
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(rec);
        try
        {
            when(scoringEngine.scoreByDualEngine(any(), any(), any()))
                    .thenReturn(score("{\"serviceNorm\":90,\"answerAccuracy\":85,"
                            + "\"emotionAttitude\":88,\"compliance\":95}", "[]"));
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }

        service.inspect(1L);

        verify(riskWarningService, never()).insertAiRiskWarning(any());
        verify(inspectionMapper, never()).updateRiskWarningId(any(), any());
    }

    @Test
    void inspect_existingWarningId_skipsDuplicate()
    {
        AiQualityInspection ins = pending(10L, 1L);
        ins.setRiskWarningId(55L);
        AiCallRecord rec = record(1L, "我要投诉");
        when(inspectionMapper.selectByRecordId(1L)).thenReturn(ins);
        when(inspectionMapper.casAiStatus(10L, "0", "1")).thenReturn(1);
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(rec);
        try
        {
            when(scoringEngine.scoreByDualEngine(any(), any(), any()))
                    .thenReturn(score("{\"compliance\":80}",
                            "[{\"keyword\":\"投诉\",\"reason\":\"敏感\"}]"));
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }

        service.inspect(1L);

        verify(riskWarningService, never()).insertAiRiskWarning(any());
    }

    @Test
    void inspect_riskWarningThrows_doesNotFailInspection()
    {
        AiCallRecord rec = record(1L, "我要投诉");
        when(inspectionMapper.selectByRecordId(1L)).thenReturn(pending(10L, 1L));
        when(inspectionMapper.casAiStatus(10L, "0", "1")).thenReturn(1);
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(rec);
        try
        {
            when(scoringEngine.scoreByDualEngine(any(), any(), any()))
                    .thenReturn(score("{\"compliance\":80}",
                            "[{\"keyword\":\"投诉\",\"reason\":\"敏感\"}]"));
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
        when(riskWarningService.insertAiRiskWarning(any())).thenThrow(new IllegalStateException("db down"));

        service.inspect(1L);

        verify(inspectionMapper).updateAiQualityInspection(argThat(u -> "2".equals(u.getAiStatus())));
        verify(metrics).incrementQuality("success");
    }

    // ------------------------------------------------------------ 小结联动

    @Test
    void inspect_summaryDisabled_skips()
    {
        ReflectionTestUtils.setField(service, "summaryAutoOnInspect", false);
        stubHappyPath();

        service.inspect(1L);

        verifyNoInteractions(callSummaryService);
    }

    @Test
    void inspect_summaryThrows_swallowed()
    {
        stubHappyPath();
        when(callSummaryService.generateSummary(1L, false)).thenThrow(new IllegalStateException("小结失败"));

        service.inspect(1L);

        verify(inspectionMapper).updateAiQualityInspection(argThat(u -> "2".equals(u.getAiStatus())));
    }

    private void stubHappyPath()
    {
        AiCallRecord rec = record(1L, "转写");
        when(inspectionMapper.selectByRecordId(1L)).thenReturn(pending(10L, 1L));
        when(inspectionMapper.casAiStatus(10L, "0", "1")).thenReturn(1);
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(rec);
        try
        {
            when(scoringEngine.scoreByDualEngine(any(), any(), any()))
                    .thenReturn(score("{\"serviceNorm\":90,\"answerAccuracy\":85,"
                            + "\"emotionAttitude\":88,\"compliance\":95}", "[]"));
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------ 人工复核

    @Test
    void review_rejected_notifiesAgent()
    {
        AiQualityInspection review = new AiQualityInspection();
        review.setInspectionId(10L);
        review.setReviewStatus("2");
        review.setReviewRemark("话术不规范");
        review.setReviewerName("组长");
        AiQualityInspection full = pending(10L, 1L);
        full.setAgentId(3L);
        AiCallAgentStatus agent = new AiCallAgentStatus();
        agent.setUserId(7L);
        when(inspectionMapper.reviewAiQualityInspection(review)).thenReturn(1);
        when(inspectionMapper.selectAiQualityInspectionByInspectionId(10L)).thenReturn(full);
        when(agentStatusMapper.selectAiCallAgentStatusByAgentId(3L)).thenReturn(agent);

        int rows = service.review(review);

        assertThat(rows).isEqualTo(1);
        verify(messageNotifyDispatcher).notify(eq(7L), eq("4"), eq("质检结果驳回，请整改"),
                argThat(content -> content.contains("质检ID 10") && content.contains("话术不规范")),
                eq("quality"), eq(10L), eq("组长"));
        assertThat(review.getReviewTime()).isNotNull();
    }

    @Test
    void review_approved_noNotify()
    {
        AiQualityInspection review = new AiQualityInspection();
        review.setInspectionId(10L);
        review.setReviewStatus("1");
        when(inspectionMapper.reviewAiQualityInspection(review)).thenReturn(1);

        service.review(review);

        verifyNoInteractions(messageNotifyDispatcher, agentStatusMapper);
    }

    @Test
    void review_rowsZero_noNotify()
    {
        AiQualityInspection review = new AiQualityInspection();
        review.setInspectionId(10L);
        review.setReviewStatus("2");
        when(inspectionMapper.reviewAiQualityInspection(review)).thenReturn(0);

        service.review(review);

        verifyNoInteractions(messageNotifyDispatcher);
    }

    @Test
    void review_agentUnbound_noNotify()
    {
        AiQualityInspection review = new AiQualityInspection();
        review.setInspectionId(10L);
        review.setReviewStatus("2");
        when(inspectionMapper.reviewAiQualityInspection(review)).thenReturn(1);
        when(inspectionMapper.selectAiQualityInspectionByInspectionId(10L)).thenReturn(null);

        service.review(review);

        verifyNoInteractions(messageNotifyDispatcher);
    }

    @Test
    void review_notifyThrows_stillReturnsRows()
    {
        AiQualityInspection review = new AiQualityInspection();
        review.setInspectionId(10L);
        review.setReviewStatus("2");
        AiQualityInspection full = pending(10L, 1L);
        full.setAgentId(3L);
        AiCallAgentStatus agent = new AiCallAgentStatus();
        agent.setUserId(7L);
        when(inspectionMapper.reviewAiQualityInspection(review)).thenReturn(1);
        when(inspectionMapper.selectAiQualityInspectionByInspectionId(10L)).thenReturn(full);
        when(agentStatusMapper.selectAiCallAgentStatusByAgentId(3L)).thenReturn(agent);
        doThrow(new IllegalStateException("mq down")).when(messageNotifyDispatcher)
                .notify(any(), any(), any(), any(), any(), any(), any());

        assertThat(service.review(review)).isEqualTo(1);
    }

    // ------------------------------------------------------------ 申诉

    @Test
    void appeal_nullInspectionId_throws()
    {
        assertThatThrownBy(() -> service.appeal(null, "理由", 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("质检ID不能为空");
    }

    @Test
    void appeal_nullUser_throws()
    {
        assertThatThrownBy(() -> service.appeal(10L, "理由", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无法识别当前坐席身份");
    }

    @Test
    void appeal_blankReason_throws()
    {
        assertThatThrownBy(() -> service.appeal(10L, "   ", 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("申诉理由不能为空");
    }

    @Test
    void appeal_notCompleted_throws()
    {
        AiQualityInspection full = pending(10L, 1L);
        full.setAiStatus("1");
        when(inspectionMapper.selectAiQualityInspectionByInspectionId(10L)).thenReturn(full);

        assertThatThrownBy(() -> service.appeal(10L, "理由", 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅 AI 质检已完成的记录可发起申诉");
    }

    @Test
    void appeal_notOwner_throws()
    {
        AiQualityInspection full = pending(10L, 1L);
        full.setAiStatus("2");
        full.setAgentId(3L);
        AiCallAgentStatus agent = new AiCallAgentStatus();
        agent.setUserId(8L);
        when(inspectionMapper.selectAiQualityInspectionByInspectionId(10L)).thenReturn(full);
        when(agentStatusMapper.selectAiCallAgentStatusByAgentId(3L)).thenReturn(agent);

        assertThatThrownBy(() -> service.appeal(10L, "理由", 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅被检坐席本人可发起申诉");
    }

    @Test
    void appeal_success_submits()
    {
        AiQualityInspection full = pending(10L, 1L);
        full.setAiStatus("2");
        full.setAgentId(3L);
        AiCallAgentStatus agent = new AiCallAgentStatus();
        agent.setUserId(7L);
        when(inspectionMapper.selectAiQualityInspectionByInspectionId(10L)).thenReturn(full);
        when(agentStatusMapper.selectAiCallAgentStatusByAgentId(3L)).thenReturn(agent);
        when(inspectionMapper.appeal(10L, "评分有误")).thenReturn(1);

        assertThat(service.appeal(10L, " 评分有误 ", 7L)).isEqualTo(1);
        verify(inspectionMapper).appeal(10L, "评分有误");
    }

    @Test
    void appeal_rowsZero_throwsDuplicate()
    {
        AiQualityInspection full = pending(10L, 1L);
        full.setAiStatus("2");
        full.setAgentId(3L);
        AiCallAgentStatus agent = new AiCallAgentStatus();
        agent.setUserId(7L);
        when(inspectionMapper.selectAiQualityInspectionByInspectionId(10L)).thenReturn(full);
        when(agentStatusMapper.selectAiCallAgentStatusByAgentId(3L)).thenReturn(agent);
        when(inspectionMapper.appeal(10L, "理由")).thenReturn(0);

        assertThatThrownBy(() -> service.appeal(10L, "理由", 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("请勿重复提交");
    }

    // ------------------------------------------------------------ 申诉复核

    @Test
    void appealReview_nullId_throws()
    {
        assertThatThrownBy(() -> service.appealReview(new AiQualityInspection()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("质检ID不能为空");
    }

    @Test
    void appealReview_invalidResult_throws()
    {
        AiQualityInspection review = new AiQualityInspection();
        review.setInspectionId(10L);
        review.setAppealStatus("1");

        assertThatThrownBy(() -> service.appealReview(review))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只能为 2维持 或 3改分");
    }

    @Test
    void appealReview_changeWithoutScore_throws()
    {
        AiQualityInspection review = new AiQualityInspection();
        review.setInspectionId(10L);
        review.setAppealStatus("3");

        assertThatThrownBy(() -> service.appealReview(review))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须填写调整分数");
    }

    @Test
    void appealReview_scoreOutOfRange_throws()
    {
        AiQualityInspection review = new AiQualityInspection();
        review.setInspectionId(10L);
        review.setAppealStatus("3");
        review.setAdjustedScore(new BigDecimal("100.1"));

        assertThatThrownBy(() -> service.appealReview(review))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0-100");
    }

    @Test
    void appealReview_maintain_clearsAdjustedScore()
    {
        AiQualityInspection review = new AiQualityInspection();
        review.setInspectionId(10L);
        review.setAppealStatus("2");
        review.setAdjustedScore(new BigDecimal("88"));
        when(inspectionMapper.appealReview(review)).thenReturn(1);

        service.appealReview(review);

        verify(inspectionMapper).appealReview(argThat(r -> r.getAdjustedScore() == null));
    }

    @Test
    void appealReview_rowsZero_throws()
    {
        AiQualityInspection review = new AiQualityInspection();
        review.setInspectionId(10L);
        review.setAppealStatus("2");
        when(inspectionMapper.appealReview(review)).thenReturn(0);

        assertThatThrownBy(() -> service.appealReview(review))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不在待复核状态");
    }

    @Test
    void appealReview_changedScore_notifiesAgent()
    {
        AiQualityInspection review = new AiQualityInspection();
        review.setInspectionId(10L);
        review.setAppealStatus("3");
        review.setAdjustedScore(new BigDecimal("88"));
        review.setReviewerName("组长");
        AiQualityInspection full = pending(10L, 1L);
        full.setAgentId(3L);
        AiCallAgentStatus agent = new AiCallAgentStatus();
        agent.setUserId(7L);
        when(inspectionMapper.appealReview(review)).thenReturn(1);
        when(inspectionMapper.selectAiQualityInspectionByInspectionId(10L)).thenReturn(full);
        when(agentStatusMapper.selectAiCallAgentStatusByAgentId(3L)).thenReturn(agent);

        int rows = service.appealReview(review);

        assertThat(rows).isEqualTo(1);
        verify(messageNotifyDispatcher).notify(eq(7L), eq("4"), eq("质检申诉成立，已改分"),
                argThat(content -> content.contains("88")), eq("quality"), eq(10L), eq("组长"));
    }

    @Test
    void appealReview_agentResolveThrows_noNotify()
    {
        AiQualityInspection review = new AiQualityInspection();
        review.setInspectionId(10L);
        review.setAppealStatus("2");
        when(inspectionMapper.appealReview(review)).thenReturn(1);
        when(inspectionMapper.selectAiQualityInspectionByInspectionId(10L))
                .thenThrow(new IllegalStateException("db down"));

        assertThat(service.appealReview(review)).isEqualTo(1);
        verifyNoInteractions(messageNotifyDispatcher);
    }

    @Test
    void selectQueries_delegate()
    {
        AiQualityInspection query = new AiQualityInspection();
        when(inspectionMapper.selectAiQualityInspectionList(query)).thenReturn(Collections.emptyList());
        when(inspectionMapper.selectAiQualityInspectionByInspectionId(10L)).thenReturn(pending(10L, 1L));

        assertThat(service.selectInspectionList(query)).isEmpty();
        assertThat(service.selectInspectionById(10L).getInspectionId()).isEqualTo(10L);
    }
}
