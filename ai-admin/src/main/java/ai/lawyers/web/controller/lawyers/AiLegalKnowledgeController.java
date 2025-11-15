package ai.lawyers.web.controller.lawyers;

import java.util.Date;
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
import org.springframework.web.multipart.MultipartFile;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.poi.ExcelUtil;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.service.lawyers.IAiLegalKnowledgeService;

/**
 * 法律知识库Controller
 * 
 * @author AI Lawyers
 */
@RestController
@RequestMapping("/lawyers/knowledge")
public class AiLegalKnowledgeController extends BaseController
{
    @Autowired
    private IAiLegalKnowledgeService aiLegalKnowledgeService;

    /**
     * 查询法律知识库列表
     */
    @PreAuthorize("@ss.hasPermi('lawyers:knowledge:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiLegalKnowledge aiLegalKnowledge)
    {
        startPage();
        List<AiLegalKnowledge> list = aiLegalKnowledgeService.selectAiLegalKnowledgeList(aiLegalKnowledge);
        return getDataTable(list);
    }

    /**
     * 导出法律知识库列表
     */
    @PreAuthorize("@ss.hasPermi('lawyers:knowledge:export')")
    @Log(title = "法律知识库", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiLegalKnowledge aiLegalKnowledge)
    {
        List<AiLegalKnowledge> list = aiLegalKnowledgeService.selectAiLegalKnowledgeList(aiLegalKnowledge);
        ExcelUtil<AiLegalKnowledge> util = new ExcelUtil<AiLegalKnowledge>(AiLegalKnowledge.class);
        util.exportExcel(response, list, "法律知识库数据");
    }

    /**
     * 获取法律知识库详细信息
     */
    @PreAuthorize("@ss.hasPermi('lawyers:knowledge:query')")
    @GetMapping(value = "/{knowledgeId}")
    public AjaxResult getInfo(@PathVariable("knowledgeId") Long knowledgeId)
    {
        return success(aiLegalKnowledgeService.selectAiLegalKnowledgeByKnowledgeId(knowledgeId));
    }

    /**
     * 根据分类ID查询法律知识库列表
     */
    @PreAuthorize("@ss.hasPermi('lawyers:knowledge:query')")
    @GetMapping(value = "/category/{categoryId}")
    public AjaxResult getKnowledgeByCategory(@PathVariable("categoryId") Long categoryId)
    {
        List<AiLegalKnowledge> list = aiLegalKnowledgeService.selectAiLegalKnowledgeByCategoryId(categoryId);
        return success(list);
    }

    /**
     * 根据关键词搜索法律知识库
     */
    @PreAuthorize("@ss.hasPermi('lawyers:knowledge:query')")
    @GetMapping(value = "/search/{keyword}")
    public AjaxResult searchKnowledge(@PathVariable("keyword") String keyword)
    {
        List<AiLegalKnowledge> list = aiLegalKnowledgeService.searchAiLegalKnowledge(keyword);
        return success(list);
    }

    /**
     * 新增法律知识库
     */
    @PreAuthorize("@ss.hasPermi('lawyers:knowledge:add')")
    @Log(title = "法律知识库", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiLegalKnowledge aiLegalKnowledge)
    {
        aiLegalKnowledge.setCreateBy(getUsername());
        return toAjax(aiLegalKnowledgeService.insertAiLegalKnowledge(aiLegalKnowledge));
    }

    /**
     * 修改法律知识库
     */
    @PreAuthorize("@ss.hasPermi('lawyers:knowledge:edit')")
    @Log(title = "法律知识库", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiLegalKnowledge aiLegalKnowledge)
    {
        aiLegalKnowledge.setUpdateBy(getUsername());
        return toAjax(aiLegalKnowledgeService.updateAiLegalKnowledge(aiLegalKnowledge));
    }

    /**
     * 删除法律知识库
     */
    @PreAuthorize("@ss.hasPermi('lawyers:knowledge:remove')")
    @Log(title = "法律知识库", businessType = BusinessType.DELETE)
    @DeleteMapping("/{knowledgeIds}")
    public AjaxResult remove(@PathVariable Long[] knowledgeIds)
    {
        return toAjax(aiLegalKnowledgeService.deleteAiLegalKnowledgeByKnowledgeIds(knowledgeIds));
    }

    /**
     * 批量导入法律知识库
     */
    @PreAuthorize("@ss.hasPermi('lawyers:knowledge:import')")
    @Log(title = "法律知识库", businessType = BusinessType.IMPORT)
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file, boolean updateSupport) throws Exception
    {
        ExcelUtil<AiLegalKnowledge> util = new ExcelUtil<AiLegalKnowledge>(AiLegalKnowledge.class);
        List<AiLegalKnowledge> knowledgeList = util.importExcel(file.getInputStream());
        String operName = getUsername();
        String message = aiLegalKnowledgeService.importKnowledge(knowledgeList, updateSupport, operName);
        return success(message);
    }

    /**
     * 获取法律知识库导入模板
     */
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response)
    {
        ExcelUtil<AiLegalKnowledge> util = new ExcelUtil<AiLegalKnowledge>(AiLegalKnowledge.class);
        util.importTemplateExcel(response, "法律知识库数据");
    }

    /**
     * 审核法律知识库
     */
    @PreAuthorize("@ss.hasPermi('lawyers:knowledge:audit')")
    @Log(title = "法律知识库", businessType = BusinessType.UPDATE)
    @PutMapping("/audit")
    public AjaxResult audit(@RequestBody AiLegalKnowledge aiLegalKnowledge)
    {
        aiLegalKnowledge.setAuditBy(getUsername());
        aiLegalKnowledge.setAuditTime(new Date());
        return toAjax(aiLegalKnowledgeService.auditAiLegalKnowledge(aiLegalKnowledge));
    }
}