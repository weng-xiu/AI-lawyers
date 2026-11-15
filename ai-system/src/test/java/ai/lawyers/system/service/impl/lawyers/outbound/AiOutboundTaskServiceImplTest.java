package ai.lawyers.system.service.impl.lawyers.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundTask;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundTaskMapper;

/**
 * 外呼任务服务单测。
 *
 * <p>覆盖任务查询与删除委托、任务号自动生成、状态启停流转。</p>
 *
 * @author ai-lawyers
 */
class AiOutboundTaskServiceImplTest
{
    private AiOutboundTaskServiceImpl service;

    @Mock private AiOutboundTaskMapper aiOutboundTaskMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiOutboundTaskServiceImpl();
        ReflectionTestUtils.setField(service, "aiOutboundTaskMapper", aiOutboundTaskMapper);
    }

    @Test
    void selectAiOutboundTaskByTaskId_delegates()
    {
        AiOutboundTask expected = new AiOutboundTask();
        when(aiOutboundTaskMapper.selectAiOutboundTaskByTaskId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiOutboundTaskByTaskId(1L));
    }

    @Test
    void selectAiOutboundTaskList_delegates()
    {
        AiOutboundTask query = new AiOutboundTask();
        List<AiOutboundTask> list = Collections.singletonList(new AiOutboundTask());
        when(aiOutboundTaskMapper.selectAiOutboundTaskList(query)).thenReturn(list);

        assertSame(list, service.selectAiOutboundTaskList(query));
    }

    @Test
    void selectAiOutboundTaskByTaskNo_delegates()
    {
        AiOutboundTask expected = new AiOutboundTask();
        when(aiOutboundTaskMapper.selectAiOutboundTaskByTaskNo("OB1")).thenReturn(expected);

        assertSame(expected, service.selectAiOutboundTaskByTaskNo("OB1"));
    }

    @Test
    void insertAiOutboundTask_missingTaskNo_generates()
    {
        AiOutboundTask task = new AiOutboundTask();
        when(aiOutboundTaskMapper.insertAiOutboundTask(task)).thenReturn(1);

        assertEquals(1, service.insertAiOutboundTask(task));
        assertTrue(task.getTaskNo().startsWith("OB"));
    }

    @Test
    void insertAiOutboundTask_existingTaskNo_keeps()
    {
        AiOutboundTask task = new AiOutboundTask();
        task.setTaskNo("OB-EXIST");
        when(aiOutboundTaskMapper.insertAiOutboundTask(task)).thenReturn(1);

        assertEquals(1, service.insertAiOutboundTask(task));
        assertEquals("OB-EXIST", task.getTaskNo());
    }

    @Test
    void generateTaskNo_startsWithPrefix()
    {
        assertTrue(service.generateTaskNo().startsWith("OB"));
    }

    @Test
    void updateAiOutboundTask_delegates()
    {
        AiOutboundTask task = new AiOutboundTask();
        when(aiOutboundTaskMapper.updateAiOutboundTask(task)).thenReturn(1);

        assertEquals(1, service.updateAiOutboundTask(task));
    }

    @Test
    void deleteAiOutboundTaskByTaskId_delegates()
    {
        when(aiOutboundTaskMapper.deleteAiOutboundTaskByTaskId(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiOutboundTaskByTaskId(1L));
    }

    @Test
    void deleteAiOutboundTaskByTaskIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiOutboundTaskMapper.deleteAiOutboundTaskByTaskIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiOutboundTaskByTaskIds(ids));
    }

    private AiOutboundTask captureStatusTask()
    {
        ArgumentCaptor<AiOutboundTask> captor = ArgumentCaptor.forClass(AiOutboundTask.class);
        verify(aiOutboundTaskMapper).updateTaskStatus(captor.capture());
        return captor.getValue();
    }

    @Test
    void startTask_setsRunningStatus()
    {
        when(aiOutboundTaskMapper.updateTaskStatus(any(AiOutboundTask.class))).thenReturn(1);

        assertEquals(1, service.startTask(1L));
        assertEquals(1L, captureStatusTask().getTaskId());
        assertEquals("1", captureStatusTask().getStatus());
    }

    @Test
    void pauseTask_setsPausedStatus()
    {
        when(aiOutboundTaskMapper.updateTaskStatus(any(AiOutboundTask.class))).thenReturn(1);

        assertEquals(1, service.pauseTask(1L));
        assertEquals("3", captureStatusTask().getStatus());
    }

    @Test
    void stopTask_setsStoppedStatus()
    {
        when(aiOutboundTaskMapper.updateTaskStatus(any(AiOutboundTask.class))).thenReturn(1);

        assertEquals(1, service.stopTask(1L));
        assertEquals("4", captureStatusTask().getStatus());
    }
}
