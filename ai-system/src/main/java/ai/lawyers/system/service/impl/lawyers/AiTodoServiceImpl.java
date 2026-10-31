package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiTodo;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.mapper.lawyers.AiTodoMapper;
import ai.lawyers.system.service.lawyers.IAiTodoService;

@Service
public class AiTodoServiceImpl implements IAiTodoService
{
    private static final Logger log = LoggerFactory.getLogger(AiTodoServiceImpl.class);

    /** F7：待办关联业务类型——工单 */
    private static final String BIZ_TYPE_TICKET = "ticket";

    @Autowired
    private AiTodoMapper aiTodoMapper;

    /** F7：直接依赖工单 Mapper，避免与工单服务形成循环依赖 */
    @Autowired
    private AiCallTicketMapper aiCallTicketMapper;

    @Override
    public AiTodo selectAiTodoByTodoId(Long todoId)
    {
        return aiTodoMapper.selectAiTodoByTodoId(todoId);
    }

    @Override
    public AiCallTicket selectLinkedTicket(Long todoId)
    {
        if (todoId == null)
        {
            return null;
        }
        AiTodo todo = aiTodoMapper.selectAiTodoByTodoId(todoId);
        if (todo == null || todo.getBizId() == null
                || !BIZ_TYPE_TICKET.equalsIgnoreCase(todo.getBizType()))
        {
            return null;
        }
        return aiCallTicketMapper.selectAiCallTicketByTicketId(todo.getBizId());
    }

    @Override
    public List<AiTodo> selectAiTodoList(AiTodo aiTodo)
    {
        return aiTodoMapper.selectAiTodoList(aiTodo);
    }

    @Override
    public List<AiTodo> selectAiTodoListByUserId(Long userId)
    {
        return aiTodoMapper.selectAiTodoListByUserId(userId);
    }

    @Override
    public int insertAiTodo(AiTodo aiTodo)
    {
        if (aiTodo.getStatus() == null || aiTodo.getStatus().isEmpty()) {
            aiTodo.setStatus("0");
        }
        if (aiTodo.getPriority() == null || aiTodo.getPriority().isEmpty()) {
            aiTodo.setPriority("2");
        }
        // F7：填了业务ID 即视为关联工单，服务端补齐 bizType（不依赖前端传值）
        if (aiTodo.getBizId() != null && StringUtils.isEmpty(aiTodo.getBizType())) {
            aiTodo.setBizType(BIZ_TYPE_TICKET);
        }
        return aiTodoMapper.insertAiTodo(aiTodo);
    }

    @Override
    public int closeTodosByBiz(String bizType, Long bizId, String updateBy)
    {
        if (StringUtils.isEmpty(bizType) || bizId == null)
        {
            return 0;
        }
        String type = bizType.trim();
        String operator = StringUtils.isNotEmpty(updateBy) ? updateBy : currentUsername();
        int rows = aiTodoMapper.closeTodosByBiz(type, bizId, operator);
        if (rows > 0)
        {
            log.info("关联业务待办自动关闭 bizType={} bizId={} rows={} by={}", bizType, bizId, rows, operator);
        }
        return rows;
    }

    /** 当前登录用户名；定时任务等无登录上下文场景返回 null（不阻断主流程） */
    private String currentUsername()
    {
        try
        {
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            log.debug("当前上下文无登录用户，待办操作人不记录：{}", e.getMessage());
            return null;
        }
    }

    @Override
    public int updateAiTodo(AiTodo aiTodo)
    {
        return aiTodoMapper.updateAiTodo(aiTodo);
    }

    @Override
    public int deleteAiTodoByTodoId(Long todoId)
    {
        return aiTodoMapper.deleteAiTodoByTodoId(todoId);
    }

    @Override
    public int deleteAiTodoByTodoIds(Long[] todoIds)
    {
        return aiTodoMapper.deleteAiTodoByTodoIds(todoIds);
    }
}
