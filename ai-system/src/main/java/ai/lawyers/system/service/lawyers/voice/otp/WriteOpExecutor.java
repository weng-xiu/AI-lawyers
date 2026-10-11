package ai.lawyers.system.service.lawyers.voice.otp;

/**
 * P3-#12：机器人写操作业务执行器 SPI（调研报告 6.3 "CONFIRMED 后落库动作"）。
 *
 * <p>每个白名单动作对应一个执行器实现；{@link WriteOpExecutionServiceImpl}
 * 在会话到达 CONFIRMED（用户已按 1 确认）后按动作分发。执行器成功落库后由
 * 编排服务回执 {@link IWriteOpConfirmService#complete(String)}（CONFIRMED→EXECUTED），
 * 失败保持 CONFIRMED 可重试，状态机本体不做任何业务落库。</p>
 *
 * <p>纪律：目标号码/话单一律取自 {@link WriteOpSession}（服务端锚定），
 * 不接受 payload 指定第三方号码；payload 仅承载动作参数（模板ID、回访时间等）。</p>
 *
 * @author ai-lawyers
 */
public interface WriteOpExecutor
{
    /** 本执行器承接的白名单动作 */
    WriteOpAction action();

    /**
     * 执行落库（仅在会话状态为 CONFIRMED 时被调用）。
     *
     * @param session 确认会话（含锚定号码/话单/会话ID 与 payloadJson 草稿）
     * @return 执行结果（失败不外抛，返回 fail）
     */
    ExecutionResult execute(WriteOpSession session);

    /**
     * 执行结果值类。
     */
    class ExecutionResult
    {
        private final boolean success;

        private final String message;

        /** 业务单据引用（如短信 msgId / 回访单ID，可空） */
        private final String ref;

        private ExecutionResult(boolean success, String message, String ref)
        {
            this.success = success;
            this.message = message;
            this.ref = ref;
        }

        public static ExecutionResult ok(String message, String ref)
        {
            return new ExecutionResult(true, message, ref);
        }

        public static ExecutionResult fail(String message)
        {
            return new ExecutionResult(false, message, null);
        }

        public boolean isSuccess() { return success; }

        public String getMessage() { return message; }

        public String getRef() { return ref; }
    }
}
