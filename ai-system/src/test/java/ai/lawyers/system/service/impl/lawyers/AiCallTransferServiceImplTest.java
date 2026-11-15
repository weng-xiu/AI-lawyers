package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallTransfer;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.AiCallTransferMapper;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;

/**
 * 呼叫转接服务单测。
 *
 * <p>覆盖转接记录查询与删除委托，以及转接落库后话单状态、转接号和归属坐席的同步更新。</p>
 *
 * @author ai-lawyers
 */
class AiCallTransferServiceImplTest
{
    private AiCallTransferServiceImpl service;

    @Mock private AiCallTransferMapper aiCallTransferMapper;
    @Mock private AiCallRecordMapper aiCallRecordMapper;
    @Mock private IAiCallRecordService aiCallRecordService;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiCallTransferServiceImpl();
        ReflectionTestUtils.setField(service, "aiCallTransferMapper", aiCallTransferMapper);
        ReflectionTestUtils.setField(service, "aiCallRecordMapper", aiCallRecordMapper);
        ReflectionTestUtils.setField(service, "aiCallRecordService", aiCallRecordService);
    }

    @Test
    void selectAiCallTransferByTransferId_delegates()
    {
        AiCallTransfer expected = new AiCallTransfer();
        when(aiCallTransferMapper.selectAiCallTransferByTransferId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiCallTransferByTransferId(1L));
    }

    @Test
    void selectAiCallTransferList_delegates()
    {
        AiCallTransfer query = new AiCallTransfer();
        List<AiCallTransfer> list = Collections.singletonList(new AiCallTransfer());
        when(aiCallTransferMapper.selectAiCallTransferList(query)).thenReturn(list);

        assertSame(list, service.selectAiCallTransferList(query));
    }

    @Test
    void insertAiCallTransfer_delegates()
    {
        AiCallTransfer transfer = new AiCallTransfer();
        when(aiCallTransferMapper.insertAiCallTransfer(transfer)).thenReturn(1);

        assertEquals(1, service.insertAiCallTransfer(transfer));
    }

    @Test
    void selectAiCallTransferByRecordId_delegates()
    {
        List<AiCallTransfer> list = Collections.singletonList(new AiCallTransfer());
        when(aiCallTransferMapper.selectAiCallTransferByRecordId(1L)).thenReturn(list);

        assertSame(list, service.selectAiCallTransferByRecordId(1L));
    }

    @Test
    void deleteAiCallTransferByTransferId_delegates()
    {
        when(aiCallTransferMapper.deleteAiCallTransferByTransferId(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiCallTransferByTransferId(1L));
    }

    @Test
    void deleteAiCallTransferByTransferIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiCallTransferMapper.deleteAiCallTransferByTransferIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiCallTransferByTransferIds(ids));
    }

    @Test
    void transferCall_recordExists_updatesRecordThroughService()
    {
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(1L);
        when(aiCallRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(record);

        assertEquals(1, service.transferCall(1L, 10L, "甲", 20L, "乙", "忙线"));

        ArgumentCaptor<AiCallTransfer> transferCaptor = ArgumentCaptor.forClass(AiCallTransfer.class);
        verify(aiCallTransferMapper).insertAiCallTransfer(transferCaptor.capture());
        AiCallTransfer transfer = transferCaptor.getValue();
        assertEquals(1L, transfer.getRecordId());
        assertEquals(10L, transfer.getFromAgentId());
        assertEquals("甲", transfer.getFromAgentName());
        assertEquals(20L, transfer.getToAgentId());
        assertEquals("乙", transfer.getToAgentName());
        assertEquals("忙线", transfer.getReason());
        assertNotNull(transfer.getTransferTime());

        assertEquals("2", record.getStatus());
        assertEquals(20L, record.getAgentId());
        verify(aiCallRecordService).updateAiCallRecord(record);
    }

    @Test
    void transferCall_recordMissing_onlyInsertsTransfer()
    {
        when(aiCallRecordMapper.selectAiCallRecordByRecordId(2L)).thenReturn(null);

        assertEquals(1, service.transferCall(2L, 10L, "甲", 20L, "乙", "转接"));

        verify(aiCallTransferMapper).insertAiCallTransfer(any(AiCallTransfer.class));
        verify(aiCallRecordService, never()).updateAiCallRecord(any());
    }
}
