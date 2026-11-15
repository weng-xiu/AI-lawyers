package ai.lawyers.system.service.impl.lawyers.outbound;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundCallee;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundTask;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;

/**
 * 外呼自动建单开关（默认 false）测试：关闭时零副作用，开启后仅回访类/转人工类建单，
 * 字段映射与异常隔离符合"外呼主链路不受建单失败影响"的约定。
 *
 * @author ai-lawyers
 */
class OutboundCallFinalizerTicketTest
{
    private OutboundCallFinalizer finalizer;
    private IAiCallTicketService callTicketService;

    @BeforeEach
    void setUp()
    {
        finalizer = new OutboundCallFinalizer();
        callTicketService = mock(IAiCallTicketService.class);
        ReflectionTestUtils.setField(finalizer, "callTicketService", callTicketService);
        ReflectionTestUtils.setField(finalizer, "autoCreateTicket", false);
    }

    private static AiOutboundTask task(String taskType)
    {
        AiOutboundTask t = new AiOutboundTask();
        t.setTaskId(1L);
        t.setTaskNo("OUT-001");
        t.setTaskName("春季回访");
        t.setTaskType(taskType);
        t.setCallerName("张律师");
        return t;
    }

    private static AiOutboundCallee callee()
    {
        AiOutboundCallee c = new AiOutboundCallee();
        c.setCalleeId(9L);
        c.setCalleeNumber("13800000000");
        c.setCalleeName("李四");
        return c;
    }

    private void enable()
    {
        ReflectionTestUtils.setField(finalizer, "autoCreateTicket", true);
    }

    @Test
    void disabled_noTicket()
    {
        finalizer.maybeCreateTicket(task("2"), callee(), 100L, null);

        verifyNoInteractions(callTicketService);
    }

    @Test
    void enabled_nullTaskOrRecord_noTicket()
    {
        enable();

        finalizer.maybeCreateTicket(null, callee(), 100L, null);
        finalizer.maybeCreateTicket(task("2"), callee(), null, null);

        verifyNoInteractions(callTicketService);
    }

    @Test
    void enabled_normalTaskWithoutTransfer_noTicket()
    {
        enable();

        finalizer.maybeCreateTicket(task("1"), callee(), 100L, null);

        verifyNoInteractions(callTicketService);
    }

    @Test
    void enabled_visitTask_createsVisitTicket()
    {
        enable();

        finalizer.maybeCreateTicket(task("2"), callee(), 100L, null);

        verify(callTicketService).insertAiCallTicket(any());
        verify(callTicketService).insertAiCallTicket(org.mockito.ArgumentMatchers.argThat(t ->
                Long.valueOf(100L).equals(t.getRecordId())
                        && t.getTitle().equals("回访工单：春季回访")
                        && "2".equals(t.getPriority())
                        && "0".equals(t.getStatus())
                        && "张律师".equals(t.getAssignUserName())
                        && t.getContent().contains("OUT-001")
                        && t.getContent().contains("13800000000")
                        && t.getContent().contains("李四")));
    }

    @Test
    void enabled_transferFlow_createsFollowUpTicketWithIntention()
    {
        enable();
        IvrExecuteResult flow = new IvrExecuteResult();
        flow.setTransferTarget("人工坐席");
        flow.setMatchedIntentionName("劳动仲裁咨询");

        finalizer.maybeCreateTicket(task("1"), callee(), 100L, flow);

        verify(callTicketService).insertAiCallTicket(org.mockito.ArgumentMatchers.argThat(t ->
                t.getTitle().equals("跟进工单：春季回访")
                        && t.getContent().contains("识别意图:劳动仲裁咨询")));
    }

    @Test
    void enabled_blankTransferTarget_noTicket()
    {
        enable();
        IvrExecuteResult flow = new IvrExecuteResult();
        flow.setTransferTarget("  ");

        finalizer.maybeCreateTicket(task("1"), callee(), 100L, flow);

        verifyNoInteractions(callTicketService);
    }

    @Test
    void enabled_insertThrows_swallowed()
    {
        enable();
        doThrow(new IllegalStateException("db down")).when(callTicketService).insertAiCallTicket(any());

        finalizer.maybeCreateTicket(task("2"), callee(), 100L, null);

        verify(callTicketService).insertAiCallTicket(any());
    }

    @Test
    void enabled_nullCreateBy_defaultsToExecutor()
    {
        enable();

        finalizer.maybeCreateTicket(task("2"), callee(), 100L, null);

        verify(callTicketService).insertAiCallTicket(org.mockito.ArgumentMatchers.argThat(
                (AiCallTicket t) -> "outbound-executor".equals(t.getCreateBy())));
    }
}
