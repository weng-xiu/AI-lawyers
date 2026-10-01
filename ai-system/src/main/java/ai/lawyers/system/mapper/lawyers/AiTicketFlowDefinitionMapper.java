package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.AiTicketFlowDefinition;

/**
 * P1-6：工单流程状态机定义数据层（配置化流转 DSL）。
 *
 * @author ai-lawyers
 */
public interface AiTicketFlowDefinitionMapper
{
    /** 查询某流程在指定源状态下启用的动作（供按钮动态渲染） */
    List<AiTicketFlowDefinition> selectActions(@Param("flowCode") String flowCode,
                                               @Param("statusFrom") String statusFrom);

    /** 查询某流程全部规则（管理页） */
    List<AiTicketFlowDefinition> selectByFlowCode(String flowCode);

    /** 查询单条规则（动作执行前校验） */
    AiTicketFlowDefinition selectRule(@Param("flowCode") String flowCode,
                                      @Param("statusFrom") String statusFrom,
                                      @Param("actionCode") String actionCode);

    int insertDefinition(AiTicketFlowDefinition definition);

    int updateDefinition(AiTicketFlowDefinition definition);
}
