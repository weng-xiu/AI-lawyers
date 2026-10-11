package ai.lawyers.system.service.impl.lawyers.voice.otp;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.service.lawyers.voice.otp.IWriteOpConfirmService;
import ai.lawyers.system.service.lawyers.voice.otp.IWriteOpConfirmService.TransitionResult;
import ai.lawyers.system.service.lawyers.voice.otp.IWriteOpExecutionService;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpAction;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpExecutor;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpSession;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpState;

/**
 * P3-#12：写操作业务执行编排实现。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>执行器注册表：Spring 注入全部 {@link WriteOpExecutor}，按动作建
 *       EnumMap（重复动作首个生效并 WARN）；执行器缺席视为"动作无执行器"；</li>
 *   <li>状态守卫：仅 CONFIRMED 可执行；执行成功回执
 *       {@link IWriteOpConfirmService#complete(String)}（CONFIRMED→EXECUTED），
 *       失败保持 CONFIRMED（可重试），绝不跳过状态机直接落库；</li>
 *   <li>异常隔离：任何异常转为 fail 返回，不外抛、不改变会话状态。</li>
 * </ul>
 *
 * @author ai-lawyers
 */
@Service
public class WriteOpExecutionServiceImpl implements IWriteOpExecutionService
{
    private static final Logger log = LoggerFactory.getLogger(WriteOpExecutionServiceImpl.class);

    @Autowired
    private IWriteOpConfirmService confirmService;

    /** 执行器注册表（Spring 全量注入；缺省空表 = 动作无执行器） */
    private final Map<WriteOpAction, WriteOpExecutor> executors =
            new EnumMap<>(WriteOpAction.class);

    @Autowired(required = false)
    public void setExecutors(List<WriteOpExecutor> executorList)
    {
        if (executorList == null)
        {
            return;
        }
        for (WriteOpExecutor e : executorList)
        {
            if (e == null || e.action() == null)
            {
                continue;
            }
            WriteOpExecutor prev = executors.putIfAbsent(e.action(), e);
            if (prev != null)
            {
                log.warn("写操作执行器动作重复，首个生效 action={} keep={} ignore={}",
                        e.action(), prev.getClass().getSimpleName(), e.getClass().getSimpleName());
            }
        }
        log.info("写操作执行器注册完成 actions={}", executors.keySet());
    }

    @Override
    public WriteOpExecutor.ExecutionResult executeConfirmed(String opId)
    {
        WriteOpSession session = confirmService.get(opId);
        if (session == null)
        {
            return WriteOpExecutor.ExecutionResult.fail("会话不存在或已过期");
        }
        if (session.getState() != WriteOpState.CONFIRMED)
        {
            return WriteOpExecutor.ExecutionResult.fail(
                    "当前状态不允许执行（须 CONFIRMED），当前=" + session.getState().name());
        }
        WriteOpExecutor executor = executors.get(session.getAction());
        if (executor == null)
        {
            log.warn("写操作无执行器 op={} action={}", opId, session.getAction());
            return WriteOpExecutor.ExecutionResult.fail(
                    "动作 " + session.getAction().getCode() + " 无执行器");
        }
        try
        {
            WriteOpExecutor.ExecutionResult r = executor.execute(session);
            if (!r.isSuccess())
            {
                log.warn("写操作执行失败（保持 CONFIRMED 可重试） op={} action={} msg={}",
                        opId, session.getAction(), r.getMessage());
                return r;
            }
            TransitionResult receipt = confirmService.complete(opId);
            if (!receipt.isSuccess())
            {
                log.warn("写操作回执失败 op={} action={} msg={}",
                        opId, session.getAction(), receipt.getMessage());
                return WriteOpExecutor.ExecutionResult.fail("落库成功但回执失败: " + receipt.getMessage());
            }
            log.info("写操作执行完成 op={} action={} ref={}", opId, session.getAction(), r.getRef());
            return r;
        }
        catch (Exception e)
        {
            log.warn("写操作执行异常（保持 CONFIRMED） op={} action={}", opId, session.getAction(), e);
            return WriteOpExecutor.ExecutionResult.fail("执行异常: " + e.getMessage());
        }
    }
}
