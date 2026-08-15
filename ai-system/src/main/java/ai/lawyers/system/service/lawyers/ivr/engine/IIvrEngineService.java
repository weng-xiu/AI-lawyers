package ai.lawyers.system.service.lawyers.ivr.engine;

import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;

/**
 * IVR 流程执行引擎。
 *
 * 融合方案 3.5.1：流程发布后关联到来电号码/技能组，通话接入时触发对应 IVR 流程，
 * 流程节点执行结果更新到通话记录。
 */
public interface IIvrEngineService
{
    /**
     * 执行一次 IVR 流程（在线调试 / 运行时执行通用）。
     */
    IvrExecuteResult executeFlow(IvrExecuteRequest request);

    /**
     * 根据主叫号码解析应执行的流程。
     * 当前阶段按"默认流程"兜底，后续可扩展为按号码段/技能组绑定（扩展点）。
     */
    AiIvrFlow resolveFlowForCaller(String callerNumber);
}
