package ai.lawyers.web.controller.lawyers;

import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.poi.ExcelUtil;
import ai.lawyers.system.domain.lawyers.AiTodo;
import ai.lawyers.system.service.lawyers.IAiTodoService;

@RestController
@RequestMapping("/lawyers/workbench/todo")
public class AiTodoController extends BaseController
{
    @Autowired
    private IAiTodoService aiTodoService;

    @PreAuthorize("@ss.hasPermi('lawyers:workbench:todo:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiTodo aiTodo)
    {
        startPage();
        if (aiTodo.getUserId() == null) {
            aiTodo.setUserId(getUserId());
        }
        List<AiTodo> list = aiTodoService.selectAiTodoList(aiTodo);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:workbench:todo:export')")
    @Log(title = "工作台待办", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiTodo aiTodo)
    {
        List<AiTodo> list = aiTodoService.selectAiTodoList(aiTodo);
        ExcelUtil<AiTodo> util = new ExcelUtil<AiTodo>(AiTodo.class);
        util.exportExcel(response, list, "工作台待办数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:workbench:todo:query')")
    @GetMapping(value = "/{todoId}")
    public AjaxResult getInfo(@PathVariable("todoId") Long todoId)
    {
        return success(aiTodoService.selectAiTodoByTodoId(todoId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:workbench:todo:add')")
    @Log(title = "工作台待办", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiTodo aiTodo)
    {
        aiTodo.setCreateBy(getUsername());
        if (aiTodo.getUserId() == null) {
            aiTodo.setUserId(getUserId());
        }
        return toAjax(aiTodoService.insertAiTodo(aiTodo));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:workbench:todo:edit')")
    @Log(title = "工作台待办", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiTodo aiTodo)
    {
        aiTodo.setUpdateBy(getUsername());
        return toAjax(aiTodoService.updateAiTodo(aiTodo));
    }

    /** 处理待办：标记为已完成 */
    @PreAuthorize("@ss.hasPermi('lawyers:workbench:todo:edit')")
    @Log(title = "处理待办", businessType = BusinessType.OTHER)
    @PutMapping("/process/{todoId}")
    public AjaxResult process(@PathVariable("todoId") Long todoId)
    {
        AiTodo todo = new AiTodo();
        todo.setTodoId(todoId);
        todo.setStatus("1");
        todo.setUpdateBy(getUsername());
        return toAjax(aiTodoService.updateAiTodo(todo));
    }

    /** 延后待办 */
    @PreAuthorize("@ss.hasPermi('lawyers:workbench:todo:edit')")
    @Log(title = "延后待办", businessType = BusinessType.OTHER)
    @PutMapping("/defer/{todoId}")
    public AjaxResult defer(@PathVariable("todoId") Long todoId)
    {
        AiTodo todo = new AiTodo();
        todo.setTodoId(todoId);
        todo.setStatus("2");
        todo.setUpdateBy(getUsername());
        return toAjax(aiTodoService.updateAiTodo(todo));
    }

    /** 忽略待办 */
    @PreAuthorize("@ss.hasPermi('lawyers:workbench:todo:edit')")
    @Log(title = "忽略待办", businessType = BusinessType.OTHER)
    @PutMapping("/ignore/{todoId}")
    public AjaxResult ignore(@PathVariable("todoId") Long todoId)
    {
        AiTodo todo = new AiTodo();
        todo.setTodoId(todoId);
        todo.setStatus("3");
        todo.setUpdateBy(getUsername());
        return toAjax(aiTodoService.updateAiTodo(todo));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:workbench:todo:remove')")
    @Log(title = "工作台待办", businessType = BusinessType.DELETE)
    @DeleteMapping("/{todoIds}")
    public AjaxResult remove(@PathVariable Long[] todoIds)
    {
        return toAjax(aiTodoService.deleteAiTodoByTodoIds(todoIds));
    }
}
