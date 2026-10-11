package ai.lawyers.system.service.impl.lawyers.voice.otp;

import ai.lawyers.system.service.lawyers.voice.otp.WriteOpAction;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpExecutor;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpSession;

/**
 * 写操作执行器测试支撑：可编程结果的桩执行器。
 *
 * @author ai-lawyers
 */
final class WriteOpExecutorTestSupport
{
    private WriteOpExecutorTestSupport()
    {
    }

    /** 桩执行器：返回可编程结果，记录最近一次入参会话 */
    static class StubExecutor implements WriteOpExecutor
    {
        private final boolean success;

        private final String ref;

        volatile WriteOpSession lastSession;

        StubExecutor(boolean success, String ref)
        {
            this.success = success;
            this.ref = ref;
        }

        @Override
        public WriteOpAction action()
        {
            return WriteOpAction.SEND_SMS;
        }

        @Override
        public ExecutionResult execute(WriteOpSession session)
        {
            this.lastSession = session;
            return success ? ExecutionResult.ok("已执行", ref) : ExecutionResult.fail("桩失败");
        }
    }
}
