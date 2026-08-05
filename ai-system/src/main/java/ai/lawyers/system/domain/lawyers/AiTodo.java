package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 工作台待办对象 ai_todo
 */
public class AiTodo extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long todoId;

    @Excel(name = "待办标题")
    private String todoTitle;

    @Excel(name = "待办内容")
    private String todoContent;

    @Excel(name = "状态", readConverterExp = "0=待办,1=已完成,2=延后,3=忽略")
    private String status;

    @Excel(name = "优先级", readConverterExp = "1=紧急,2=普通,3=低")
    private String priority;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "到期日期", width = 20, dateFormat = "yyyy-MM-dd")
    private Date dueDate;

    @Excel(name = "归属用户ID")
    private Long userId;

    public void setTodoId(Long todoId)
    {
        this.todoId = todoId;
    }

    public Long getTodoId()
    {
        return todoId;
    }

    public void setTodoTitle(String todoTitle)
    {
        this.todoTitle = todoTitle;
    }

    public String getTodoTitle()
    {
        return todoTitle;
    }

    public void setTodoContent(String todoContent)
    {
        this.todoContent = todoContent;
    }

    public String getTodoContent()
    {
        return todoContent;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setPriority(String priority)
    {
        this.priority = priority;
    }

    public String getPriority()
    {
        return priority;
    }

    public void setDueDate(Date dueDate)
    {
        this.dueDate = dueDate;
    }

    public Date getDueDate()
    {
        return dueDate;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getUserId()
    {
        return userId;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("todoId", getTodoId())
            .append("todoTitle", getTodoTitle())
            .append("todoContent", getTodoContent())
            .append("status", getStatus())
            .append("priority", getPriority())
            .append("dueDate", getDueDate())
            .append("userId", getUserId())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
