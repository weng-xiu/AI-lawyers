package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;

/**
 * IVR 节点执行器：每类节点一个实现，引擎注册表按节点类型（小写）分发。
 *
 * <p>实现类为 Spring 组件、构造器注入协作服务；新增节点类型只需新增一个实现类，
 * 引擎主循环无需改动（开闭原则）。执行器不得反向依赖引擎门面，子流程递归经
 * {@link IvrNodeContext#getFlowRunner()} 提供的函数接口完成，避免 Bean 成环。</p>
 *
 * @author ai-lawyers
 */
public interface IvrNodeExecutor
{
    /** 支持的节点类型（小写，与前端设计器对齐）；一个执行器可支持多个类型（如 menu/dtmf） */
    String[] supportedTypes();

    /**
     * 执行节点语义：就地写步骤/变量/结果后返回下一节点；返回 null 表示流程终止（挂断/转接）。
     */
    AiIvrNode execute(IvrNodeContext ctx);
}
