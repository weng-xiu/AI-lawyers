package ai.lawyers.web.controller.lawyers.collab;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.system.domain.lawyers.AiKnowledgeAudit;
import ai.lawyers.system.service.lawyers.IAiKnowledgeAuditService;

/**
 * 法律知识审核发布 Controller（F7）
 *
 * <p>审核流水查询 + 状态机动作（提交/通过/驳回/发布/下线），
 * 知识条目分页查询复用 {@code GET /lawyers/knowledge/list}（auditStatus 过滤）。</p>
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/knowledge/audit")
public class AiKnowledgeAuditController extends BaseController
{
    @Autowired
    private IAiKnowledgeAuditService knowledgeAuditService;

    /**
     * 审核发布流水。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:knowledge:audit:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiKnowledgeAudit query)
    {
        startPage();
        List<AiKnowledgeAudit> list = knowledgeAuditService.selectAuditList(query);
        return getDataTable(list);
    }

    /**
     * 提交审核。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:knowledge:audit:submit')")
    @Log(title = "知识提交审核", businessType = BusinessType.UPDATE)
    @PostMapping("/submit/{knowledgeId}")
    public AjaxResult submit(@PathVariable("knowledgeId") Long knowledgeId)
    {
        knowledgeAuditService.submit(knowledgeId, getUsername());
        return success("已提交审核");
    }

    /**
     * 审核通过。Body：{ "opinion": "审核意见" }
     */
    @PreAuthorize("@ss.hasPermi('lawyers:knowledge:audit:approve')")
    @Log(title = "知识审核通过", businessType = BusinessType.UPDATE)
    @PostMapping("/approve/{knowledgeId}")
    public AjaxResult approve(@PathVariable("knowledgeId") Long knowledgeId,
                              @RequestBody(required = false) Map<String, String> body)
    {
        knowledgeAuditService.approve(knowledgeId, opinion(body), getUsername());
        return success("审核通过");
    }

    /**
     * 审核驳回。Body：{ "opinion": "驳回原因" }
     */
    @PreAuthorize("@ss.hasPermi('lawyers:knowledge:audit:approve')")
    @Log(title = "知识审核驳回", businessType = BusinessType.UPDATE)
    @PostMapping("/reject/{knowledgeId}")
    public AjaxResult reject(@PathVariable("knowledgeId") Long knowledgeId,
                             @RequestBody(required = false) Map<String, String> body)
    {
        knowledgeAuditService.reject(knowledgeId, opinion(body), getUsername());
        return success("已驳回");
    }

    /**
     * 发布上架（版本号+1）。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:knowledge:audit:approve')")
    @Log(title = "知识发布", businessType = BusinessType.UPDATE)
    @PostMapping("/publish/{knowledgeId}")
    public AjaxResult publish(@PathVariable("knowledgeId") Long knowledgeId,
                              @RequestBody(required = false) Map<String, String> body)
    {
        knowledgeAuditService.publish(knowledgeId, opinion(body), getUsername());
        return success("已发布");
    }

    /**
     * 下线。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:knowledge:audit:offline')")
    @Log(title = "知识下线", businessType = BusinessType.UPDATE)
    @PostMapping("/offline/{knowledgeId}")
    public AjaxResult offline(@PathVariable("knowledgeId") Long knowledgeId,
                              @RequestBody(required = false) Map<String, String> body)
    {
        knowledgeAuditService.offline(knowledgeId, opinion(body), getUsername());
        return success("已下线");
    }

    private String opinion(Map<String, String> body)
    {
        if (body == null)
        {
            return null;
        }
        String opinion = body.get("opinion");
        if (opinion == null)
        {
            opinion = body.get("auditRemark");
        }
        return opinion;
    }
}
