package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiTicketFlowLayout;

/**
 * F2：工单流程画布节点布局数据层。
 *
 * @author ai-lawyers
 */
public interface AiTicketFlowLayoutMapper
{
    /** 查询某流程已保存的节点布局（可为空，前端回退默认排布） */
    List<AiTicketFlowLayout> selectLayoutByFlowCode(String flowCode);

    /** 按 (flow_code, status_code) 唯一键写入/覆盖节点坐标 */
    int upsertLayout(AiTicketFlowLayout layout);
}
